(ns anbf.level
  (:require [clojure.tools.logging :as log]
            [anbf.tile :refer :all]))

(defrecord Level
  [dlvl
   branch-id
   tags ; :shop, :oracle, :minetown, :vault, ...
   tiles
   monsters] ; { {:x X :y Y} => Monster }
  anbf.bot.ILevel)

(defmethod print-method Level [level w]
  (.write w (str "#anbf.level.Level"
                 (assoc (.without level :tiles) :tiles "<trimmed>"))))

(defn- initial-tiles []
  (->> (for [y (range 21)
             x (range 80)]
         (initial-tile x (inc y)))
       (partition 80) (map vec) vec))

(defn new-level [dlvl branch-id]
  (Level. dlvl branch-id #{} (initial-tiles) {}))
