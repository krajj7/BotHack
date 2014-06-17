(ns anbf.position)

(defn position [of]
  ; TODO IPosition for java
  (select-keys of [:x :y]))

(defn valid-position? [{:keys [x y]}]
  (and (<= 0 x 79) (<= 1 y 21)))

(defn at
  "Tile of the level at given terminal position"
  [level {:keys [x y] :as pos}]
  {:pre [(valid-position? pos)]}
  (get-in level [:tiles (dec y) x]))

(def directions [:NW :N :NE :W :E :SW :S :SE])
(def opposite {:NW :SE :N :S :NE :SW :W :E :E :W :SW :NE :S :N :SE :NW})

(def deltas [[-1  -1] [0  -1] [1  -1] [-1  0] [1  0] [-1 1] [0 1] [1 1]])
(def dirmap (merge (zipmap directions deltas)
                   (zipmap deltas directions)))

(def straight #{:N :W :S :E})
(def diagonal #{:NW :SW :NE :SE})

(def <=> (comp #(Integer/signum %) compare))

(defn adjacent? [pos1 pos2]
  (and (<= (Math/abs (- (:x pos1) (:x pos2))) 1)
       (<= (Math/abs (- (:y pos1) (:y pos2))) 1)))

(defn neighbors
  ([level tile]
   (map #(at level %) (neighbors tile)))
  ([pos]
   (filter valid-position?
           (map #(hash-map :x (+ (:x pos) (% 0))
                           :y (+ (:y pos) (% 1))) deltas))))

(defn in-direction [from dir]
  {:pre [(valid-position? from)]}
  (assoc (position from)
         :x (+ ((dirmap dir) 0) (:x from))
         :y (+ ((dirmap dir) 1) (:y from))))

(defn towards [from to]
  (get dirmap ((juxt #(<=> (:x %2) (:x %1))
                     #(<=> (:y %2) (:y %1)))
               from to)))

(defn diagonal? [from to]
  (diagonal (towards from to)))

(defn straight? [from to]
  (straight (towards from to)))

(defn distance [from to]
  (max (Math/abs (- (:x from) (:x to)))
       (Math/abs (- (:y from) (:y to)))))
