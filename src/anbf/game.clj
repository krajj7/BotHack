; representation of the game world

(ns anbf.game
  (:require [clojure.tools.logging :as log]
            [anbf.player :refer :all]
            [anbf.frame :refer :all]
            [anbf.delegator :refer :all]))

(defrecord Game
  [frame
   player
   dungeon
   level
   turn
   score]
  anbf.bot.IGame
  (frame [this] (:frame this))
  (player [this] (:player this)))

(defn new-game []
  (Game. nil (new-player) nil nil 0 0))

(defrecord Dungeon [])

(defrecord Level [player-x player-y])

(defrecord Monster [])

(defrecord Item [])

(defn game-handler
  "Returns a handler that updates the game model according to various events and publishes other events."
  [game delegator]
  (reify
    RedrawHandler
    (redraw [_ frame]
      (swap! game assoc-in [:frame] frame))
    BOTLHandler
    (botl [_ status]
      (swap! game update-in [:player] update-player status delegator))
    FullFrameHandler
    (fullFrame [_ frame]
      (log/debug "TODO update player location in level")
      (send delegator chooseAction @game))))
