(ns bothack.action
  (:require [clojure.tools.logging :as log]))

(defprotocol Action
  (handler [this bh]
           "Retuns nil or an event/prompt handler that will be registered just before executing the action and deregistered when the next action is chosen.  May also modify the game state immediately without returning a handler.")
  (trigger [this]
           "Returns the string to write to NetHack to perform the action."))

(extend-type bothack.bot.IAction
  Action
  (handler [this bh] (.handler this bh))
  (trigger [this] (.trigger this)))
