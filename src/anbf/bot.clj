; interfaces for bots

(ns anbf.bot)

(definterface IANBF
  (^anbf.bot.IANBF registerHandler [handler] "Register a user handler implementing command/event protocols it is interested in to the delegator" )
  (^anbf.bot.IANBF deregisterHandler [handler] "Deregister the given handler from the delegator" )
  (^anbf.bot.IANBF replaceHandler [handler] "Deregister a handler and register a different user handler" )
  (^anbf.bot.IGame game [])
  (^anbf.bot.IANBF rawWrite [^String text]))

(definterface IPlayer
  (^boolean isHungry [])
  (^boolean isWeak []))

(definterface IGame
  (frame [])
  (^anbf.bot.IPlayer player []))

(definterface IFrame
  ; TODO
  )
