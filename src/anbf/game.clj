; representation of the game world

(ns anbf.game
  (:require [clojure.tools.logging :as log]
            [anbf.player :refer :all]
            [anbf.frame :refer :all]
            [anbf.delegator :refer :all]))

(defrecord Game
  [screen
   player
   dungeon
   level
   turn
   score])

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
      (swap! game assoc-in [:screen] frame))
    BOTLHandler
    (botl [_ status]
      (swap! game update-in [:player] update-player status delegator))
    FullFrameHandler
    (full-frame [_ frame]
      (log/debug "TODO update player location in level")
      (send delegator choose-action @game))))
