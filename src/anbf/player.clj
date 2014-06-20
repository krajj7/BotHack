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
   fov
   inventory
   hunger ; :fainting :weak :hungry :satiated
   burden
   engulfed
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
  (apply ->Player (repeat 18 nil)))

(defn update-player [player status]
  (->> (keys player) (select-keys status) (into player)))

(defn in-fov? [game pos]
  (get-in game [:player :fov (dec (:y pos)) (:x pos)]))

(defn blind? [player]
  (:blind (:state player)))

(defn visible? [game tile]
  "Only considers normal sight, not infravision/ESP/..."
  (and (not (blind? (:player game)))
       (in-fov? game tile)
       (lit? game tile)))

(defn impaired? [player]
  (some (:state player) #{:conf :stun :hallu :blind}))

(defn light-radius [player]
  1) ; TODO check for lit lamp/candelabrum/sunsword/burning oil
