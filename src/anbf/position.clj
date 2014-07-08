(ns anbf.position
  (:require [clojure.tools.logging :as log]))

(defn position [of]
  {:post [(:x %) (:y %)]}
  ; TODO IPosition for java
  (select-keys of [:x :y]))

(defn valid-position?
  ([x y] (and (<= 0 x 79) (<= 1 y 21)))
  ([{:keys [x y]}] (valid-position? x y)))

(defn at
  "Tile of the level at given terminal position"
  ([level x y]
   {:pre [(valid-position? x y)]}
   (get-in level [:tiles (dec y) x]))
  ([level {:keys [x y] :as pos}] (at level x y)))

(def directions [:NW :N :NE :W :E :SW :S :SE])
(def opposite {:NW :SE :N :S :NE :SW :W :E :E :W :SW :NE :S :N :SE :NW})

(def deltas [[-1 -1] [0 -1] [1 -1] [-1 0] [1 0] [-1 1] [0 1] [1 1]])
(def dirmap (merge (zipmap directions deltas)
                   (zipmap deltas directions)))

(def straight #{:N :W :S :E})
(def diagonal #{:NW :SW :NE :SE})

(defn adjacent? [pos1 pos2]
  (and (<= (Math/abs ^long (unchecked-subtract (:x pos1) (:x pos2))) 1)
       (<= (Math/abs ^long (unchecked-subtract (:y pos1) (:y pos2))) 1)))

(defn neighbors
  ([level tile]
   (map #(at level %) (neighbors tile)))
  ([pos]
   (filter valid-position?
           (map #(hash-map :x (unchecked-add (:x pos) (% 0))
                           :y (unchecked-add (:y pos) (% 1))) deltas))))

(defn in-direction [from dir]
  {:pre [(valid-position? from)]}
  (let [res (assoc (position from)
                   :x (unchecked-add ((dirmap dir) 0) (:x from))
                   :y (unchecked-add ((dirmap dir) 1) (:y from)))]
    (if (valid-position? res)
      res)))

(defn towards [from to]
  (get dirmap [(Long/compare (:x to) (:x from))
               (Long/compare (:y to) (:y from))]))

(defn diagonal? [from to]
  (diagonal (towards from to)))

(defn straight? [from to]
  (straight (towards from to)))

(defn distance [from to]
  (max (Math/abs ^long (unchecked-subtract (:x from) (:x to)))
       (Math/abs ^long (unchecked-subtract (:y from) (:y to)))))
