(ns anbf.item
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [anbf.itemtype :refer :all]
            [anbf.itemid :refer :all]
            [anbf.util :refer :all]))

(defrecord Item
  [label
   name
   generic ; called
   specific ; named
   qty
   buc ; nil :uncursed :cursed :blessed
   erosion ; nil or 1-6
   proof ; :fixed :rustproof :fireproof ...
   enchantment ; nil or number
   charges
   recharges
   in-use ; worn / wielded
   cost]) ; not to be confused with ItemType price

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

(def ^:private item-re #"^(?:([\w\#\$])\s[+-]\s)?\s*([Aa]n?|[Tt]he|\d+)?\s*(blessed|(?:un)?cursed|(?:un)?holy)?\s*(greased)?\s*(poisoned)?\s*((?:(?:very|thoroughly) )?(?:burnt|rusty))?\s*((?:(?:very|thoroughly) )?(?:rotted|corroded))?\s*(fixed|(?:fire|rust|corrode)proof)?\s*(partly used)?\s*(partly eaten)?\s*(diluted)?\s*([+-]\d+)?\s*(?:(?:pair|set) of)?\s*\b(.*?)\s*(?:called (.*?))?\s*(?:named (.*?))?\s*(?:\((\d+):(-?\d+)\))?\s*(?:\((no|[1-7]) candles?(, lit| attached)\))?\s*(\(lit\))?\s*(\(laid by you\))?\s*(\(chained to you\))?\s*(\(in quiver\))?\s*(\(alternate weapon; not wielded\))?\s*(\(wielded in other.*?\))?\s*(\((?:weapon|wielded).*?\))?\s*(\((?:being|embedded|on).*?\))?\s*(?:\(unpaid, (\d+) zorkmids?\)|\((\d+) zorkmids?\)|, no charge(?:, .*)?|, (?:price )?(\d+) zorkmids( each)?(?:, .*)?)?\.?\s*$")

(defn parse-label [label]
  (let [norm-label (string/replace label ; for uniques ("Lord Surtur's partly eaten corpse" => "partly eaten Lord Surtur's corpse"
                                   #"(.*) partly eaten corpse$"
                                   "partly eaten $1 corpse")
        raw (zipmap item-fields (re-first-groups item-re norm-label))]
    ;(log/debug raw)
    (as-> raw res
      (if-let [buc (re-seq #"^potions? of ((?:un)?holy) water$" (:name res))]
        (assoc res
               :name "potion of water"
               :buc (if (= buc "holy") "blessed" "cursed"))
        res)
      (update res :name #(get jap->eng % %))
      (update res :name #(get plural->singular % %))
      (assoc res :lit (or (:lit res)
                          (and (:lit-candelabrum res)
                               (.contains (:lit-candelabrum res) "lit"))))
      (assoc res :in-use (find-first some? (map res [:wielded :worn])))
      (assoc res :cost (find-first some? (map res [:cost1 :cost2 :cost3])))
      (update res :qty #(if (and % (re-seq #"[0-9]+" %))
                          (parse-int %)
                          1))
      (if (:candles raw)
        (update res :candles #(if (= % "no") 0 (parse-int %)))
        res)
      (reduce #(update %1 %2 str->kw) res [:buc :proof])
      (reduce #(update %1 %2 parse-int) res
              (filter (comp seq res) [:cost :enchantment :charges :recharges]))
      (assoc res :erosion (if-let [deg (+ (or (erosion (:erosion1 res)) 0)
                                          (or (erosion (:erosion2 res)) 0))]
                            (if (pos? deg) deg)))
      (dissoc res :cost1 :cost2 :cost3 :lit-candelabrum :erosion1 :erosion2 :slot)
      (into {:label label} (filter (comp some? val) res)))))

(defn label->item [label]
  (map->Item (parse-label label)))

(defn slot-item
  "Turns a string 'h - an octagonal amulet (being worn)' or [char String] pair into a [char Item] pair"
  ([s]
   (if-let [[slot label] (re-first-groups #"\s*(.)  ?[-+#] (.*)\s*$" s)]
     (slot-item (.charAt slot 0) label)))
  ([chr label]
   [chr (label->item label)]))

(defn safe?
  "Known not to be cursed?"
  [item]
  (#{:uncursed :blessed} (:buc item)))

(defn cursed? [item]
  (= :cursed (:buc item)))

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
  (re-seq #" tins?\b" (:name item)))

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

(defn container? [item]
  (some? (re-seq #"^bag\b|sack$|chest$|box$" (:name item))))

(defn know-contents? [item]
  (or (not (container? item))
      (= "bag of tricks" (:name item))
      (some? (:items item))))

(defn boh? [game item]
  (= "bag of holding" (:name (item-id game item))))

(def explorable-container? (every-pred (complement know-contents?)
                                       (complement :locked)
                                       (some-fn noncursed?
                                                (complement boh?))))

(def candelabrum "Candelabrum of Invocation")
(def bell "Bell of Opening")
(def book "Book of the Dead")
