; interfaces for bots

(ns anbf.bot)

(definterface IANBF
  (^anbf.bot.IANBF registerHandler [handler])
  (^anbf.bot.IANBF registerHandler [^int priority handler])
  (^anbf.bot.IANBF deregisterHandler [handler])
  (^anbf.bot.IANBF replaceHandler [handler-old handler-new])
  (^anbf.bot.IGame game [])
  (^anbf.bot.IPlayer player [])
  (^anbf.bot.IANBF write [^String text]))

(definterface IPlayer
  (^boolean isHungry [])
  (^boolean isWeak []))

(definterface IGame
  (^anbf.bot.IFrame frame [])
  (^anbf.bot.IPlayer player []))

(definterface IFrame
  ; TODO
  )
