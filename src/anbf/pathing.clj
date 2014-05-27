(ns anbf.pathing
  (:require [clojure.data.priority-map :refer [priority-map-keyfn]]
            [clojure.tools.logging :as log]
            [anbf.position :refer :all]
            [anbf.delegator :refer :all]
            [anbf.action :refer :all]
            [anbf.dungeon :refer :all]))

(def <=> (comp #(Integer/signum %) compare))

(def directions
  {[-1  1] :NW [0  1] :N [1  1] :NE
   [-1  0] :W            [1  0] :E
   [-1 -1] :SW [0 -1] :S [1 -1] :SE})

(def straight #{:N :W :S :E})
(def diagonal #{:NW :SW :NE :SE})

(defn towards [from to]
  (get directions ((juxt #(<=> (:x %2) (:x %1))
                         #(<=> (:y %1) (:y %2)))
                   from to)))

(defn diagonal? [from to]
  (diagonal (towards from to)))

(defn straight? [from to]
  (straight (towards from to)))

(defn diagonal-walkable? [tile]
  (if (not-any? #(= % (:feature tile)) [nil :door-open])
    true)) ;TODO no diagonal near boulders, squeeze-spaces

(defn passable-walking?
  "Only needs Move action, no door opening etc."
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
               (or (not= :door-locked (:feature to-tile))
                   (not (:shop to-tile)))))))

(defn move-cost [level from to]
  ;(cond->)
  ; diagonal open door +2 (close+kick)
  ; diagonal closed door +1 (kick)
  0) ; TODO

(defn neighbors
  ([level tile]
   (map #(at level %) (neighbors tile)))
  ([pos]
   (filter valid-position?
           (map #(hash-map :x (+ (:x pos) (% 0))
                           :y (+ (:y pos) (% 1)))
                (keys directions)))))

(defn distance [from to]
  (max (Math/abs (- (:x from) (:x to)))
       (Math/abs (- (:y from) (:y to)))))

(defn a* [from to passable? extra-cost]
  "Extra-cost must always return non-negative values, target tile may not be passable, but will always be included in the path"
  (loop [closed {}
         open (priority-map-keyfn first (position from) [0 0])]
    ;(log/debug (count open))
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
