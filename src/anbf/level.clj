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

(defn likely-walkable?
  "Less optimistic about unexplored tiles than walkable?, but still returns true for item in an (unknown) wall."
  [level tile]
  (and (walkable? tile)
       (or (if-let [m (monster-at level tile)]
             (not= \; (:glyph m)))
           (:feature tile)
           (item? tile))))

(defn safely-walkable? [level tile]
  (and (likely-walkable? level tile)
       (not (monster-at level tile))
       (not ((some-fn trap? ice? drawbridge-lowered?) tile))))

(defn tile-seq
  "a seq of all 80x20 tiles on the level, left to right, top to bottom"
  [level]
  (apply concat (:tiles level)))

(def wiztower-boundary
  (->> (concat (for [y (range 5 20)]
                 [(position 23 y)
                  (position 51 y)])
               (for [x (range 23 52)]
                 [(position x 5)
                  (position x 19)]))
       flatten set))

(def wiztower-inner-boundary
  (->> (concat (for [y (range 6 19)]
                 [(position 24 y)
                  (position 51 y)])
               (for [x (range 24 52)]
                 [(position x 6)
                  (position x 18)]))
       flatten set))

(def wiztower-rect
  (rectangle (position 23 5) (position 52 20)))

(def blueprints
  [{:branch :main
    :tag :medusa-1
    :undiggable true
    :cutoff-rows [1]
    :cutoff-cols [0 1 77 78 79]
    :monsters {{:x 38 :y 12} (name->monster "Medusa")}
    :features {{:x 38 :y 12} :stairs-down
               {:x 32 :y 14} :door-secret
               {:x 48 :y 9} :door-secret
               {:x 40 :y 14} :squeaky
               {:x 40 :y 9} :squeaky}}
   {:branch :main
    :tag :medusa-2
    :undiggable true
    :monsters {{:x 70 :y 12} (name->monster "Medusa")}
    :features {{:x 70 :y 12} :stairs-down
               {:x 3 :y 7} :door-secret
               {:x 8 :y 16} :door-secret
               {:x 62 :y 7} :door-secret
               {:x 68 :y 10} :door-secret
               {:x 75 :y 16} :door-secret
               {:x 5 :y 14} :magictrap}}
   {:branch :main
    :tag :castle
    :undiggable true
    :undiggable-floor true
    :cutoff-rows [1 2]
    :cutoff-cols [0 78 79]
    :features {{:x 55 :y 11} :door-secret
               {:x 55 :y 13} :door-secret
               {:x 46 :y 12} :door-secret
               {:x 48 :y 12} :trapdoor
               {:x 52 :y 12} :trapdoor
               {:x 56 :y 12} :trapdoor
               {:x 60 :y 12} :trapdoor
               {:x 63 :y 12} :trapdoor}}
   {:branch :main
    :tag :votd
    :undiggable true
    :undiggable-floor true
    :features {{:x 68 :y 19} :stairs-up
               {:x 6 :y 3} :door-secret
               {:x 10 :y 6} :door-secret
               {:x 8 :y 8} :door-secret}}
   {:branch :main
    :undiggable true
    :tag :asmodeus
    :features {{:x 28 :y 8} :door-secret
               {:x 19 :y 8} :spikepit
               {:x 17 :y 16} :door-secret
               {:x 20 :y 12} :door-secret
               {:x 22 :y 12} :firetrap
               {:x 27 :y 13} :stairs-down}}
   {:branch :main
    :undiggable true
    :tag :baalzebub
    :features {{:x 38 :y 12} :door-secret
               {:x 57 :y 11} :door-secret
               {:x 62 :y 14} :door-secret
               {:x 70 :y 12} :door-secret}}
   {:branch :main
    :tag :wiztower-level
    :undiggable-tiles wiztower-inner-boundary}
   {:branch :main
    :tag :fake-wiztower
    :features {{:x 38 :y 12} :portal
               {:x 39 :y 12} :squeaky
               {:x 37 :y 12} :squeaky
               {:x 38 :y 13} :squeaky
               {:x 38 :y 11} :squeaky}}
   {:branch :quest
    :role :valkyrie
    :dlvl "Home 1"
    :monsters {{:y 6, :x 22} (name->monster "Norn")}
    :cutoff-rows [1 2]
    :cutoff-cols [0 1 78 79]}
   {:branch :quest
    :role :valkyrie
    :dlvl "Home 2"
    :cutoff-cols [79]}
   {:branch :quest
    :role :valkyrie
    :dlvl "Home 3"
    :cutoff-cols [79]}
   {:branch :quest
    :role :valkyrie
    :dlvl "Home 4"
    :cutoff-cols [79]}
   {:branch :quest
    :role :valkyrie
    :dlvl "Home 5"
    :cutoff-cols [79]}
   {:branch :quest
    :role :valkyrie
    :dlvl "Home 6"
    :cutoff-cols [79]}
   {:branch :mines
    :tag :minetown-grotto
    :features {{:x 48 :y 4} :stairs-down}}
   {:branch :vlad
    :tag :bottom
    :features {{:x 20 :y 13} :door-secret
               {:x 25 :y 14} :door-secret
               {:x 29 :y 14} :door-secret
               {:x 30 :y 13} :door-secret
               {:x 30 :y 9} :door-secret
               {:x 29 :y 8} :door-secret
               {:x 25 :y 8} :door-secret
               {:x 21 :y 8} :door-secret
               {:x 20 :y 9} :door-secret
               {:x 21 :y 14} :door-secret}}
   {:branch :vlad
    :tag :middle
    :features {{:x 18 :y 9} :door-secret
               {:x 19 :y 8} :door-secret
               {:x 23 :y 8} :door-secret
               {:x 27 :y 8} :door-secret
               {:x 28 :y 9} :door-secret
               {:x 28 :y 13} :door-secret
               {:x 27 :y 14} :door-secret
               {:x 23 :y 14} :door-secret
               {:x 19 :y 14} :door-secret
               {:x 18 :y 13} :door-secret}}
   {:branch :vlad
    :tag :end
    :features {{:x 19 :y 8} :door-secret
               {:x 23 :y 8} :door-secret
               {:x 27 :y 8} :door-secret
               {:x 27 :y 14} :door-secret
               {:x 23 :y 14} :door-secret
               {:x 19 :y 14} :door-secret}}
   {:branch :wiztower
    :tag :bottom
    :cutoff-rows (concat (range 1 5) (range 19 22))
    :cutoff-cols (concat (range 0 22) (range 52 80))
    :undiggable-tiles (remove (set (rectangle (position 32 10) (position 38 16)))
                              wiztower-rect)
    :features {{:x 43 :y 17} :door-secret
               {:x 42 :y 11} :door-secret
               {:x 40 :y 7} :door-secret
               {:x 48 :y 8} :door-secret
               {:x 27 :y 10} :door-secret
               {:x 30 :y 16} :door-secret
               {:x 35 :y 13} :stairs-up
               {:x 34 :y 13} :squeaky
               {:x 36 :y 13} :squeaky
               {:x 35 :y 12} :squeaky
               {:x 35 :y 14} :squeaky}}
   {:branch :wiztower
    :tag :middle
    :undiggable true
    :cutoff-rows (concat (range 1 5) (range 19 22))
    :cutoff-cols (concat (range 0 22) (range 52 80))
    :features {{:x 42 :y 17} :door-secret
               {:x 43 :y 14} :door-secret
               {:x 46 :y 14} :door-secret
               {:x 35 :y 16} :door-secret
               {:x 30 :y 16} :door-secret
               {:x 48 :y 8} :door-secret
               {:x 40 :y 8} :door-secret
               {:x 49 :y 11} :door-secret
               {:x 26 :y 13} :door-secret
               {:x 28 :y 10} :door-secret
               {:x 31 :y 10} :door-secret
               {:x 32 :y 7} :door-secret}}
   {:branch :wiztower
    :tag :end
    :cutoff-rows (concat (range 1 5) (range 19 22))
    :cutoff-cols (concat (range 0 23) (range 52 80))
    :undiggable-tiles (remove (set (rectangle (position 37 8) (position 43 14)))
                              wiztower-rect)
    :features {{:x 29 :y 9} :door-secret
               {:x 32 :y 8} :door-secret
               {:x 27 :y 14} :door-secret
               {:x 35 :y 17} :door-secret
               {:x 47 :y 16} :door-secret
               {:x 49 :y 16} :door-secret
               {:x 49 :y 9} :door-secret
               {:x 41 :y 11} :squeaky
               {:x 39 :y 11} :squeaky
               {:x 40 :y 12} :squeaky
               {:x 40 :y 10} :squeaky}}
   {:role :samurai
    :dlvl "Home 3"
    :branch :quest
    :features {{:x 28 :y 15} :door-secret
               {:x 51 :y 8} :door-secret}}
   {:role :samurai
    :dlvl "Home 1"
    :undiggable true
    :branch :quest
    :leader {:y 6, :x 22}
    :monsters {{:y 6, :x 22} (name->monster "Lord Sato")}
    :features {{:x 29 :y 6} :door-secret
               {:x 52 :y 6} :door-secret}}])
