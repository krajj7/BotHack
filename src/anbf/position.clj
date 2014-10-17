(ns anbf.position
  (:require [clojure.tools.logging :as log]))

(defn position
  ([x y] {:x x :y y})
  ([of] {:post [(:x %) (:y %)]} (select-keys of [:x :y])))

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

(defn towards [from to]
  (get dirmap [(Long/compare (:x to) (:x from))
               (Long/compare (:y to) (:y from))]))

(defn diagonal? [from to]
  (diagonal (towards from to)))

(defn straight? [from to]
  (straight (towards from to)))

(defn neighbors
  ([level tile]
   (map #(at level %) (neighbors tile)))
  ([pos]
   (for [d deltas
         :let [nbr (position (unchecked-add (:x pos) (d 0))
                             (unchecked-add (:y pos) (d 1)))]
         :when (valid-position? nbr)]
     nbr)))

(defn including-origin
  "Include origin position in given *neighbors function results"
  ([nbr-fn pos]
   (conj (nbr-fn pos) (position pos)))
  ([nbr-fn level pos]
   (conj (nbr-fn level pos) (at level pos))))

(defn straight-neighbors
  ([level tile]
   (filter (partial straight? tile) (neighbors level tile))))

(defn diagonal-neighbors
  ([level tile]
   (filter (partial diagonal? tile) (neighbors level tile))))

(defn in-direction
  ([level from dir]
   (some->> (in-direction from dir) (at level)))
  ([from dir]
   {:pre [(valid-position? from)]}
   (let [res (assoc (position from)
                    :x (unchecked-add ((dirmap dir) 0) (:x from))
                    :y (unchecked-add ((dirmap dir) 1) (:y from)))]
     (if (valid-position? res)
       res))))

(defn distance [from to]
  (max (Math/abs ^long (unchecked-subtract (:x from) (:x to)))
       (Math/abs ^long (unchecked-subtract (:y from) (:y to)))))

(defn rectangle [NW-corner SE-corner]
  (for [x (range (:x NW-corner) (inc (:x SE-corner)))
        y (range (:y NW-corner) (inc (:y SE-corner)))]
    (position x y)))

(defn rectangle-boundary [NW-corner SE-corner]
  (for [x (range (:x NW-corner) (inc (:x SE-corner)))
        y (range (:y NW-corner) (inc (:y SE-corner)))
        :when (or (= x (:x NW-corner)) (= x (:x SE-corner))
                  (= y (:y NW-corner)) (= y (:y SE-corner)))]
    (position x y)))
