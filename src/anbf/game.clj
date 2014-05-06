; representation of the game world

(ns anbf.game
  (:require [clojure.tools.logging :as log]
            [anbf.player :refer :all]
            [anbf.dungeon :refer :all]
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
  (Game. nil (new-player) (new-dungeon) 0 0)) ; TODO

(defrecord Monster [])

(defrecord Item [])

(defn- update-game [game status delegator]
  ; TODO not just merge, emit events on changes
  (->> game keys (select-keys status) (merge game)))

(defn game-handler
  [game delegator]
  (reify
    RedrawHandler
    (redraw [_ frame]
      (swap! game assoc-in [:frame] frame))
    BOTLHandler
    (botl [_ status]
      (swap! game update-game status delegator))
    FullFrameHandler
    (full-frame [_ frame]
      (send delegator choose-action @game))))
