(ns anbf.player
  "representation of the bot avatar"
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [anbf.util :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.delegator :refer :all]
            [anbf.montype :refer :all]
            [anbf.itemid :refer :all]
            [anbf.itemtype :refer :all]
            [anbf.item :refer :all]))

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
   title
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
   state ; subset #{:stun :conf :hallu :blind :ill :ext-blind}
   stat-drained
   polymorphed
   lycantrophy
   stoning
   stats ; :dex :con :int :wis :cha :str (effective integer str) :str* (string like "18/**")
   alignment ; :lawful :neutral :chaotic
   protection
   can-enhance]
  anbf.bot.IPlayer
  ; TODO expose stats etc.
  (alignment [this] (kw->enum anbf.bot.Alignment (:alignment this)))
  (hunger [this] (kw->enum anbf.bot.Hunger (:hunger this)))
  (isHungry [this] (boolean (hungry? this)))
  (isWeak [this] (boolean (weak? this))))

(defn new-player []
  (map->Player {:protection 0
                :inventory {}}))

(defn update-player [player status]
  (cond-> (->> (keys player) (select-keys status) (into player))
    (not (:blind status)) (update :state disj :ext-blind)
    (= "HD" (:xp-label status)) (assoc :polymorphed (some->> (:title status)
                                                             string/lower-case
                                                             name->monster))
    (= "Exp" (:xp-label status)) (assoc :polymorphed nil)
    (zero? (:gold status)) (update :inventory dissoc \$)
    (pos? (:gold status)) (assoc-in [:inventory \$]
                                    (assoc (label->item "uncursed gold piece")
                                           :qty (:gold status)))))


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

(defn has-hands? [player]
  ; TODO two handed weapon with shield ...
  (not (get-in player [:polymorphed :tags :nohands])))

(defn light-radius [game]
  1) ; TODO check for lit lamp/candelabrum/sunsword/burning oil

(declare inventory)

(defn bagged-items [game]
  (for [[slot bag] (inventory game)
        :when (container? bag)
        item (:items bag)]
    [slot item]))

(defn inventory
  ([game bagged?]
   (concat (-> game :player :inventory)
           (if bagged? (bagged-items game))))
  ([game]
   (inventory game false)))

(defn- base-selector [game name-or-set-or-fn]
  (cond ((some-fn keyword? fn?) name-or-set-or-fn) name-or-set-or-fn
        (set? name-or-set-or-fn) (some-fn (comp name-or-set-or-fn
                                                (partial item-name game))
                                          (comp name-or-set-or-fn :name))
        :else (some-fn (comp (partial = name-or-set-or-fn) :name)
                       (comp (partial = name-or-set-or-fn)
                             (partial item-name game)))))

(def slots
  [:helmet :cloak :suit :shirt :shield :gloves :boots :accessory :amulet])

(def blocker-slots
  (reduce #(update %1 %2 conj %2) {:suit [:cloak]
                                   :shirt [:cloak :suit]} slots))

(declare have have-all)

(defn inventory-slot
  "Return item for the inventory slot (letter or slot keyword)"
  [game slot]
  {:pre [(or (char? slot) (slots slot)) (:player game)]}
  (if (char? slot)
    (get-in game [:player :inventory slot])
    (have game (every-pred :worn
                           (comp (partial = slot) item-subtype)))))

(defn wielding
  "Return the wielded [slot item] or nil"
  [game]
  (have game :wielded))

(defn free-finger?
  "Does the player have a free ring-finger?"
  [player]
  {:pre [(:inventory player)]}
  (less-than? 2 (filter (every-pred ring? :in-use)
                        (vals (:inventory player)))))

