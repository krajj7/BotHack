(ns anbf.position)

(defrecord Position [x y]
  anbf.bot.IPosition
  (x [this] (:x this))
  (y [this] (:y this)))

(defmethod print-method Position [pos w]
  (.write w (str "#Position[" (:x pos) " " (:y pos) "]")))

(defn valid-position? [{:keys [x y]}]
  (and (<= 0 x 79) (<= 1 y 21)))

(defn at
  "Tile of the level at given terminal position"
  ([level {:keys [x y] :as pos}]
   {:pre [(valid-position? pos)]}
   (-> level :tiles (nth (dec y)) (nth x)))
  ([level x y]
   (at level (->Position x y))))

(defn adjacent? [pos1 pos2]
  (and (<= (Math/abs (- (:x pos1) (:x pos2))) 1)
       (<= (Math/abs (- (:y pos1) (:y pos2))) 1)))
