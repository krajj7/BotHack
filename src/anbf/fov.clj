(ns anbf.fov
  (:import [anbf NHFov NHFov$TransparencyInfo])
  (:require [clojure.tools.logging :as log]
            [anbf.dungeon :refer :all]
            [anbf.player :refer :all]
            [anbf.tile :refer :all]
            [anbf.util :refer :all]))

(defn update-fov [game cursor]
  (assoc game :fov
         (.calculateFov (NHFov.) (:x cursor) (dec (:y cursor))
                        (reify NHFov$TransparencyInfo
                          (isTransparent [_ x y]
                            (if (and (<= 0 y 20) (<= 0 x 79))
                              (boolean
                                (transparent?
                                  (((-> game curlvl :tiles) y) x)))
                              false))))))

(defn in-fov? [game pos]
  (get-in game [:fov (dec (:y pos)) (:x pos)]))

(defn visible? [game tile]
  "Only considers normal sight, not infravision/ESP/..."
  (and (not (blind? (:player game)))
       (in-fov? game tile)
       (lit? game tile)))
