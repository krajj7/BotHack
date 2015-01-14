(ns bothack.item
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [bothack.itemtype :refer :all]
            [bothack.delegator :refer :all]
            [bothack.itemid :refer :all]
            [bothack.util :refer :all]))

(def ^:private item-fields
  [:slot :qty :buc :grease :poison :erosion1 :erosion2 :proof :used :eaten
   :diluted :enchantment :name :generic :specific :recharges :charges :candles
   :lit-candelabrum :lit :laid :chained :quivered :offhand :offhand-wielded
   :wielded :worn :cost1 :cost2 :cost3])

(defn- erosion [s]
  (if (and s (some #(.contains s %) ["burnt" "rusty" "rotted" "corroded"]))
    (condp #(.contains %2 %1) s
      "very" 2
      "thoroughly" 3
      1)))

(def ^:private item-re #"^(?:([\w\#\$])\s[+-]\s)?\s*([Aa]n?|[Tt]he|\d+)?\s*(blessed|(?:un)?cursed|(?:un)?holy)?\s*(greased)?\s*(poisoned)?\s*((?:(?:very|thoroughly) )?(?:burnt|rusty))?\s*((?:(?:very|thoroughly) )?(?:rotted|corroded))?\s*(fixed|(?:fire|rust|corrode)proof)?\s*(partly used)?\s*(partly eaten)?\s*(diluted)?\s*([+-]\d+)?\s*(?:(?:pair|set) of)?\s*\b(.*?)\s*(?:called (.*?))?\s*(?:named (.*?))?\s*(?:\((\d+):(-?\d+)\))?\s*(?:\((no|[1-7]) candles?(, lit| attached)\))?\s*(\(lit\))?\s*(\(laid by you\))?\s*(\(chained to you\))?\s*(\(in quiver\))?\s*(\(altern.*?\)?)?\s*(\(wielded i.*?\))?\s*(\((?:weapo?n?|wield?e?d?).*?\)?)?\s*(\((?:bei?n?g?|emb?e?d?d?e?d?|on?).*?\)?)?\s*(?:\(unpaid, (\d+) zorkmids?\)|\((\d+) zorkmids?\)|, no charge(?:, .*)?|, (?:price )?(\d+) zorkmids( each)?(?:, .*)?)?\.?\s*$")

(defn parse-label [label]
  (let [norm-label (string/replace label ; for uniques ("Lord Surtur's partly eaten corpse" => "partly eaten Lord Surtur's corpse"
                                   #"(?:the )?(.*) partly eaten corpse$"
                                   "partly eaten $1 corpse")
        raw (zipmap item-fields (re-first-groups item-re norm-label))]
    ;(log/debug raw)
    (as-> raw res
      (if-let [buc (re-first-group #"^potions? of ((?:un)?holy) water$"
                                   (:name res))]
        (assoc res
               :name "potion of water"
               :buc (if (= buc "holy") "blessed" "cursed"))
        res)
      (update res :name #(get jap->eng % %))
      (update res :name #(get plural->singular % %))
      (assoc res :lit (or (:lit res)
                          (and (:lit-candelabrum res)
                               (.contains (:lit-candelabrum res) "lit"))))
      (assoc res :in-use (first (keep res [:wielded :worn])))
      (assoc res :cost (first (keep res [:cost1 :cost2 :cost3])))
      (update res :qty #(if (and % (re-seq #"[0-9]+" %))
                          (parse-int %)
                          1))
      (if (:candles raw)
        (update res :candles #(if (= % "no") 0 (parse-int %)))
        res)
      (reduce #(update %1 %2 str->kw) res [:buc :proof])
      (if (or (and (:enchantment raw) (not (:buc res)))
              (and (:charges raw) (not (:buc res))))
        (assoc res :buc :uncursed)
        res)
      (reduce #(update %1 %2 parse-int) res
              (filter (comp seq res) [:cost :enchantment :charges :recharges]))
      (assoc res :erosion ((fnil max 0 0) (erosion (:erosion1 res))
                                          (erosion (:erosion2 res))))
      (dissoc res :cost1 :cost2 :cost3 :lit-candelabrum :erosion1 :erosion2 :slot)
      (into {:label label} (filter (comp some? val) res)))))

(defn safe-buc?
  "Known not to be cursed?"
  [item]
  (#{:uncursed :blessed} (:buc item)))

(defn cursed? [item]
  (= :cursed (:buc item)))

(defn uncursed? [item]
  (= :uncursed (:buc item)))

(defn noncursed? [item]
  (not= :cursed (:buc item)))

(defn blessed? [item]
  (= :blessed (:buc item)))

(defn single? [item]
  (= 1 (:qty item)))

(defn corpse? [item]
  (re-seq #" corpses?\b" (:name item)))

(defn corpse->monster [item]
  (:monster (name->item (:name item))))

(defn tin? [item]
  (re-seq #"\btins?\b" (:name item)))

(defn can-take? [item]
  (not (:cost item)))

(defn candle? [item]
  (.contains (:name item) "candle"))

(defmacro ^:private def-itemtype-pred [kw]
  `(defn ~(symbol (str (subs (str kw) 1) \?)) [~'item]
     (= ~kw (item-type ~'item))))

#_(pprint (macroexpand-1 '(def-itemtype-pred :food)))

(defmacro ^:private def-itemtype-preds
  "defines item type predicates: food? armor? tool? etc."
  []
  `(do ~@(for [kw (keys item-kinds)]
           `(def-itemtype-pred ~kw))))

(def-itemtype-preds)

(defn shield? [item]
  (= :shield (item-subtype item)))

(defn gloves? [item]
  (= :gloves (item-subtype item)))

(defn container? [item]
  (some? (re-seq #"^bag\b|sack$|chest$|box$" (:name item))))

(defn know-contents? [item]
  (or (not (container? item))
      (= "bag of tricks" (:name item))
      (some? (:items item))))

(defn boh? [game item]
  (= "bag of holding" (item-name game item)))

(defn water? [item]
  (= "potion of water" (:name item)))

(defn holy-water? [item]
  (and (water? item) (= :blessed (:buc item))))

(def explorable-container? (every-pred (complement know-contents?)
                                       (complement :locked)
                                       (some-fn noncursed?
                                                (complement boh?))))

(def candelabrum "Candelabrum of Invocation")
(def bell "Bell of Opening")
(def book "Book of the Dead")

(defn dagger? [item]
  (.contains (:name item) "dagger"))

(def daggers (->> (:weapon item-kinds)
                  (filter dagger?)
                  (map :name)
                  set))

(defn ammo? [item]
  (or (.contains (:name item) "arrow")
      (.contains (:name item) "bolt")))

(defn dart? [item]
  (.contains (:name item) "dart"))

(def ammo (->> (:weapon item-kinds)
               (filter (some-fn ammo? dart?))
               (map :name)
               set))

(defn short-sword? [item]
  (.contains (:name item) "short sword"))

(defn rocks? [item]
  (= "rock" (:name item)))

(defn egg? [item]
  (re-seq #"\begg\b" (:name item)))

(defn artifact? [item]
  (:artifact (or (name->item (:specific item))
                 (name->item (:name item)))))

(defn nw-ratio
  "Nutrition/weight ratio of item"
  [item]
  (let [id (item-id item)]
    (if (not= :food (typekw id))
      0
      ((fnil / 0) (:nutrition id) (:weight id)))))

(defn gold? [item]
  (= "gold piece" (:name item)))

(defn key? [item]
  (.contains (:name item) "key"))

(defn price-id?
  ([game item]
   (and (price-id? item)
        (not (know-price? game item))))
  ([item]
   (and (not (artifact? item))
        (not (container? item))
        (knowable-appearance? (appearance-of item))
        ((some-fn tool? ring? scroll? wand? potion? armor?) item))))

(defn wished? [item]
  (= "wish" (:specific item)))

(defn safe? [game item]
  (or (weapon? item)
      (tool? item)
      (and (or (safe-buc? item) (:in-use item) (wished? item))
           (:safe (item-id game item)))))

(defn shops-taking [item]
  (-> (case (item-type item)
        :armor #{:armor :weapon}
        :weapon #{:armor :weapon}
        :scroll #{:book}
        :spellbook #{:book}
        :amulet #{:gem}
        :ring #{:gem}
        #{(item-type item)})
      (conj :general)))

(defn enchantment [item]
  (or (:enchantment item) 0))

(defn charged? [item]
  (and (not= "empty" (:specific item))
       (not ((fnil zero? 1) (:charges item)))))

(defn recharged? [item]
  (or (= "recharged" (:specific item))
      (not ((fnil zero? 1) (:recharges item)))))

(defn pick? [item]
  (#{"pick-axe" "dwarvish mattock"} (:name item)))

(defn safe-enchant? [item]
  (case (item-type item)
    :weapon (> 6 (enchantment item))
    :armor (> 4 (enchantment item))
    nil))

(defn two-handed? [item]
  (= 2 (:hands (item-id item))))

(defrecord Item
  [label
   name
   generic ; called
   specific ; named
   qty
   buc ; nil :uncursed :cursed :blessed
   erosion ; 0-3
   proof ; :fixed :rustproof :fireproof ...
   enchantment ; nil or number
   charges
   recharges
   in-use ; worn / wielded
   cost] ; not to be confused with ItemType price
  bothack.bot.items.IItem
  (quantity [item] (:qty item))
  (label [item] (:label item))
  (name [item] (:name item))
  (buc [item] (kw->enum bothack.bot.items.BUC (:buc item)))
  (erosion [item] (:erosion item))
  (isFixed [item] (boolean (:proof item)))
  (enchantment [item] (:enchantment item))
  (charges [item] (:charges item))
  (recharges [item] (:recharges item))
  (isRecharged [item] (boolean (recharged? item)))
  (isCharged [item] (boolean (charged? item)))
  (isTwohanded [item] (boolean (two-handed? item)))
  (isSafeToEnchant [item] (boolean (safe-enchant? item)))
  (isArtifact [item] (boolean (artifact? item)))
  (type [item] (item-id item))
  (possibilities [item] (initial-ids item))
  (cost [item] (:cost item))
  (isInUse [item] (boolean (:in-use item)))
  (isWorn [item] (boolean (:worn item)))
  (isContainer [item] (container? item))
  (knowContents [item] (know-contents? item))
  (contents [item] (:items item))
  (isCorpse [item] (boolean (corpse? item)))
  (isWielded [item] (boolean (:wielded item))))

(defn label->item [label]
  (map->Item (parse-label label)))

(defn slot-item
  "Turns a string 'h - an octagonal amulet (being worn)' or [char String] pair into a [char Item] pair"
  ([s]
   (if-let [[slot label] (re-first-groups #"\s*(.)  ?[-+#] (.*)\s*$" s)]
     (slot-item (.charAt slot 0) label)))
  ([chr label]
   [chr (label->item label)]))
