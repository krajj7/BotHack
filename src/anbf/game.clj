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
   score])

(defrecord Dungeon [])

(defrecord Level [player-x player-y])

(defrecord Monster [])

(defrecord Item [])

(defn- update-player [player frame delegator]
  (log/debug "TODO update player by botl"))

(defn game-handler
  "Returns a handler that updates the game model according to various events and publishes other events."
  [game delegator]
  (reify
    RedrawHandler
    (redraw [_ frame]
      (swap! game assoc-in [:frame] frame))
    BOTLHandler
    (botl [_ frame]
      (swap! game update-in [:player] update-player frame delegator))
    FullFrameHandler
    (full-frame [_ frame]
      (log/debug "TODO update player location in level")
      (send-off delegator choose-action @game))))
