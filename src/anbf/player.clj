(ns anbf.player
  "representation of the bot avatar"
  (:require [anbf.util :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.delegator :refer :all]
            [anbf.itemid :refer :all]
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
   lycantrophy
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

(defn inventory [game]
  (-> game :player :inventory))

(defn inventory-slot [game slot]
  (get-in game [:player :inventory slot]))

(defn have
  "Returns the [slot item] of matching item in player's inventory or nil.
   First arg can be:
     String (name of item)
     #{String} (set of strings - item name alternatives with no preference)
     fn - predicate function to filter items"
  [game itemname-or-set-or-fn]
  ;(log/debug "have")
  (find-first (cond (set? itemname-or-set-or-fn) (comp itemname-or-set-or-fn
                                                       (partial item-name game)
                                                       val)
                    (fn? itemname-or-set-or-fn) (comp itemname-or-set-or-fn val)
                    :else (comp (partial = itemname-or-set-or-fn)
                                (partial item-name game) val))
              (inventory game)))

(defn have-unihorn [game]
  (have game #(and (= "unicorn horn" (item-name game %))
                   (not= :cursed (:buc %)))))

(defn have-pick [game]
  (have game #(and (#{"pick-axe" "dwarvish mattock"} (item-name game %))
                   (or (not= :cursed (:buc %)) (:in-use %)))))

(defn unihorn-recoverable? [{:keys [player] :as game}]
  (or (some (:state player) #{:conf :stun :hallu :ill})
      (and (:blind (:state player))
           (not (have game #(and (#{"towel" "blindfold"} (item-name game %))
                                 (:in-use %)))))))

(defn can-remove? [game item]
  (not= :cursed (:buc item))) ; TODO not obstructed by cursed item / weapon

(defn wielding
  "Return the wielded item or nil"
  [game]
  (find-first #(:wielded (val %))
              (inventory game)))
