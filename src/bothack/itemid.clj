(ns bothack.itemid
  "identifying items using some logic programming"
  (:refer-clojure :exclude [==])
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.core.logic :refer :all]
            [clojure.core.logic.pldb :refer :all]
            [bothack.itemtype :refer :all]
            [bothack.itemdata :refer :all]
            [bothack.util :refer :all]))

(db-rel discovery ^:index appearance ^:index itemid)
(db-rel appearance-name ^:index appearance ^:index itemid)
(db-rel appearance-cha-cost ^:index appearance ^:index cha ^:index cost)
(db-rel appearance-prop-val ^:index appearance ^:index propname ^:index propval)
(db-rel base-cha-cost ^:index base ^:index cha ^:index cost)

(def observable-props #{:engrave :target :hardness :autoid :food})

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

(def names (set (apply concat (vals item-names))))

(def ^:private cost-data
  (for [cost #_[60 300] (range 501)
        [cha charge] [[5 #(* 2 %)]
                      [7 #(+ % (quot % 2))]
                      [10 #(+ % (quot % 3))]
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
      (:name (name->item (:specific item)))
      (:name item)))

(defn new-discoveries [] empty-db)

(defn- propc [appearance id prop]
  (fresh [propval]
    (conda
      [(appearance-prop-val appearance prop propval)
       (project [id]
         (if (nil? (prop (name->item id)))
           (== propval false)
           (== propval (prop (name->item id)))))]
      [succeed])))

(defn- merge-records-fn
  "Presumes same type of all items, returns possibly partial ItemType with
  common properties to all of the records"
  []
  (memoize
    (fn [recs]
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
              (rest recs)))))

(def ^:private initial-merge (merge-records-fn))

(defn- cha-group [cha]
  (condp > cha
    3 0 ; sell price
    6 5 ; cha<6
    8 7 ; cha 6-7
    11 10 ; cha 8-10
    16 15 ; cha 11-15
    18 17 ; cha 16-17
    19 18 ; cha 18
    25)) ; cha>18

(declare item-type)

(defn- pricec [appearance id]
  (fresh [cha cost price enchant]
    (conda
      [(appearance-cha-cost appearance cha cost)
       (project [id cha]
         (base-cha-cost price (cha-group cha) cost)
         (membero enchant (if (= :armor (item-type (name->item id)))
                            [0 1 2 3 4]
                            [0]))
         (project [enchant]
           (== price (+ (* enchant 10) (:price (name->item id))))))]
      [succeed])))

(defn knowable-appearance?
  "Does it make sense to know anything about this appearance?  Not true for
  unnamed lamps and other non-exclusive appearances"
  [appearance]
  {:pre [(string? appearance)]}
  (not (or (blind-appearances appearance)
           (and (not (names appearance))
                (not (exclusive-appearances appearance))))))

(defn- possibleo [appearance id]
  (fresh [x]
    (appearance-name appearance id)
    (condu
      [(project [appearance]
         (== false (knowable-appearance? appearance)))]
      [(discovery x id) (== x appearance)]
      [(discovery appearance x) (== x id)]
      [(pricec appearance id)
       (everyg (partial propc appearance id) observable-props)])))

(defn- eliminatedo [appearance id]
  (fresh [x]
    (appearance-name appearance id)
    (project [appearance]
      (if (or (names appearance) (exclusive-appearances appearance))
        succeed
        fail))
    (condu
      [(discovery appearance id) fail]
      [(possibleo appearance id)
       (!= x id)
       (condu
         [(possibleo appearance x) fail]
         [succeed])])))

(defn- possibilities-fn [game]
  (memoize
    (fn [appearance n]
      (or (if-let [unseen-item (blind-appearances appearance)]
            [unseen-item])
          (if-let [known-item (name->item appearance)]
            [known-item])
          (->> (run n [q] (possibleo appearance q))
               (query (:discoveries game)) (map name->item) seq)
          (log/error (IllegalArgumentException. "unknown itemtype for item")
                     appearance)))))

(defn- reset-possibilities [game]
  (log/debug "reset possibilities cache")
  (assoc game
         :merged (merge-records-fn)
         :possibilities (possibilities-fn game)))

(declare add-discovery)

(defn- add-eliminated [game appearance]
  (log/debug "group elimination for" appearance)
  (if-let [[[a i]] (seq (query (:discoveries game)
                               (run 1 [a i]
                                    (appearance-name appearance i)
                                    (eliminatedo a i))))]
    (add-discovery game a i)
    game))

(defn- add-fact [game relname appearance & args]
  (as-> game res
    (apply update res :discoveries db-fact relname appearance args)
    (if (not= (:discoveries game) (:discoveries res))
      (-> res
          reset-possibilities
          (add-eliminated appearance))
      game)))

(defn add-discovery [game appearance id]
  {:pre [(string? appearance) (:discoveries game)]}
  (if (or (= appearance id) (not (knowable-appearance? appearance)))
    game
    (let [id (get jap->eng id id)]
      (log/debug "adding discovery: >" appearance "< is >" id "<")
      (add-fact game discovery appearance id))))

(defn add-discoveries [game discoveries]
  (reduce (fn [game [appearance id]]
            (add-discovery game appearance id))
          game
          discoveries))

(def ^:private initial-possibilities
  (possibilities-fn {:discoveries (new-discoveries)}))

(defn- possibilities [game appearance n]
  ((or (:possibilities game)
       initial-possibilities) appearance n))

(defn possible-ids
  "Return n or all possible ItemTypes for the given item, taking current
  discoveries into consideration"
  ([game item]
   (possible-ids game item false))
  ([game item n]
   {:pre [(:discoveries game) (:name item)]}
   (possibilities game (appearance-of item) n)))

(defn initial-ids
  "Return n or all possible ItemTypes for the given item without taking
  discoveries into consideration"
  ([item]
   (initial-ids item false))
  ([item n]
   {:pre [(:name item)]}
   (initial-possibilities (appearance-of item) n)))

(defn item-id
  "Returns the common properties of all possible ItemTypes for the given item
  (or simply the full record if unambiguous) optionally taking current
  discoveries into consideration"
  ([item]
   (item-id {:discoveries (new-discoveries)} item))
  ([game item]
   {:pre [(:discoveries game) (:name item)]}
   ((or (:merged game)
        initial-merge) (possible-ids game item))))

(defn item-type [item]
  (if item
    (typekw (first (initial-ids item 1)))))

(defn item-subtype [item]
  (if item
    (:subtype (first (initial-ids item 1)))))

(defn item-weight [item]
  (if item
    (:weight (first (initial-ids item 1)))))

(defn item-name [game item]
  (if item
    (:name (item-id game item))))

(defn know-id?
  "Can the item be unambiguously identified?"
  [game item]
  (some? (item-name game item)))

(defn possible-names [game item]
  (if item
    (map :name (possible-ids game item))))

(defn add-prop-discovery [game appearance prop propval]
  {:pre [(string? appearance) (observable-props prop) (:discoveries game)]}
  (if (knowable-appearance? appearance)
    (do (log/debug "for appearance" appearance
                   "adding observed property" prop "with value" propval)
        (add-fact game appearance-prop-val appearance prop propval))
    game))

(defn add-observed-cost
  ([game appearance cha cost sell?]
   {:pre [(number? cha) (number? cost) (string? appearance)
          (:discoveries game)]}
   (if (knowable-appearance? appearance)
     (do (log/debug "for appearance" appearance
                    "adding observed cost" cost)
         (add-fact game appearance-cha-cost appearance (if sell?
                                                         0
                                                         (cha-group cha)) cost))
     game))
  ([{:keys [player] :as game} appearance cost]
   (add-observed-cost game appearance (-> player :stats :cha) cost false))
  ([{:keys [player] :as game} appearance cost sell?]
   (add-observed-cost game appearance (-> player :stats :cha) cost sell?)))

(defn ambiguous-appearance? [item]
  {:pre [(:name item)]}
  (and (not (:generic item))
       (item-names (:name item))))

(defn know-price? [game item]
  (or (:price (item-id game item))
      (seq (query (:discoveries game)
             (run 1 [q]
               (fresh [cha cost]
                 (appearance-cha-cost (appearance-of item) cha cost)))))))

(defn know-appearance? [game id]
  {:pre [(string? id) (:discoveries game)]}
  (= 1 (count (query (:discoveries game)
                (run 2 [q]
                  (conda
                    [(discovery q id)]
                    [(possibleo q id)]))))))

(defn could-be? [game id item]
  {:pre [(string? id) (:name item)]}
  (some #(= id (:name %)) (possible-ids game item)))

(defn- facts-for-name [db name]
  (concat (for [id (query db (run* [id] (discovery name id)))]
            [discovery name id])
          (for [[propname propval] (query db (run* [p v]
                                               (appearance-prop-val name p v)))]
            [appearance-prop-val name propname propval])
          (for [[cha cost] (query db (run* [cha cost]
                                       (appearance-cha-cost name cha cost)))]
            [appearance-cha-cost name cha cost])))

(defn- forget-name [game name]
  (log/warn "forgot name" name)
  (if ((:used-names game) name)
    game
    (update game :discoveries
            #(apply db-retractions % (facts-for-name % name)))))

(defn name-for [game item]
  (->> item :name item-names
       (find-first #(not ((:used-names game) %)))))

(defn name-variants [name]
  (item-names (string/replace name #"(.*)[0-9]+" "$1")))

(defn forget-names [game names]
  (if (empty? names)
    game
    (reset-possibilities
      (reduce add-eliminated
              (reduce forget-name
                      (reduce #(update %1 :used-names disj %2) game names)
                      (for [name names
                            variant (name-variants name)]
                        variant))
              names))))

#_(-> (#'bothack.game/new-game)
      (assoc-in [:player :stats :cha] 9)
      (add-observed-cost "scroll labeled PRATYAVAYAH" 26)
      (add-observed-cost "scroll labeled PRATYAVAYAH" 34)
      (possible-ids {:name "scroll labeled PRATYAVAYAH"}))

#_(-> (#'bothack.game/new-game)
      (assoc-in [:player :stats :cha] 13)
      (add-observed-cost "lamp1" 10) ; lamp called lamp1 costs 10
      (item-name {:name "lamp" :generic "lamp2"})) ;=> lamp2 is magic

#_(-> (#'bothack.game/new-game)
      (assoc-in [:player :stats :cha] 15)
      (add-prop-discovery "silver wand" :engrave :stop) ; engrave-id silver wand
      (add-observed-cost "silver wand" 500) ; silver wand costs 500 (is WoDeath)
      (add-observed-cost "aluminum wand" 500) ; aluminum wand costs 500
      (item-name {:name "aluminum wand"})) ;=> aluminum is wand of wishing

#_(-> (#'bothack.game/new-game)
      (assoc-in [:player :stats :cha] 14)
      (add-observed-cost "scroll labeled NR 9" 8 :sell) ; sell price 8
      (item-name {:name "scroll labeled NR 9"})) ;=> is identify

#_(-> (#'bothack.game/new-game)
      (assoc-in [:player :stats :cha] 14)
      (add-prop-discovery "silver wand" :target true)
      (add-prop-discovery "silver wand" :autoid false)
      ;(add-discovery "silver wand" "wand of polymorph")
      (possible-ids {:name "silver wand"})
      ((partial map :name)))

#_(-> (#'bothack.game/new-game)
      (assoc-in [:player :stats :cha] 10)
      (add-observed-cost "old gloves" 93)
      (possible-ids {:name "old gloves"})
      ((partial map :name)))

#_(-> (#'bothack.game/new-game)
      (assoc-in [:player :stats :cha] 10)
      (add-observed-cost "opera cloak" 50 :sell)
      (possible-ids {:name "opera cloak"})
      ((partial map :name)))

#_(-> (#'bothack.game/new-game)
      (assoc-in [:player :stats :cha] 5)
      (add-observed-cost "sky blue potion" 400)
      (add-observed-cost "sky blue potion" 533)
      (possible-ids {:name "sky blue potion"})
      ((partial map :name)))

#_(-> (#'bothack.game/new-game)
      (could-be? "magic lamp" {:name "lamp" :generic "lamp1"}))