(defn blockers
  "Return a seq of [slot item] of armor that needs to be removed before armor item can be worn (in possible order of removal)"
  [{:keys [player] :as game} item]
  (or (if-let [subtype (item-subtype item)]
        (for [btype (blocker-slots subtype)
              :let [blocker (have game (every-pred :worn (comp (partial = btype)
                                                               item-subtype)))]
              :when blocker]
          blocker))
      (if (weapon? item) ; TODO consider cursed two-hander...
        [(wielding game)])
      (if (ring? item)
        (concat (have-all game #(= :gloves (item-subtype %)) #{:in-use :cursed})
                (if-not (free-finger? player)
                  (have-all game ring? #{:in-use}))))
      (if (amulet? item)
        [(have game amulet? #{:in-use})])))

(defn cursed-blockers [game slot]
  (if-let [[[blocker-slot blocker] & _ :as blockers]
           (blockers game (inventory-slot game slot))]
    (->> (map secondv blockers)
         (filter cursed?) seq)))

(defn- have-selector [game name-or-set-or-fn opts]
  (apply every-pred (base-selector game name-or-set-or-fn)
         (remove nil? [(if (:nonempty opts) (comp (partial not= "empty")
                                                  :specific))
                       (if (:safe-buc opts) safe-buc?)
                       (if (:unsafe-buc opts) (complement safe-buc?))
                       (if (false? (:safe-buc opts)) (complement safe-buc?))
                       (if (:noncursed opts) noncursed?)
                       (if (:buc opts) (comp (partial = (:buc opts)) :buc))
                       (if (:nonblessed opts) (complement blessed?))
                       (if (:blessed opts) blessed?)
                       (if (:cursed opts) cursed?)
                       (if (:wished opts) wished?)
                       (if (:know-buc opts) (comp some? :buc))
                       (if (false? (:know-buc opts)) (comp nil? :buc))
                       (if (false? (:in-use opts)) (complement :in-use))
                       (if (:in-use opts) :in-use)])))

(defn have-all
  "Returns a lazy seq of all matching [slot item] pairs in inventory, options same as 'have'"
  ([game name-or-set-or-fn]
   (have-all game name-or-set-or-fn {}))
  ([{:keys [player] :as game} name-or-set-or-fn opts]
   {:pre [((some-fn ifn? string?) name-or-set-or-fn)
          ((some-fn map? set?) opts)]}
   (let [opts (if (set? opts) (zipmap opts (repeat true)) opts)
         selector (have-selector game name-or-set-or-fn opts)]
     (concat (for [[slot item] (inventory game)
                   :when (and (selector item)
                              (not (and (:can-use opts)
                                        (and (or (armor? item) (weapon? item))
                                             (not (has-hands? player)))
                                        (cursed-blockers game slot)
                                        (not (:in-use item))))
                              (not (and (:can-remove opts)
                                        (:in-use item)
                                        (cursed-blockers game slot))))]
               [slot item])
             (if (:bagged opts)
               (for [[slot bag] (inventory game)
                     :when (container? bag)
                     :let [matches (filter selector (:items bag))]
                     match matches]
                 [slot match]))))))

(defn have-sum
  "Returns sum of the quantities of matching items"
  ([game name-or-set-or-fn] (have-sum game name-or-set-or-fn {}))
  ([game name-or-set-or-fn opts]
   (reduce (fn [res [_ item]] (+ res (:qty item))) 0
           (have-all game name-or-set-or-fn opts))))

(defn have
  "Returns the [slot item] of matching item in player's inventory or nil.
   First arg can be:
     String (name of item)
     #{String} (set of strings - item name alternatives with no preference)
     fn - predicate function to filter items (gets the Item as arg, not the name)
   Options map (or set if all map vals would be true) can contain:
     :noncursed - return only items not known to be cursed
     :nonblessed - return only items not known to be blessed
     :blessed - only blessed
     :cursed - only cursed
     :wished - only wished-for items
     :buc <:cursed/:uncursed/:blessed> - return only items known to have given buc
     :know-buc - if true items with any (known) buc, if false items with unknown buc
     :safe-buc - same as :know-buc + :noncursed
     :unsafe-buc - complement of :safe-buc
     :in-use - if false only non-used items, if true only used (worn/wielded)
     :bagged - return slot of bag containing the item if it is not present in main inventory
     :can-remove - returns only items that are unused or not blocked by anything cursed
     :can-use - returns only items that are already in use or not blocked by anything cursed
     :nonempty - items not named empty"
  ([game map-or-name-or-set-or-fn]
   (if (map? map-or-name-or-set-or-fn)
     (have game (constantly true) map-or-name-or-set-or-fn)
     (have game map-or-name-or-set-or-fn {})))
  ([game name-or-set-or-fn opts]
   (first (have-all game name-or-set-or-fn opts))))

(defn have-usable [game smth]
  (have game smth #{:can-use}))

(defn have-unihorn [game]
  (have game "unicorn horn" #{:noncursed}))

(defn have-pick [game]
  (have-usable game #(and (#{"pick-axe" "dwarvish mattock"} (item-name game %))
                          (or (not (cursed? %)) (:in-use %)))))

(defn have-key [game]
  (have game #{"skeleton key" "lock pick" "credit card"}))

(defn have-levi-on [game]
  (have game #{"boots of levitation" "ring of levitation"} #{:in-use}))

(defn have-levi [game]
  (have game #(and (#{"boots of levitation" "ring of levitation"}
                             (item-name game %))
                   (or (noncursed? %) (:in-use %)))))

(defn reflection? [game]
  (have game #{"amulet of reflection" "shield of reflection"
               "silver dragon scale mail"} #{:in-use}))

(defn free-action? [game]
  (have game "ring of free action" #{:in-use}))

(defn unihorn-recoverable? [{:keys [player] :as game}]
  (or (:stat-drained player)
      (some (:state player) #{:conf :stun :hallu :ill})
      (and (:blind (:state player))
           (not (:ext-blind (:state player)))
           (not (have game #(and (#{"towel" "blindfold"} (item-name game %))
                                 (:in-use %)))))))

(defn can-remove? [game slot]
  (let [item (inventory-slot game slot)]
    (or (not (:in-use item))
        (and (empty? (cursed-blockers game slot))
             (noncursed? item)))))

(defn initial-intrinsics [race-or-role]
  (case race-or-role
    :valkyrie #{:cold :stealth}
    :orc #{:poison}
    ; TODO rest
    #{}))

(defn add-intrinsic [game intrinsic]
  {:pre [(:player game)]}
  (log/debug "adding intrinsic:" intrinsic)
  (update-in game [:player :intrinsics] conj intrinsic))

(defn remove-intrinsic [game intrinsic]
  {:pre [(:player game)]}
  (log/debug "removing intrinsic:" intrinsic)
  (update-in game [:player :intrinsics] disj intrinsic))

(defn have-intrinsic? [{:keys [player] :as game-or-player} resist]
  (resist (:intrinsics (or player game-or-player))))

(defn fast? [player]
  {:pre [(:intrinsics player)]}
  (have-intrinsic? player :speed))

(defn count-candles [game]
  {:pre [(:player game)]}
  (reduce +
          (if-let [[_ candelabrum] (have game "Candelabrum of Invocation")]
            (:candles candelabrum))
          (for [[_ candles] (have-all game candle?)]
            (:qty candles))))

(defn have-candles? [game]
  {:pre [(:player game)]}
  (= 7 (count-candles game)))

(def taboo-corpses #{"chickatrice" "cockatrice" "green slime" "stalker" "quantum mechanic" "elf" "human" "dwarf" "giant" "violet fungus" "yellow mold" "chameleon"})

(defn safe-corpse-type? [player corpse {:keys [monster] :as corpse-type}]
  {:pre [(:intrinsics player)]}
  (and (or (tin? corpse)
           (have-intrinsic? player :poison)
           (not (:poisonous corpse-type)))
       (not ((:race player) (:tags monster)))
       (not (or (taboo-corpses (:name monster))
                ((some-fn :were :teleport :domestic) (:tags monster))
                (re-seq #"bat$" (:name monster))))))

(defn can-eat?
  "Only true for safe food or unknown tins"
  [player {:keys [name] :as food}]
  {:pre [(:intrinsics player)]}
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
  {:pre [(:intrinsics player)]}
  (and (can-eat? player corpse)
       (let [{:keys [monster] :as corpse-type} (name->item (:name corpse))
             strength (get-in player [:stats :str*])]
         (or (= #{"newt" "tengu" "wraith"} (:name monster))
             (and (or (not= "18/**" strength)
                      (some-> (parse-int strength) (< 18)))
                  (:str (:tags monster)))
             (some (complement (partial have-intrinsic? player))
                   (:resistances-conferred monster))))))

(defn update-slot
  "Apply update-fn to item at the inventory slot"
  [game slot update-fn & args]
  {:pre [(:player game)]}
  (apply update-in game [:player :inventory slot] update-fn args))

(defn nutrition-sum
  "Sum of nutrition of carried food"
  [game]
  {:pre [(:player game)]}
  (reduce (fn [res [_ item]]
            (+ res ((fnil * 0) (:nutrition (item-id game item)) (:qty item))))
          0
          (have-all game food? #{:bagged :noncursed})))

(defn nw-ratio-avg
  "Nutrition/weight ratio average for all carried food"
  [game]
  {:pre [(:player game)]}
  (if-let [food (seq (have-all game food? #{:bagged :noncursed}))]
    (/ (nutrition-sum game)
       (reduce (fn [res [_ item]] (+ (:weight (item-id game item)) res)) 0
               food))))

(defn can-engrave?
  "Checks if the player is capable of engraving (also for non-engravable planes)"
  [{:keys [player] :as game}]
  {:pre [(:inventory player)]}
  (not (or (not (has-hands? player))
           (impaired? player)
           (#{:air :water} (branch-key game))
           (have-levi-on game))))

(defn weight-mod [game item]
  {:pre [(:player game)]}
  (if (and (:items item) (boh? game item))
    (case (:buc item)
      :blessed (comp inc (partial * 0.25))
      :cursed (partial * 2)
      (comp inc (partial * 0.5)))
    identity))

(defn weight-sum [game]
  {:pre [(:player game)]}
  (reduce + (for [[_ item] (inventory game)
                  :let [q (weight-mod game item)]
                  i (conj (:items item) item)
                  :let [w (item-weight i)]
                  :when w]
              (* (:qty i) (if (= i item) w (q w))))))

(defn capacity [{:keys [stats] :as player}]
  {:pre [stats]}
  (min 1000
       (+ 50 (* 25 (+ (:con stats)
                      (:str stats))))))

(defn weight-to-burden [game]
  {:pre [(:player game)]}
  (- (capacity (:player game)) (weight-sum game)))

(defn available-gold
  "Return the amount of gold the player has in main inventory"
  [game]
  {:pre [(:player game)]}
  (get-in game [:player :inventory \$ :qty]))

(defn gold
  "Return the amount of gold the player has including bagged gold"
  [game]
  {:pre [(:player game)]}
  (have-sum game gold? #{:bagged}))

(defn inventory-label
  "Returns the [slot item] of something in main inventory that matches the label or nil"
  [game label]
  {:pre [(:player game)]}
  (find-first #(or (.startsWith (:label (val %)) label)
                   (= (:name (val %)) label))
              (inventory game)))

(defn slot-appearance [game slot]
  (appearance-of (inventory-slot game slot)))

(defn have-mr? [game]
  (have game #{"gray dragon scale mail" "cloak of magic resistance"
               "Magicbane" "gray dragon scales"} #{:in-use}))

(defn overloaded? [player]
  (= :overloaded (:burden player)))
