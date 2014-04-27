; interfaces for bots

(ns anbf.bot)

; note: delegator.clj and action.clj generate additional classes in the anbf.bot package

(definterface IANBF
  (^anbf.bot.IANBF registerHandler [handler])
  (^anbf.bot.IANBF registerHandler [^int priority handler])
  (^anbf.bot.IANBF deregisterHandler [handler])
  (^anbf.bot.IANBF replaceHandler [handler-old handler-new])
  (^anbf.bot.IGame game [])
  (^anbf.bot.IPlayer player [])
  (^anbf.bot.IANBF write [^String text]))

(definterface IAction
  (^String trigger []))

(definterface IPlayer
  (^boolean isHungry [])
  (^boolean isWeak []))

(definterface IGame
  (^anbf.bot.IFrame frame [])
  (^anbf.bot.IPlayer player []))

(definterface IFrame
  ; TODO
  )
