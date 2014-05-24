(ns anbf.position)

(defn position [of]
  (select-keys of [:x :y]))

(defn valid-position? [{:keys [x y]}]
  (and (<= 0 x 79) (<= 1 y 21)))

(defn at
  "Tile of the level at given terminal position"
  [level {:keys [x y] :as pos}]
  {:pre [(valid-position? pos)]}
  (-> level :tiles (nth (dec y)) (nth x)))

(defn adjacent? [pos1 pos2]
  (and (<= (Math/abs (- (:x pos1) (:x pos2))) 1)
       (<= (Math/abs (- (:y pos1) (:y pos2))) 1)))
