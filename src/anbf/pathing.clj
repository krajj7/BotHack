(ns anbf.pathing
  (:require [clojure.data.priority-map :refer [priority-map-keyfn]]
            [clojure.tools.logging :as log]
            [anbf.position :refer :all]
            [anbf.monster :refer :all]
            [anbf.delegator :refer :all]
            [anbf.actions :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.util :refer :all]
            [anbf.tile :refer :all]))

(defn base-cost [level dir tile]
  {:pre [(and (some? level) (some? dir) (some? tile))]}
  (let [feature (:feature tile)]
    (cond-> 1
      (= :trap feature) (+ 5) ; TODO trap types
      (diagonal dir) (+ 0.1)
      (not (#{:stairs-up :stairs-down} feature)) (+ 0.1)
      (not (or (:dug tile) (:walked tile))) (+ 0.2)
      (and (not (:walked tile)) (= :floor feature)) (+ 0.5))))

(defn diagonal-walkable? [game {:keys [feature] :as tile}]
  (not= :door-open feature)) ;TODO no diagonal near boulders, squeeze-spaces

(defn- a* [from to move-fn]
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
               (not-any? #(move-fn % to) (neighbors to))) (conj final-path to)
          :else (recur
                  (assoc closed node path)
                  (merge-with
                    (partial min-key first)
                    (pop open)
                    (into {}
                          (for [nbr (remove closed (neighbors node))
                                :let [[cost action] (move-fn node nbr)]
                                :when (some? action)]
                            (let [new-dist (int (+ dist cost))]
                              [nbr [(+ new-dist delta) new-dist node]]))))))))))

(defn- dijkstra [from goal? move-fn]
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
                                   (for [nbr (remove closed (neighbors node))
                                         :let [[cost action] (move-fn node nbr)]
                                         :when (some? action)]
                                     [nbr [(+ dist cost) node]])))))))))

(defn passable-walking?
  "Only needs Move action, no door opening etc., will path through monsters"
  [game level from to]
  (let [from-tile (at level from)
        to-tile (at level to)]
    (and (or (walkable? to-tile)
             (and (not (boulder? to-tile))
                  (nil? (:feature to-tile)))) ; try to path via unexplored tiles
         (or (straight (towards from to))
             (and (diagonal-walkable? game from-tile)
                  (diagonal-walkable? game to-tile))))))

(defn- random-move [{:keys [player] :as game} level]
  (some->> (neighbors player)
           (remove (partial monster-at level))
           (filterv (partial passable-walking? game level player))
           (#(if (seq %) % nil))
           rand-nth
           (towards player)
           ->Move))

(defn fidget
  "Move around randomly or wait to make a peaceful move out of the way"
  ([game] (fidget game (curlvl game)))
  ([game level] (fidget game level nil))
  ([{:keys [player] :as game} level target]
   (with-handler priority-top
     (fn [anbf]
       (log/debug "fidgeting")
       (if (some? target)
         (reify AboutToChooseActionHandler
           (about-to-choose [_ new-game]
             (when (:peaceful (curlvl-monster-at new-game target)) ; target did not move
               (log/debug "blocked by peaceful" target)
               (swap! (:game anbf) update-curlvl-at target
                      update-in [:blocked] (fnil inc 0)))))))
     (or (random-move game level)
         (->Search)))))

(defn dare-kick? [level tile]
  (and (not ((:tags level) :minetown))
       (not (shop? tile))))

(defn move
  "Returns [cost Action] for a move, if it is possible"
  ([game level from to]
   (move game from to #{}))
  ([game level from to opts]
   (let [to-tile (at level to)
         dir (towards from to-tile)
         monster (monster-at level to)]
     (if-let [step
              (or (if (passable-walking? game level from to)
                    (if-not monster
                      [0 (->Move dir)]
                      (if-not (:peaceful monster)
                        [30 (->Move dir)]
                        (if ((fnil <= 0) (:blocked to-tile) 10)
                          [50 (fidget game level to-tile)])))) ; hopefully will move
                  ; TODO should look ahead if we have to move diagonally FROM the door in the next to and kick door down in advance if necessary
                  ; TODO digging/levi
                  (if (:trapped (:player game))
                    (if-let [step (random-move game level)]
                      [0 step]))
                  (if (and (door? to-tile)
                           (not monster)
                           (not (item? (:glyph to-tile))) ; don't try to break blocked doors
                           (not (:walking opts)))
                    (if (diagonal dir)
                      (if (dare-kick? level to-tile)
                        (if (= :door-open (:feature to-tile))
                          [8 (->Close dir)]
                          [5 (->Kick dir)]))
                      (if (= :door-locked (:feature to-tile))
                        ; TODO unlocking
                        (if (dare-kick? level to-tile)
                          [5 (->Kick dir)])
                        [3 (->Open dir)]))))]
       (update-in step [0] + (base-cost level dir to-tile))))))

(defrecord Path
  [step ; next Action to perform to move along path
   path ; vector of remaining positions
   target]) ; position of target

; TODO could use builtin pathfinding (_ command) if the path is all explored
(defn- path-step [from move-fn path]
  (if (seq path)
    (nth (move-fn from (nth path 0)) 1)))

(defn- get-a*-path [from to move-fn opts]
  (log/debug "a*")
  (if-let [path (a* from to move-fn)]
    (if (seq path)
      (if (:adjacent opts)
        (->Path (path-step from move-fn path) (pop path) to)
        (and (or (= 1 (count path)) (move-fn (-> path pop peek) to))
             (->Path (path-step from move-fn path) path to)))
      (->Path nil [] to))))

(defn navigate
  "Return shortest Path for given target position or predicate, will use A* or Dijkstra's algorithm as appropriate.
  Supported options:
    :walking - don't use actions except Move (no door opening etc.)
    :adjacent - path to closest adjacent tile instead of the target directly"
  [{:keys [player] :as game} pos-or-goal-fn & opts]
  [{:pre [(or (fn? pos-or-goal-fn) (position pos-or-goal-fn))]}]
  ;(log/debug "navigating" pos-or-goal-fn opts)
  (let [opts (apply hash-set opts)
        level (curlvl game)
        move-fn #(move game level %1 %2 opts)]
    (if-not (fn? pos-or-goal-fn)
      (get-a*-path player pos-or-goal-fn move-fn opts)
      (let [goal-fn #(pos-or-goal-fn (at level %))]
        (if (:adjacent opts)
          (let [goal-set (->> level :tiles (apply concat) (filter goal-fn)
                              (mapcat neighbors) (into #{}))]
            (case (count goal-set)
              0 nil
              1 (get-a*-path player (first goal-set) move-fn opts)
              (if-let [path (dijkstra player goal-set move-fn)]
                (->Path (path-step player move-fn path) path
                        (->> (or (peek path) player) neighbors
                             (filter goal-fn) first)))))
          ; not searching for adjacent
          (if-let [goal-seq (->> level :tiles (apply concat) (filter goal-fn)
                                 (take 2) seq)]
            (if (= 2 (count goal-seq))
              (if-let [path (dijkstra player goal-fn move-fn)]
                (->Path (path-step player move-fn path) path (peek path)))
              (get-a*-path player (first goal-seq) move-fn opts))))))))
