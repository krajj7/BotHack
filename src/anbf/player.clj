; representation of the bot avatar

(ns anbf.player
  (:require [anbf.util :refer [enum]]
            [anbf.delegator :refer :all]
            [clojure.tools.logging :as log]))

(defn hungry?
  "Return true if hunger state is Hungry or worse"
  [{:keys [hunger]}]
  (not-any? #(= hunger %) [:normal :satiated]))

(defn weak?
  "Return true if hunger state is Weak or worse"
  [{:keys [hunger]}]
  (some #(= hunger %) [:weak :fainting]))

(defrecord Player
  [nickname
   hp
   maxhp
   pw
   maxpw
   ac
   xp
   xplvl
   position
   inventory
   hunger ; :fainting :weak :hungry :normal :satiated
   burden
   state ; stun/conf/hallu/blind/...
   stats ; :str :dex :con :int :wis :cha
   alignment] ; :lawful :neutral :chaotic
  anbf.bot.IPlayer
  ; TODO expose stats etc.
  (alignment [this] (enum anbf.bot.Alignment (:alignment this)))
  (hunger [this] (enum anbf.bot.Hunger (:hunger this)))
  (isHungry [this] (boolean (hungry? this)))
  (isWeak [this] (boolean (weak? this))))

(defn new-player []
  (apply ->Player (repeat 15 nil)))

(defn light-radius [player]
  1) ; TODO check for lit lamp/candelabrum/sunsword/burning oil
