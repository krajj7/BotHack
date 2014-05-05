(ns anbf.dungeon
  (:require [clojure.tools.logging :as log]
            [anbf.frame :refer :all]
            [anbf.delegator :refer :all]))

(defrecord Tile
  [glyph
   feature
   walked
   searched
   items
   monster
   engraving]
  anbf.bot.ITile)

(defn- unknown-tile []
  (Tile. \space nil false 0 [] nil nil))

(defrecord Level
  [branch ; :unknown, :main, :mines, ...
   depth
   tiles
   monsters
   items]
  anbf.bot.ILevel)

(defn new-level ; TODO
  ([depth]
   (new-level depth :unknown))
  ([branch depth]
   (Level. depth branch [(repeat 80 (unknown-tile))] [] [])))

(defrecord Dungeon
  [levels
   curlvl]
  anbf.bot.IDungeon)

(defn new-dungeon []) ; TODO

(defn- update-level [level frame]
  ; TODO guess id + parse map ?
  level)

; TODO update levels by curlvl on Dlvl change event (or on followup map-drawn)?

(defn dungeon-handler
  [game delegator]
  (reify
    MapHandler
    (map-drawn [_ frame]
      (swap! game update-in [:dungeon :curlvl] update-level frame))))
