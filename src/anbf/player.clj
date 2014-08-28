(ns anbf.player
  "representation of the bot avatar"
  (:require [anbf.util :refer [kw->enum]]
            [anbf.dungeon :refer :all]
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
   inventory ; {char => Item}
   hunger ; :fainting :weak :hungry :satiated
   burden ; :overloaded :overtaxed :strained :stressed :burdened
   engulfed
   trapped
   leg-hurt
   state ; subset #{:stun :conf :hallu :blind :ill}
   stats ; :str :dex :con :int :wis :cha
   alignment] ; :lawful :neutral :chaotic
  anbf.bot.IPlayer
  ; TODO expose stats etc.
  (alignment [this] (kw->enum anbf.bot.Alignment (:alignment this)))
  (hunger [this] (kw->enum anbf.bot.Hunger (:hunger this)))
  (isHungry [this] (boolean (hungry? this)))
  (isWeak [this] (boolean (weak? this))))

(defn new-player []
  (map->Player {}))

(defn update-player [player status]
  (->> (keys player) (select-keys status) (into player)))

(defn blind? [player]
  (:blind (:state player)))

(defn impaired? [player]
  (some (:state player) #{:conf :stun :hallu :blind}))

(defn dizzy? [player]
  (some (:state player) #{:conf :stun}))

(defn thick?
  "True if the player can't pass through narrow diagonals."
  [player]
  (not (:thick player)))

(defn light-radius [game]
  1) ; TODO check for lit lamp/candelabrum/sunsword/burning oil
