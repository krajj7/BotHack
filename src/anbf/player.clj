; representation of the bot avatar

(ns anbf.player
  (:require [anbf.util :refer [kw->enum]]
            [anbf.delegator :refer :all]
            [clojure.tools.logging :as log]))

(defn hungry?
  "Returns hunger state if it is Hungry or worse, else nil"
  [player]
  (#{:hungry :weak :fainting} (:hunger player)))

(defn weak?
  "Returns hunger state if it is Weak or worse, else nil"
  [player]
  (#{:weak :fainting} (:hunger player)))

(defrecord Player
  [nickname
   hp
   maxhp
   pw
   maxpw
   ac
   xp
   xplvl
   x y
   inventory
   hunger ; :fainting :weak :hungry :normal :satiated
   burden
   state ; stun/conf/hallu/blind/...
   stats ; :str :dex :con :int :wis :cha
   alignment] ; :lawful :neutral :chaotic
  anbf.bot.IPlayer
  ; TODO expose stats etc.
  (alignment [this] (kw->enum anbf.bot.Alignment (:alignment this)))
  (hunger [this] (kw->enum anbf.bot.Hunger (:hunger this)))
  (isHungry [this] (boolean (hungry? this)))
  (isWeak [this] (boolean (weak? this))))

(defn new-player []
  (apply ->Player (repeat 16 nil)))

(defn update-player [player status delegator]
  ; TODO events
  (->> player keys (select-keys status) (merge player)))

(defn light-radius [player]
  1) ; TODO check for lit lamp/candelabrum/sunsword/burning oil
