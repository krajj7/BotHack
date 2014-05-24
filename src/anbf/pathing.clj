(ns anbf.pathing
  (:require [clojure.data.priority-map :refer [priority-map]]
            [clojure.tools.logging :as log]
            [anbf.position :refer :all]
            [anbf.dungeon :refer :all]))

(def <=> (comp #(Integer/signum %) compare))

(def directions
  {[-1  1] :NW [0  1] :N [1  1] :NE
   [-1  0] :W            [1  0] :E
   [-1 -1] :SW [0 -1] :S [1 -1] :SE})

(defn towards [from to]
  (get directions ((juxt #(<=> (:x %2) (:x %1))
                         #(<=> (:y %1) (:y %2)))
                   from to)))

(defn diagonal-passable? [tile]
  (if-not (= :door-open (:feature tile))
    true)) ;TODO no diagonal near boulders, squeeze-spaces

(defn passable-walking? [level from to]
  (let [from-tile (at level from)
        to-tile (at level to)]
    (and (or (walkable? to-tile)
             (= nil (:feature to-tile))) ; try to path via unexplored tiles
         (or (#{:N :W :S :E} (towards from to))
             (and (diagonal-passable? from-tile)
                  (diagonal-passable? to-tile))))))

(defn walking-cost [tile]
  ;(cond->)
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
  (loop [closed #{}
         open (priority-map [from [] 0] 1)]
    ;(log/debug (count open))
    (if (empty? open) nil
      (let [[[node path dist] total] (peek open)
            delta (distance node to)]
        (cond
          (zero? delta) path
          (and (= 1 delta)
               (not-any? #(passable? % to) (neighbors to))) (conj path to)
          :else (recur (conj closed node)
                       (into (pop open)
                             (for [nbr (filter #(and (not (closed %))
                                                     (passable? node %))
                                               (neighbors node))]
                               (let [new-node nbr
                                     new-path (conj path nbr)
                                     new-dist (+ dist (inc (extra-cost nbr)))]
                                 [[new-node new-path new-dist]
                                   (+ new-dist delta)])))))))))

(def path a*)

(defn path-walking [game to]
  (let [level (curlvl (:dungeon game))]
    (path (-> game :player) to
          (partial passable-walking? level)
          #(walking-cost (at level %)))))
