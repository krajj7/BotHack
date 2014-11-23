(ns anbf.itemid
  "identifying items using some logic programming"
  (:refer-clojure :exclude [==])
  (:require [clojure.tools.logging :as log]
            [clojure.core.logic :refer :all]
            [clojure.core.logic.pldb :refer :all]
            [anbf.itemtype :refer :all]
            [anbf.itemdata :refer :all]
            [anbf.util :refer :all]))

(db-rel discovery ^:index appearance ^:index itemid)
(db-rel appearance-name ^:index appearance ^:index itemid)
(db-rel appearance-cha-cost ^:index appearance ^:index cha ^:index cost)
(db-rel appearance-prop-val ^:index appearance ^:index propname ^:index propval)
(db-rel base-cha-cost ^:index base ^:index cha ^:index cost)

(def observable-props #{:engrave :target :hardness})

(def item-names ; {lamp => [lamp1 lamp2], bag => [bag1 bag2 bag3 bag4], ...}
  (->> (for [{:keys [appearances]} items
                a appearances
                :when (not (exclusive-appearances a))
                :when (not= "Amulet of Yendor" a)
                :when (not= "egg" a)
                :when (not= "tin" a)]
            a)
       (reduce (fn add-name [res a]
                 (update res a #(->> % count inc (str a) (conj (or % [])))))
               {})
       (remove #(= 1 (count (val %))))
       (into {})))

(def names (into #{} (apply concat (vals item-names))))

(def ^:private cost-data
  (for [cost #_[60 300] (range 501)
        [cha charge] [[6 #(* 2 %)]
                      [7 #(+ % (quot % 2))]
                      [8 #(+ % (quot % 3))]
                      [15 identity]
                      [17 #(- % (quot % 4))]
                      [18 #(- % (quot % 3))]
                      [25 #(quot % 2)]
                      [0 identity]] ; sell price
        id-charge (if (zero? cha)
                    [#_#(quot % 3) ; ignore dunce cap case
                     #(quot % 2)]
                    [identity #(+ % (quot % 3))])
        sucker-charge (if (zero? cha)
                 [identity #(- % (quot % 4))]
                 [identity #(+ % (quot % 3))])]
    [cost cha (-> cost id-charge sucker-charge charge)]))

(def ^:private initial
  "DB that contains the initial possibilities for costs and appearances of items"
  (apply db (concat (for [[base cha cost] cost-data]
                      [base-cha-cost base cha cost])
                    (for [{:keys [name appearances] :as i} items
                          res (if-not (and (:artifact i) (:base i))
                                (concat (for [a appearances]
                                          [appearance-name a name])
                                        (for [a appearances
                                              n (item-names a)]
                                          [appearance-name n name])))
                          :when res]
                      res))))

(def blind-appearances
  (into {} (for [[generic-name typekw glyph] [["stone" :gem \*]
                                              ["gem" :gem \*]
                                              ["potion" :potion \!]
                                              ["wand" :wand \/]
                                              ["spellbook" :spellbook \+]
                                              ["scroll" :scroll \?]]]
             [generic-name ((kw->itemtype typekw) {:glyph glyph})])))

(defmacro query [discoveries qr]
  `(with-dbs [@#'initial ~discoveries] ~qr))

(defn appearance-of [item]
  (or (and (item-names (:name item)) (:generic item))
      (:name item)))

(defn new-discoveries [] empty-db)

(defn- propc [appearance id prop]
  (fresh [propval]
    (conda
      [(appearance-prop-val appearance prop propval)
       (project [id]
         (== propval (prop (name->item id))))]
      [succeed])))

(defn- merge-records
  "Presumes same type of all items, returns possibly partial ItemType with common properties to all of the records"
  [recs]
  (reduce (fn merge-item [r s]
            (reduce (fn merge-entry [r k]
                      (if (and (contains? r k) (contains? s k))
                        (if (= (get r k) (get s k))
                          r
                          (assoc r k nil))
                        (dissoc r k)))
                    r
                    (concat (keys s) (keys r))))
          (first recs)
          (rest recs)))

(defn- cha-group [cha]
  (condp > cha
    3 0 ; sell price
    6 6 ; cha<6
    8 7 ; cha 6-7
    11 10 ; cha 8-10
    16 15 ; cha 11-15
    18 17 ; cha 16-17
    19 18 ; cha 18
    25)) ; cha>18

(defn- pricec [appearance id]
  (fresh [cha cost price]
    (conda
      [(appearance-cha-cost appearance cha cost)
       (project [id cha cost]
         (base-cha-cost price (cha-group cha) cost)
         (== price (:price (name->item id))))]
      [succeed])))

(defn- possibleo [appearance id]
  (fresh [x]
    (appearance-name appearance id)
    (conda
      [(discovery x id) (== x appearance)]
      [(discovery appearance x) (== x id)]
      [succeed])
    (pricec appearance id)
    (everyg (partial propc appearance id) observable-props)))

(defn- eliminatedo [appearance id]
  (fresh [x]
    (appearance-name appearance id)
    (project [appearance]
      (if (or (names appearance) (exclusive-appearances appearance))
        succeed
        fail))
    (conda
      [(discovery appearance id) fail]
      [(possibleo appearance id)
       (!= x id)
       (conda
         [(possibleo appearance x) fail]
         [succeed])])))

(defn knowable-appearance?
  "Does it make sense to know anything about this appearance?  Not true for unnamed lamps and other non-exclusive appearances"
  [appearance]
  {:pre [(string? appearance)]}
  (not (or (blind-appearances appearance)
           (and (not (names appearance))
                (not (exclusive-appearances appearance))))))

(defn add-discovery [game appearance id]
  {:pre [(string? appearance) (string? id)]}
  (if (or (not (knowable-appearance? appearance))
          (= appearance id))
    game
    (let [id (get jap->eng id id)]
      (log/debug "adding discovery: >" appearance "< is >" id "<")
      (update game :discoveries db-fact discovery appearance id))))

(defn add-discoveries [game discoveries]
  (reduce (fn [game [appearance id]]
            (add-discovery game appearance id))
          game
          discoveries))

(defn- eliminate-group [game appearance]
  (log/debug "group elimination for" appearance)
  (when-let [[[a i]] (seq (query (:discoveries game)
                                 (run 1 [a i]
                                      (appearance-name appearance i)
                                      (eliminatedo a i))))]
    (log/debug "discovery by group elimination of" appearance)
    (add-discovery game a i)))

(defn- eliminate-one [game]
  (when-let [[[a i]] (seq (query (:discoveries game)
                                 (run 1 [a i]
                                      (eliminatedo a i))))]
    (log/debug "discovery by elimination:")
    (add-discovery game a i)))

(defn add-eliminated
  ([game]
   (last (take-while some? (iterate eliminate-one game))))
  ([game appearance]
   (last (take-while some? (iterate #(eliminate-group % appearance) game)))))

(defn possible-ids
  "Return n or all possible ItemTypes for the given item, taking current discoveries into consideration"
  ([game item]
   (possible-ids game item false))
  ([game item n]
   {:pre [(:discoveries game) (:name item)]}
   (or (if-let [unseen-item (blind-appearances (:name item))]
         [unseen-item])
       (if-let [named-arti (name->item (:specific item))]
         [named-arti])
       (if-let [known-item (name->item (:name item))] ; identified in-game
         [known-item])
       (->> (run n [q] (possibleo (appearance-of item) q))
            (query (:discoveries game)) (map name->item) seq)
       (log/error (IllegalArgumentException. "unknown itemtype for item")
                  item))))

(defn initial-ids
  "Return n or all possible ItemTypes for the given item without taking discoveries into consideration"
  ([item]
   (initial-ids item false))
  ([item n]
   (possible-ids {:discoveries (new-discoveries)} item n)))

(defn item-id
  "Returns the common properties of all possible ItemTypes for the given item (or simply the full record if unambiguous) optionally taking current discoveries into consideration"
  ([item]
   (item-id {:discoveries (new-discoveries)} item))
  ([game item]
   {:pre [(:discoveries game) (:name item)]}
   (merge-records (possible-ids game item))))

(defn item-type [item]
  (typekw (first (initial-ids item 1))))

(defn item-subtype [item]
  (:subtype (first (initial-ids item 1))))

(defn item-weight [item]
  (:weight (first (initial-ids item 1))))

(defn item-name [game item]
  (:name (item-id game item)))

(defn know-id?
  "Can the item be unambiguously identified?"
  [game item]
  (some? (item-name game item)))

(defn possible-names [game item]
  (map :name (possible-ids game item)))

(defn add-prop-discovery [game appearance prop propval]
  {:pre [(string? appearance) (observable-props prop)]}
  (if (knowable-appearance? appearance)
    (do (log/debug "for appearance" appearance
                   "adding observed property" prop "with value" propval)
        (-> game
            (update :discoveries db-fact appearance-prop-val
                    appearance prop propval)
            (add-eliminated appearance)))
    game))

(defn add-observed-cost
  ([game appearance cha cost sell?]
   {:pre [(number? cha) (number? cost) (string? appearance)
          (:discoveries game)]}
   (if (knowable-appearance? appearance)
     (do (log/debug "for appearance" appearance
                    "adding observed cost" cost)
         (-> game
             (update :discoveries db-fact appearance-cha-cost
                     appearance (if sell?
                                  0
                                  (cha-group cha)) cost)
             (add-eliminated appearance)))
     game))
  ([{:keys [player] :as game} appearance cost]
   (add-observed-cost game appearance (-> player :stats :cha) cost false))
  ([{:keys [player] :as game} appearance cost sell?]
   (add-observed-cost game appearance (-> player :stats :cha) cost sell?)))

(defn ambiguous-appearance? [game item]
  (and (not (:generic item))
       (names (:name item))))

(defn know-price? [game item]
  (seq (query (:discoveries game)
         (run 1 [q]
           (fresh [cha cost]
             (appearance-cha-cost (appearance-of item) cha cost))))))

(defn know-prop? [game item prop]
  (seq (query (:discoveries game)
         (run 1 [q]
           (fresh [_]
             (appearance-prop-val (appearance-of item) prop _))))))

#_(-> (#'anbf.game/new-game)
      (assoc-in [:player :stats :cha] 13)
      (add-observed-cost "lamp1" 10) ; lamp called lamp1 costs 10
      (item-name {:name "lamp" :generic "lamp2"})) ;=> lamp2 is magic

#_(-> (#'anbf.game/new-game)
      (assoc-in [:player :stats :cha] 15)
      (add-prop-discovery "silver wand" :engrave :stop) ; engrave-id silver wand
      (add-observed-cost "silver wand" 500) ; silver wand costs 500 (is WoDeath)
      (add-observed-cost "aluminum wand" 500) ; aluminum wand costs 500
      (item-name {:name "aluminum wand"})) ;=> aluminum is wand of wishing

#_(-> (#'anbf.game/new-game)
      (assoc-in [:player :stats :cha] 14)
      (add-observed-cost "scroll labeled NR 9" 8 :sell) ; sell price 8
      (item-name {:name "scroll labeled NR 9"})) ;=> is identify
