; representation of the game world

(ns anbf.game
  (:import [anbf NHFov NHFov$TransparencyInfo])
  (:require [clojure.tools.logging :as log]
            [anbf.player :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.tile :refer :all]
            [anbf.position :refer :all]
            [anbf.handlers :refer :all]
            [anbf.tracker :refer :all]
            [anbf.util :refer :all]
            [anbf.delegator :refer :all]))

(defrecord Game
  [frame
   player
   dungeon
   turn
   score]
  anbf.bot.IGame
  (frame [this] (:frame this))
  (player [this] (:player this)))

(defn new-game []
  (Game. nil (new-player) (new-dungeon) 0 0))

(defn- update-game [game status delegator]
  (->> game keys (select-keys status) (merge game)))

(defn- update-by-botl [game status delegator]
  (-> game
      (update-in [:dungeon] update-dlvl status delegator)
      (update-in [:player] update-player status delegator)
      (update-game status delegator)))

(defn- update-fov [game cursor]
  (assoc-in game [:player :fov]
            (.calculateFov (NHFov.) (:x cursor) (dec (:y cursor))
                           (reify NHFov$TransparencyInfo
                             (isTransparent [_ x y]
                               (if (and (<= 0 y 20) (<= 0 x 79))
                                 (boolean
                                   (transparent?
                                     (((-> game curlvl :tiles) y) x)))
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
      (update-in [:player] #(into % (:cursor frame))) ; update position
      (update-dungeon frame)
      (update-fov cursor)
      (track-monsters game)
      update-explored))

(defn game-handler
  [{:keys [game delegator] :as anbf}]
  (reify
    RedrawHandler
    (redraw [_ frame]
      (swap! game assoc-in [:frame] frame))
    BOTLHandler
    (botl [_ status]
      (swap! game update-by-botl status delegator))
    FullFrameHandler
    (full-frame [_ frame]
      (swap! game update-map frame delegator))
    ToplineMessageHandler
    (message [_ text]
      (if-let [room (room-type text)]
        (update-on-known-position anbf mark-room room)))))
