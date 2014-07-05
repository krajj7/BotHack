(ns anbf.fov
  (:import [anbf NHFov NHFov$TransparencyInfo])
  (:require [clojure.tools.logging :as log]
            [anbf.dungeon :refer :all]
            [anbf.player :refer :all]
            [anbf.tile :refer :all]
            [anbf.util :refer :all]))

(defn update-fov [game cursor]
  (let [level (curlvl game)]
    (assoc game :fov
           (.calculateFov (NHFov.) (:x cursor) (dec (:y cursor))
                          (reify NHFov$TransparencyInfo
                            (isTransparent [_ x y]
                              (if (and (< 0 y 20) (< 0 x 79))
                                (transparent? (get-in level [:tiles y x]))
                                false)))))))

(defn in-fov? [game pos]
  (get-in game [:fov (dec (:y pos)) (:x pos)]))

(defn visible?
  "Only considers normal sight, not infravision/ESP/..."
  ([game pos] (visible? game (curlvl game) pos))
  ([{:keys [player] :as game} level pos] ; checking visibility only makes sense for current level, if supplied explicitly it performs faster in tight loops (for each tile etc.)
   (and (not (blind? player))
        (in-fov? game pos)
        (lit? player level pos))))
