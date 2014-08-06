(ns anbf.level
  (:require [clojure.tools.logging :as log]
            [anbf.montype :refer :all]
            [anbf.position :refer :all]
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

(defn update-at
  "Update the level tile at given position by applying update-fn to its current value and args"
  [level pos update-fn & args]
  (apply update-in level [:tiles (dec (:y pos)) (:x pos)] update-fn args))

(defn monster-at [level pos]
  {:pre [(:monsters level)]}
  (get-in level [:monsters (position pos)]))

(defn reset-monster [level monster]
  (assoc-in level [:monsters (position monster)] monster))
