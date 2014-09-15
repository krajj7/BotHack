(ns anbf.itemid
  (:refer-clojure :exclude [==])
  (:require [clojure.tools.logging :as log]
            [clojure.core.logic :refer :all]
            [clojure.core.logic.pldb :refer :all]
            [anbf.item :refer :all]
            [anbf.itemtype :refer :all]
            [anbf.itemdata :refer :all]
            [anbf.util :refer :all]))

(db-rel itemid ^:index i)
(db-rel itemid-appearance ^:index i ^:index a)
(db-rel itemid-zaptype ^:index i z)
(db-rel itemid-price ^:index i p)
(db-rel appearance ^:index a)
(db-rel appearance-price ^:index a ^:index p) ; for exclusive appearances, for non-exclusive only cost matters
(db-rel appearance-zaptype ^:index a ^:index z)

(def initial-discoveries
  (->> (for [{:keys [name appearances price zaptype] :as i} items]
         (apply vector
                [itemid name]
                (if price
                  [itemid-price name price])
                (if zaptype
                  [itemid-zaptype name zaptype])
                (if-not (and (:artifact i) (:base i))
                  (apply concat
                         (for [a appearances]
                           [[appearance a]
                            [itemid-appearance name a]])))))
       (apply concat)
       (remove nil?)
       (apply db)))

(def blind-appearances
  (into {} (for [[generic-name typekw glyph] [["stone" :gem \*]
                                              ["gem" :gem \*]
                                              ["potion" :potion \!]
                                              ["wand" :wand \/]
                                              ["spellbook" :spellbook \+]
                                              ["scroll" :scroll \?]]]
             [generic-name ((kw->itemtype typekw) {:glyph glyph})])))

(defmacro query [game qr]
  `(with-db (:discoveries ~game) ~qr))

(defn- possibilities ; TODO consider price/cost, zaptype, etc.
  "Only for actual appearances, not identified names"
  ([game appearance]
   (possibilities game appearance nil))
  ([game appearance cost]
   (->> (run* [q] (itemid-appearance q appearance))
        (query game)
        (map name->item)
        seq)))

(defn possible-ids
  "Return all possible ItemTypes for the given item"
  [game {:keys [name specific cost] :as item}]
  (or (if-let [unseen-item (blind-appearances name)]
        [unseen-item])
      (if-let [named-arti (name->item specific)]
        [named-arti])
      (if-let [known-item (name->item name)] ; identified in-game
        [known-item])
      (possibilities game name cost)
      (log/error "unknown itemtype for item:" item)))

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

; TODO memoized version?
(defn item-id
  "Returns the common properties of all possible ItemTypes for the given item (or simply the full record if unambiguous)"
  [game item]
  (merge-records (possible-ids game item)))

(defn item-name [game item]
  (:name (item-id game item)))

(defn know-id?
  "Can the item be unambiguously identified?"
  [game item]
  (some? (item-name game item)))

(defn add-discovery [game appearance id]
  (let [id (get jap->eng id id)]
    (log/debug "adding discovery: >" appearance "< is >" id "<")
    (update-in game [:discoveries]
               (fn integrate-discovery [db]
                 (if (exclusive-appearances appearance)
                   (as-> db res
                     (reduce #(db-retraction %1 itemid-appearance
                                                  %2 appearance)
                             res
                             (query game
                                    (run* [q]
                                          (itemid-appearance q appearance))))
                     (reduce #(db-retraction %1 itemid-appearance
                                                  id %2)
                             res
                             (query game
                                    (run* [q]
                                          (itemid-appearance id q))))
                     (db-fact res itemid-appearance id appearance))
                   db))))) ; old gray stones will stay gray stones...

(defn add-discoveries [game discoveries]
  (reduce (fn [game [appearance id]]
            (add-discovery game appearance id))
          game
          discoveries))
