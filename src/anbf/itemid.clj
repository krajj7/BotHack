(ns anbf.itemid
  (:refer-clojure :exclude [==])
  (:require [clojure.tools.logging :as log]
            [clojure.core.logic :refer :all]
            [clojure.core.logic.pldb :refer :all]
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

(defmacro query [discoveries qr]
  `(with-db ~discoveries ~qr))

(defn- possibilities ; TODO consider price/cost, zaptype, etc.
  "Return all (or n if specified) possible ItemTypes for the given item"
  ([discoveries item]
   (possibilities discoveries item nil))
  ([discoveries item n]
   (or (if-let [unseen-item (blind-appearances (:name item))]
         [unseen-item])
       (if-let [named-arti (name->item (:specific item))]
         [named-arti])
       (if-let [known-item (name->item (:name item))] ; identified in-game
         [known-item])
       (->> (if n
              (run n [q] (itemid-appearance q (:name item)))
              (run* [q] (itemid-appearance q (:name item))))
            (query discoveries)
            (map name->item)
            seq)
       (log/error (IllegalArgumentException. "unknown itemtype for item")
                  item))))

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
  "Returns the common properties of all possible ItemTypes for the given item (or simply the full record if unambiguous) taking current discoveries into consideration"
  [game item]
  {:pre [(:discoveries game) (:name item)]}
  (merge-records (possibilities (:discoveries game) item)))

(defn initial-id
  "Returns the common properties of all possible ItemTypes for the given item (or simply the full record if unambiguous) *without* taking current discoveries into consideration"
  [item]
  (merge-records (possibilities initial-discoveries item)))

(defn possible-ids
  "Return all possible ItemTypes for the given item"
  [game item]
  {:pre [(:discoveries game) (:name item)]}
  (possibilities (:discoveries game) item))

(defn item-type [item]
  (typekw (first (possibilities initial-discoveries item 1))))

(defn item-subtype [item]
  (:subtype (first (possibilities initial-discoveries item 1))))

(defn item-weight [item]
  (:weight (first (possibilities initial-discoveries item 1))))

(defn item-name [game item]
  (:name (item-id game item)))

(defn know-id?
  "Can the item be unambiguously identified?"
  [game item]
  (some? (item-name game item)))

(defn add-discovery [game appearance id]
  (if (or (blind-appearances appearance) (= appearance id)
          (not (exclusive-appearances appearance))) ; old gray stones will stay gray stones...
    game
    (let [id (get jap->eng id id)]
      (log/debug "adding discovery: >" appearance "< is >" id "<")
      (update game :discoveries
              (fn integrate-discovery [db]
                (as-> db res
                  (reduce #(db-retraction %1 itemid-appearance
                                          %2 appearance)
                          res
                          (query res
                                 (run* [q]
                                       (itemid-appearance q appearance))))
                  (reduce #(db-retraction %1 itemid-appearance
                                          id %2)
                          res
                          (query res
                                 (run* [q]
                                       (itemid-appearance id q))))
                  (db-fact res itemid-appearance id appearance)))))))

(defn add-discoveries [game discoveries]
  (reduce (fn [game [appearance id]]
            (add-discovery game appearance id))
          game
          discoveries))

(defn ambiguous-appearance? [game item]
  (not (or (:generic item)
           (exclusive-appearances (:name item))
           (know-id? game item))))
