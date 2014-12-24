(ns bothack.fov
  (:import [bothack NHFov NHFov$TransparencyInfo])
  (:require [clojure.tools.logging :as log]
            [bothack.dungeon :refer :all]
            [bothack.player :refer :all]
            [bothack.tile :refer :all]
            [bothack.util :refer :all]))

(defn update-fov [game cursor]
  (let [level (curlvl game)]
    (assoc game :fov
           (.calculateFov (NHFov.) (:x cursor) (dec (:y cursor))
                          (reify NHFov$TransparencyInfo
                            (isTransparent [_ x y]
                              (boolean
                                (if (and (< 0 y 20) (< 0 x 79))
                                  (transparent?
                                    (get-in level [:tiles y x]))))))))))

(defn in-fov? [game pos]
  (get-in game [:fov (dec (:y pos)) (:x pos)]))

(defn visible?
  "Only considers normal sight, not infravision/ESP/..."
  ([game pos] (visible? game (curlvl game) pos))
  ([{:keys [player] :as game} level pos] ; checking visibility only makes sense for current level, if supplied explicitly it performs faster in tight loops (for each tile etc.)
   (and (not (blind? player))
        (in-fov? game pos)
        (lit? player level pos))))
