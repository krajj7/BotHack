(ns anbf.level
  (:require [clojure.tools.logging :as log]
            [anbf.montype :refer :all]
            [anbf.position :refer :all]
            [anbf.tile :refer :all]))

(defrecord Level
  [dlvl
   branch-id
   tags ; subset #{:shop :oracle :minetown :vault :medusa :castle :votd ...}
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
       (partition 80) (mapv vec)))

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
  [{:branch :main
    :tag :medusa-1
    :monsters {{:x 28 :y 12} (name->monster "Medusa")}
    :features {{:x 32 :y 14} :door-secret
               {:x 48 :y 9} :door-secret
               {:x 30 :y 14} :squeaky
               {:x 30 :y 9} :squeaky
               {:x 28 :y 12} :stairs-down}}
   {:branch :main
    :tag :medusa-2
    :monsters {{:x 70 :y 12} (name->monster "Medusa")}
    :features {{:x 3 :y 7} :door-secret
               {:x 8 :y 16} :door-secret
               {:x 62 :y 7} :door-secret
               {:x 68 :y 10} :door-secret
               {:x 75 :y 16} :door-secret
               {:x 5 :y 14} :magictrap
               {:x 70 :y 12} :stairs-down}}
   {:branch :main
    :tag :castle
    :features {{:x 55 :y 10} :door-secret
               {:x 55 :y 12} :door-secret
               {:x 46 :y 11} :door-secret
               {:x 48 :y 11} :trapdoor
               {:x 52 :y 12} :trapdoor
               {:x 56 :y 12} :trapdoor
               {:x 60 :y 12} :trapdoor
               {:x 63 :y 12} :trapdoor}}
   {:branch :main
    :tag :votd
    :features {{:x 68 :y 18} :stairs-up
               {:x 6 :y 2} :door-secret
               {:x 10 :y 5} :door-secret
               {:x 8 :y 7} :door-secret}}
   {:branch :mines
    :tag :minetown-grotto
    :features {{:x 48 :y 4} :stairs-down}}
   {:role "samurai"
    :dlvl "Home 3"
    :branch :quest
    :features {{:x 28 :y 15} :door-secret
               {:x 51 :y 8} :door-secret}}
   {:role "samurai"
    :dlvl "Home 1"
    :branch :quest
    :leader {:y 6, :x 22}
    :monsters {{:y 6, :x 22} (name->monster "Lord Sato")}
    :features {{:x 29 :y 6} :door-secret
               {:x 52 :y 6} :door-secret}}])
