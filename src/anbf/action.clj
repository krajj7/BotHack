(ns anbf.action
  (:require [clojure.tools.logging :as log]
            [anbf.util :refer :all]))

(defprotocol Action
  (handler [this anbf]
           "Retuns nil or event/command handler that will be registered just before executing the action and deregistered when the next action is chosen.")
  (trigger [this]
           "Returns the string to write to NetHack to perform the action."))

(extend-type anbf.bot.IAction
  Action
  (handler [this anbf] (.handler this anbf))
  (trigger [this] (.trigger this)))
