; representation of the game world

(ns anbf.game
  (:require [clojure.tools.logging :as log]
            [anbf.player :refer :all]
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

(defrecord Monster [])

(defrecord Item [])

(defn game-handler
  [game delegator]
  (reify
    RedrawHandler
    (redraw [_ frame]
      (swap! game assoc-in [:frame] frame))
    FullFrameHandler
    (full-frame [_ frame]
      (send delegator choose-action @game))))
