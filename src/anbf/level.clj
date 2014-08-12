(ns anbf.level
  (:require [clojure.tools.logging :as log]
            [anbf.montype :refer :all]
            [anbf.position :refer :all]
            [anbf.tile :refer :all]))

(defrecord Level
  [dlvl
   branch-id
   tags ; :shop, :oracle, :minetown, :vault, ...
   blueprint ; for special levels
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
  (Level. dlvl branch-id #{} nil (initial-tiles) {}))

(defn update-at
  "Update the level tile at given position by applying update-fn to its current value and args"
  [level pos update-fn & args]
  (apply update-in level [:tiles (dec (:y pos)) (:x pos)] update-fn args))

(defn monster-at [level pos]
  {:pre [(:monsters level)]}
  (get-in level [:monsters (position pos)]))

(defn reset-monster [level monster]
  (assoc-in level [:monsters (position monster)] monster))

(def blueprints
  [{:role "samurai"
    :dlvl "Home 3"
    :branch :quest
    :features {{:x 28 :y 15} :door-secret
               {:x 51 :y 8} :door-secret}}
   {:role "samurai"
    :dlvl "Home 1"
    :branch :quest
    :leader {:y 6, :x 22}
    :monsters {{:y 6, :x 22} (by-name "Lord Sato")}
    :features {{:x 29 :y 6} :door-secret
               {:x 52 :y 6} :door-secret}}])
