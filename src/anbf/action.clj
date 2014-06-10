(ns anbf.action
  (:require [clojure.tools.logging :as log]))

(defprotocol Action
  (handler [this anbf]
           "Retuns nil or event/command handler that will be registered just before executing the action and deregistered when the next action is chosen.  May also modify the game state immediately without returning a handler.")
  (trigger [this]
           "Returns the string to write to NetHack to perform the action."))

(extend-type anbf.bot.IAction
  Action
  (handler [this anbf] (.handler this anbf))
  (trigger [this] (.trigger this)))
