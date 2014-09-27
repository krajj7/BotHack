(ns anbf.item
  (:require [clojure.tools.logging :as log]
            [anbf.itemtype :refer :all]
            [anbf.util :refer :all]))

(defrecord Key [])

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

(def jap->eng
  {"wakizashi" "short sword"
   "ninja-to" "broadsword"
   "nunchaku" "flail"
   "naginata" "glaive"
   "osaku" "lock pick"
   "koto" "wooden harp"
   "shito" "knife"
   "tanko" "plate mail"
   "kabuto" "helmet"
   "yugake" "leather gloves"
   "gunyoki" "food ration"
   "potion of sake" "potion of booze"
   "potions of sake" "potions of booze"})

(defn- erosion [s]
  (if (and s (some #(.contains s %) ["burnt" "rusty" "rotted" "corroded"]))
    (condp #(.contains %2 %1) s
      "very" 2
      "thoroughly" 3
      1)))

(def ^:private item-re #"^(?:([\w\#\$])\s[+-]\s)?\s*([Aa]n?|[Tt]he|\d+)?\s*(blessed|(?:un)?cursed|(?:un)?holy)?\s*(greased)?\s*(poisoned)?\s*((?:(?:very|thoroughly) )?(?:burnt|rusty))?\s*((?:(?:very|thoroughly) )?(?:rotted|corroded))?\s*(fixed|(?:fire|rust|corrode)proof)?\s*(partly used)?\s*(partly eaten)?\s*(diluted)?\s*([+-]\d+)?\s*(?:(?:pair|set) of)?\s*\b(.*?)\s*(?:called (.*?))?\s*(?:named (.*?))?\s*(?:\((\d+):(-?\d+)\))?\s*(?:\((no|[1-7]) candles?(, lit| attached)\))?\s*(\(lit\))?\s*(\(laid by you\))?\s*(\(chained to you\))?\s*(\(in quiver\))?\s*(\(alternate weapon; not wielded\))?\s*(\(wielded in other.*?\))?\s*(\((?:weapon|wielded).*?\))?\s*(\((?:being|embedded|on).*?\))?\s*(?:\(unpaid, (\d+) zorkmids?\)|\((\d+) zorkmids?\)|, no charge(?:, .*)?|, (?:price )?(\d+) zorkmids( each)?(?:, .*)?)?\.?\s*$")

(defn parse-label [label]
  (let [raw (zipmap item-fields (re-first-groups item-re label))]
    ;(log/debug raw)
    (as-> raw res
      (if-let [buc (re-seq #"^potions? of ((?:un)?holy) water$" (:name res))]
        (assoc res
               :name "potion of water"
               :buc (if (= buc "holy") "blessed" "cursed"))
        res)
      (update-in res [:name] #(get jap->eng % %))
      (update-in res [:name] #(get plural->singular % %))
      (assoc res :lit (some? ((some-fn :lit :lit-candelabrum) res)))
      (assoc res :in-use (find-first some? (map res [:wielded :worn])))
      (assoc res :cost (find-first some? (map res [:cost1 :cost2 :cost3])))
      (update-in res [:qty] #(if (re-seq #"[0-9]+" %)
                               (parse-int %)
                               1))
      (if (:candles raw)
        (update-in res [:candles] #(if (= % "no") 0 (parse-int %)))
        res)
      (reduce #(update-in %1 [%2] keyword) res [:buc :proof])
      (reduce #(update-in %1 [%2] parse-int)
              res
              (for [kw [:cost :enchantment :charges :recharges]
                    :when (seq (kw res))]
                kw))
      (assoc res :erosion (if-let [deg ((fnil + 0 0)
                                        (erosion (:erosion1 res))
                                        (erosion (:erosion2 res)))]
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
