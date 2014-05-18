; representation of the game world

(ns anbf.game
  (:import [anbf NHFov NHFov$TransparencyInfo])
  (:require [clojure.tools.logging :as log]
            [anbf.frame :refer [colormap]]
            [anbf.player :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.position :refer :all]
            [anbf.delegator :refer :all]))

(defrecord Game
  [frame
   player
   dungeon
   fov
   turn
   score]
  anbf.bot.IGame
  (frame [this] (:frame this))
  (player [this] (:player this)))

(defn new-game []
  (Game. nil (new-player) (new-dungeon) nil 0 0))

(defn- update-game [game status delegator]
  ; TODO not just merge, emit events on changes
  (->> game keys (select-keys status) (merge game)))

(defn- game-botl [game status delegator]
  (-> game
      (update-in [:dungeon] update-dlvl status delegator)
      (update-in [:player] update-player status delegator)
      (update-game status delegator)))

(defn lit?
  "Actual lit-ness is hard to determine and not that important, this is a pessimistic guess."
  [game tile]
  (or (adjacent? (:position tile) (:position (:player game))) ; TODO actual player light radius
      (= \. (:glyph tile))
      (and (= \# (:glyph tile)) (= :white (colormap (:color tile))))))

(defn in-fov? [game {:keys [position]}]
  (get-in (:fov game) [(dec (:y position)) (:x position)]))

(defn visible? [game tile]
  "Only considers normal sight, not infravision/ESP/..."
  (and ; TODO not blind
       (in-fov? game tile)
       (lit? game tile)))

(defn- update-fov [game cursor]
  (assoc game :fov
         (.calculateFov (NHFov.) (:x cursor) (dec (:y cursor))
                        (reify NHFov$TransparencyInfo
                          (isTransparent [_ x y]
                            (if (and (<= 0 y 20) (<= 0 x 79))
                              (boolean
                                (transparent?
                                  (((-> game :dungeon curlvl :tiles) y) x)))
                              false))))))

(defn- update-visible-tile [tile]
  (assoc tile
         :seen true
         :feature (if (= (:glyph tile) \space) :rock (:feature tile))))

(defn- update-explored [game]
  (update-in game [:dungeon :levels (branch-key (:dungeon game))
                   (:dlvl (:dungeon game)) :tiles]
             (partial map-tiles (fn [tile]
                                  (if (visible? game tile)
                                    (update-visible-tile tile)
                                    tile)))))

(defn- update-map [game {:keys [cursor] :as frame} delegator]
  (-> game
      (assoc-in [:player :position] (:cursor frame))
      (update-dungeon frame)
      (update-fov cursor)
      update-explored))

(defn game-handler
  [game delegator]
  (reify
    RedrawHandler
    (redraw [_ frame]
      (swap! game assoc-in [:frame] frame))
    BOTLHandler
    (botl [_ status]
      (swap! game game-botl status delegator))
    FullFrameHandler
    (full-frame [_ frame]
      (->> (swap! game update-map frame delegator)
           (send delegator choose-action)))))
