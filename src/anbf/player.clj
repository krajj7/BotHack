(ns anbf.player
  "representation of the bot avatar"
  (:require [anbf.util :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.delegator :refer :all]
            [anbf.itemid :refer :all]
            [anbf.itemtype :refer :all]
            [anbf.item :refer :all]
            [clojure.tools.logging :as log]))

(defn hungry?
  "Returns hunger state if it is Hungry or worse, else nil"
  [player]
  (#{:hungry :weak :fainting} (:hunger player)))

(defn weak?
  "Returns hunger state if it is Weak or worse, else nil"
  [player]
  (#{:weak :fainting} (:hunger player)))

(defn fainting?  [player]
  (= :fainting (:hunger player)))

(defn satiated? [player]
  (= :satiated (:hunger player)))

(defrecord Player
  [nickname
   role
   race
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
   intrinsics ; set of resistances, telepathy etc.
   engulfed
   trapped
   leg-hurt
   state ; subset #{:stun :conf :hallu :blind :ill}
   stat-drained
   lycantrophy
   stoning
   stats ; :str :dex :con :int :wis :cha
   alignment ; :lawful :neutral :chaotic
   can-enhance]
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

(defn hallu? [player]
  (:hallu (:state player)))

(defn dizzy? [player]
  (some (:state player) #{:conf :stun}))

(defn confused? [player]
  (:conf (:state player)))

(defn thick?
  "True if the player can't pass through narrow diagonals."
  [player]
  (not (:thick player)))

(defn light-radius [game]
  1) ; TODO check for lit lamp/candelabrum/sunsword/burning oil

(defn inventory [game]
  (-> game :player :inventory))

(defn inventory-slot
  "Return item for the inventory slot"
  [game slot]
  (get-in game [:player :inventory slot]))

(defn- base-selector [game name-or-set-or-fn]
  (cond ((some-fn keyword? fn?) name-or-set-or-fn) name-or-set-or-fn
        (set? name-or-set-or-fn) (some-fn (comp name-or-set-or-fn
                                                (partial item-name game))
                                          (comp name-or-set-or-fn :name))
        :else (some-fn (comp (partial = name-or-set-or-fn) :name)
                       (comp (partial = name-or-set-or-fn)
                             (partial item-name game)))))

(defn- have-selector [game name-or-set-or-fn opts]
  (apply every-pred (base-selector game name-or-set-or-fn)
         (remove nil? [(if (:safe opts) safe?)
                       (if (:noncursed opts) noncursed?)
                       (if (:blessed opts) blessed?)
                       (if (:nonblessed opts) (complement blessed?))
                       (if (:know-buc opts) (comp some? :buc))
                       (if (false? (:in-use opts)) (complement :in-use))
                       (if (:in-use opts) :in-use)])))

(defn have-all
  "Returns a lazy seq of all matching [slot item] pairs in inventory, options same as 'have'"
  ([game name-or-set-or-fn]
   (have-all game name-or-set-or-fn {}))
  ([game name-or-set-or-fn opts]
   (let [selector (have-selector game name-or-set-or-fn opts)]
     (concat (filter (comp selector val)
                     (inventory game))
             (if (:bagged opts)
               (for [[slot bag :as entry] (inventory game)
                     :when (container? bag)
                     :let [matches (filter selector (:items bag))]
                     match matches]
                 [slot match]))))))

(defn have
  "Returns the [slot item] of matching item in player's inventory or nil.
   First arg can be:
     String (name of item)
     #{String} (set of strings - item name alternatives with no preference)
     fn - predicate function to filter items (gets the Item as arg, not the name)
   Options map can contain:
     :safe - return only known-non-cursed items
     :noncursed - return only items not known to be cursed
     :nonblessed - return only items not known to be blessed
     :blessed - return only blessed items
     :know-buc - only items with known buc
     :in-use - if false only non-used items, if true only used (worn/wielded)
     :bagged - return slot of bag containing the item if it is not present in main inventory"
  ([game name-or-set-or-fn]
   (have game name-or-set-or-fn {}))
  ([game name-or-set-or-fn opts]
   (first (have-all game name-or-set-or-fn opts))))

(defn have-blessed
  "Like 'have' but return only blessed items"
  [game name-or-set-or-fn]
  (have game (every-pred blessed?
                         (have-selector game name-or-set-or-fn))))

(defn have-unihorn [game]
  (have game "unicorn horn" {:noncursed true}))

(defn have-pick [game]
  ; TODO (can-wield? ...)
  (have game #(and (#{"pick-axe" "dwarvish mattock"} (item-name game %))
                   (or (not (cursed? %)) (:in-use %)))))

(defn have-key [game]
  (have game #{"skeleton key" "lock pick" "credit card"}))

(defn have-levi-on [game]
  (have game #{"boots of levitation" "ring of levitation"} {:in-use true}))

(defn have-levi [game]
  (have game #(and (#{"boots of levitation" "ring of levitation"}
                             (item-name game %))
                   #_(can-use? %) ; TODO
                   (or (noncursed? %) (:in-use %)))))

(defn unihorn-recoverable? [{:keys [player] :as game}]
  (or (:stat-drained player)
      (some (:state player) #{:conf :stun :hallu :ill})
      (and (:blind (:state player))
           (not (have game #(and (#{"towel" "blindfold"} (item-name game %))
                                 (:in-use %)))))))

(defn can-remove? [game item]
  (not (cursed? item))) ; TODO not obstructed by cursed item / weapon

(defn wielding
  "Return the wielded [slot item] or nil"
  [game]
  (have game :wielded))

(defn initial-intrinsics [race-or-role]
  (case race-or-role
    :valkyrie #{:cold :stealth}
    :orc #{:poison}
    ; TODO rest
    #{}))

(defn add-intrinsic [game intrinsic]
  (log/debug "adding intrinsic:" intrinsic)
  (update-in game [:player :intrinsics] conj intrinsic))

(defn remove-intrinsic [game intrinsic]
  (log/debug "removing intrinsic:" intrinsic)
  (update-in game [:player :intrinsics] disj intrinsic))

(defn have-intrinsic? [player resist]
  (resist (:intrinsics player)))

(defn fast? [player]
  (have-intrinsic? player :speed))

(defn count-candles [game]
  (reduce +
          (if-let [[_ candelabrum] (have game "Candelabrum of Invocation")]
            (:candles candelabrum))
          (for [[_ candles] (have-all game candle?)]
            (:qty candles))))

(defn have-candles? [game]
  (= 7 (count-candles game)))

(def taboo-corpses #{"chickatrice" "cockatrice" "green slime" "stalker" "quantum mechanic" "elf" "human" "dwarf" "giant"})

(defn- safe-corpse-type? [player corpse {:keys [monster] :as corpse-type}]
  (and (or (tin? corpse)
           (have-intrinsic? player :poison)
           (not (:poisonous corpse-type)))
       (not ((:race player) (:tags monster)))
       (not (or (taboo-corpses (:name monster))
                ((some-fn :were :teleport :domestic) (:tags monster))
                (re-seq #" bat$" (:name monster))))))

(defn can-eat?
  "Only true for safe food or unknown tins"
  [player {:keys [name] :as food}]
  (and (not (cursed? food))
       (can-take? food)
       (or (tin? food)
           (if-let [itemtype (name->item name)]
             (and (food? itemtype)
                  (or (= :orc (:race player))
                      (not= "tripe ration" name))
                  (or (not (:monster itemtype))
                      (safe-corpse-type? player food itemtype)))))))

(defn want-to-eat? [player corpse]
  (and (can-eat? player corpse)
       (let [{:keys [monster] :as corpse-type} (name->item (:name corpse))
             strength (get-in player [:stats :str])]
         (or (= "wraith" (:name monster))
             (= "newt" (:name monster))
             (and (or (not= "18/**" strength)
                      (some-> (parse-int strength) (< 18)))
                  (:str (:tags monster)))
             (some (complement (partial have-intrinsic? player))
                   (:resistances-conferred monster))))))

(defn update-slot
  "Apply update-fn to item at the inventory slot"
  [game slot update-fn & args]
  (apply update-in game [:player :inventory slot] update-fn args))
