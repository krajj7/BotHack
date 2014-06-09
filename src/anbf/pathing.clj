(ns anbf.pathing
  (:require [clojure.data.priority-map :refer [priority-map-keyfn]]
            [clojure.tools.logging :as log]
            [anbf.position :refer :all]
            [anbf.monster :refer :all]
            [anbf.delegator :refer :all]
            [anbf.actions :refer :all]
            [anbf.dungeon :refer :all]))

(defn move-cost [level from to]
  {:pre [(and (some? level) (some? from) (some? to))]}
  (let [diag? (diagonal? from to)
        to-tile (at level to)
        monster (get-in level [:monsters (position to)])
        feature (:feature to-tile)]
    (cond-> 0
      (= :trap feature) (+ 5) ; TODO trap types
      (diagonal? from to) (+ 0.1)
      (not (#{:stairs-up :stairs-down} feature)) (+ 0.1)
      (not (:walked to-tile)) (+ 0.2)
      (and (not (:walked to-tile)) (= :floor feature)) (+ 1)
      ; close and kick down
      (and diag? (= :door-open feature)) (+ 8)
      ; kick down
      (and diag? (= :door-closed feature)) (+ 5)
      (and monster (hostile? monster)) (+ 10)
      (:friendly monster) (+ 5)
      (:peaceful monster) (+ 20)
      (and (not diag?) (= :door-open feature)) (+ 0.5)
      (and (not diag?) (= :door-closed feature)) (+ 3)
      (and (= :door-locked feature)) (+ 7))))

(defn diagonal-walkable? [tile]
  (if (not-any? #(= % (:feature tile)) [nil :door-open])
    true)) ;TODO no diagonal near boulders, squeeze-spaces

(defn a* [from to passable? extra-cost]
  "Extra-cost must always return non-negative values, target tile may not be passable, but will always be included in the path"
  (loop [closed {}
         open (priority-map-keyfn first (position from) [0 0])]
    (if (empty? open) nil
      (let [[node [total dist prev]] (peek open)
            path (conj (closed prev []) node)
            final-path (subvec path 1)
            delta (distance node to)]
        (cond
          (zero? delta) final-path
          (and (= 1 delta)
               (not-any? #(passable? % to) (neighbors to))) (conj final-path to)
          :else (recur
                  (assoc closed node path)
                  (merge-with
                    (partial min-key first)
                    (pop open)
                    (into {}
                          (for [nbr (filter #(and (not (closed %))
                                                  (passable? node %))
                                            (neighbors node))]
                            (let [new-dist (+ (inc (extra-cost node nbr)) dist)]
                              [nbr [(+ new-dist delta) new-dist node]]))))))))))

(def path a*)

(defn dijkstra [from goal? passable? extra-cost]
  (loop [closed {}
         open (priority-map-keyfn first (position from) [0])]
    (if-let [[node [dist prev]] (peek open)]
      (let [path (conj (closed prev []) node)]
        (if (goal? node)
          (subvec path 1)
          (recur (assoc closed node path)
                 (merge-with (partial min-key first)
                             (pop open)
                             (into {}
                                   (for [nbr (filter #(and (not (closed %))
                                                           (passable? node %))
                                                     (neighbors node))]
                                     [nbr [(+ dist (inc (extra-cost node nbr)))
                                           node]])))))))))

(def nearest dijkstra)

; XXX stuff below will need a redesign but will do for now

(defn passable-walking?
  "Only needs Move action, no door opening etc., will path through monsters"
  [level from to]
  (let [from-tile (at level from)
        to-tile (at level to)]
    (and (or (walkable? to-tile)
             (and (not (boulder? to-tile))
                  (= nil (:feature to-tile)))) ; try to path via unexplored tiles
         (or (straight (towards from to))
             (and (diagonal-walkable? from-tile)
                  (diagonal-walkable? to-tile))))))

(defn passable-travelling?
  "Consider opening/breaking doors" ; TODO unlocking shops, digging
  [level from to]
    (or (passable-walking? level from to)
        (let [from-tile (at level from)
              to-tile (at level to)]
          (and (door? to-tile)
               (not (item? (:glyph to-tile))) ; don't try to break blocked doors
               ;(not (monster? (:glyph to-tile)))
               (or (not= :door-locked (:feature to-tile))
                   (not (shop? to-tile)))))))

(defn nearest-walking [game goal?]
  (let [level (curlvl (:dungeon game))]
    (nearest (-> game :player)
             #(goal? (at level %))
             (partial passable-walking? level)
             (partial move-cost level))))

(defn nearest-travelling [game goal?]
  (let [level (curlvl (:dungeon game))]
    (nearest (-> game :player)
             #(goal? (at level %))
             (partial passable-travelling? level)
             (partial move-cost level))))

(defn path-walking [game to]
  (let [level (curlvl (:dungeon game))]
    (path (-> game :player) to
          (partial passable-walking? level)
          (partial move-cost level))))

(defn path-travelling [game to]
  (let [level (curlvl (:dungeon game))]
    (path (-> game :player) to
          (partial passable-travelling? level)
          (partial move-cost level))))

(defn move-travelling [{:keys [dungeon player] :as game} path]
  (let [level (curlvl dungeon)
        step (first path)
        to-tile (at level step)
        dir (towards player to-tile)]
    (if (passable-walking? level player step)
      (if (get-in (:monsters level) [step :peaceful] nil)
        (->Search) ; hopefully will move
        (->Move (towards player step)))
      ; TODO should look ahead if we have to move diagonally FROM the door in the next step and kick door down in advance if necessary
      (if (door? to-tile)
        (if (diagonal dir)
          (if (= :door-open (:feature to-tile))
            (->Close dir)
            (->Kick dir))
          (if (= :door-locked (:feature to-tile))
            (->Kick dir)
            (->Open dir)))))))

(defn travel [to]
  (reify ActionHandler
    (choose-action [this game]
      (if-let [p (path-travelling game to)]
        (if (empty? p)
          (log/debug "reached travel target")
          (or (move-travelling game p)
              (log/debug "failed to move according to path")))
        (do (log/debug "travel target not pathable")
            (Thread/sleep 200)))))) ; XXX

(defn walk [to]
  (reify ActionHandler
    (choose-action [this game]
      (let [level (-> game :dungeon curlvl)]
       (if-let [p (path-walking game to)]
        (if-let [n (first p)]
          (if (passable-walking? level (:player game) n)
            (if (get-in (:monsters level) [n :peaceful] nil)
              (->Search) ; hopefully will move
              (->Move (towards (:player game) n)))
            (log/debug "walk reached unwalkable target"))
          (log/debug "reached walk target"))
        (do (log/debug "walk target not pathable")
            (Thread/sleep 200))))))) ; XXX
