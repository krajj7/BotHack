(ns bothack.bot
  "interfaces for bots")

; note: delegator.clj, actions.clj and java code generate additional classes in the bothack.bot package

(definterface IBotHack
  (^bothack.bot.IBotHack registerHandler [handler])
  (^bothack.bot.IBotHack registerHandler [^int priority handler])
  (^bothack.bot.IBotHack deregisterHandler [handler])
  (^bothack.bot.IBotHack replaceHandler [handler-old handler-new])
  (^bothack.bot.IGame game [])
  (^bothack.bot.IPlayer player [])
  (^bothack.bot.IBotHack write [^String text]))

(definterface IAction
  (handler [^bothack.bot.IBotHack bh])
  (^String trigger []))

(definterface IPlayer
  (^bothack.bot.IPosition position [])
  (^bothack.bot.Alignment alignment [])
  (^bothack.bot.Hunger hunger [])
  (^boolean isHungry [])
  (^boolean isWeak []))

(definterface IGame
  (^bothack.bot.IFrame frame [])
  (^bothack.bot.IPlayer player []))

(definterface IPosition
  (^int x [])
  (^int y []))

(definterface IFrame ; TODO
  )

(definterface IDungeon ; TODO
  )

(definterface ILevel ; TODO
  )

(definterface ITile ; TODO
  )

(definterface IMonster ; TODO
  )
