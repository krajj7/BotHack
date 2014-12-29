(ns bothack.bot
  "interfaces for bots")

; note: delegator.clj, actions.clj and java code generate additional classes in the bothack.bot package

(definterface IBotHack
  (^void registerHandler [handler])
  (^void registerHandler [^int priority handler])
  (^void deregisterHandler [handler])
  (^void replaceHandler [handler-old handler-new])
  (^void write [^String text])
  (^bothack.bot.IGame game [])
  (^bothack.bot.IPlayer player []))

(definterface IAction
  (handler [^bothack.bot.IBotHack bh])
  (^String trigger []))

(definterface IPlayer
  (^bothack.bot.IPosition position [])
  (^bothack.bot.Alignment alignment [])
  (^bothack.bot.Hunger hunger [])
  (^bothack.bot.Encumbrance encumbrance [])
  (^boolean isHungry [])
  (^boolean isOverloaded [])
  (^boolean isOvertaxed [])
  (^boolean isBurdened [])
  (^boolean isWeak []))

(definterface IGame
  (^bothack.bot.IFrame frame [])
  (^bothack.bot.IPlayer player [])
  (^boolean canPray []))

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
