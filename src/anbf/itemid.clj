(ns anbf.itemid
  (:refer-clojure :exclude [==])
  (:require [clojure.tools.logging :as log]
            [clojure.core.logic :refer :all]
            [clojure.core.logic.pldb :as pldb]
            [anbf.itemtype :refer :all]
            [anbf.itemdata :refer :all]
            [anbf.util :refer :all]))

(pldb/db-rel itemid ^:index i)
(pldb/db-rel itemid-appearance ^:index i ^:index a)
(pldb/db-rel itemid-zaptype ^:index i z)
(pldb/db-rel itemid-price ^:index i p)
(pldb/db-rel appearance ^:index a)
(pldb/db-rel appearance-price ^:index a ^:index p) ; for exclusive appearances, for non-exclusive only cost matters
(pldb/db-rel appearance-zaptype ^:index a ^:index z)

(def initial-discoveries
  (->> (for [{:keys [name appearances price zaptype] :as i} items]
         (apply vector
                [itemid name]
                (if price
                  [itemid-price name price])
                (if zaptype
                  [itemid-zaptype name zaptype])
                (if-not (:artifact i)
                  (apply concat
                         (for [a appearances]
                           [[appearance a]
                            [itemid-appearance name a]])))))
       (apply concat)
       (remove nil?)
       (apply pldb/db)))

(def blind-appearances
  (into {} (map (fn [[generic-name typekw glyph]]
                  [generic-name ((kw->itemtype typekw) {:glyph glyph})])
                [["stone" :gem \*]
                 ["gem" :gem \*]
                 ["potion" :potion \!]
                 ["wand" :wand \/]
                 ["spellbook" :spellbook \+]
                 ["scroll" :scroll \?]])))

(defmacro query [game qr]
  `(pldb/with-db (:discoveries ~game) ~qr))

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

(defn item-id
  "Returns the common properties of all possible ItemTypes for the given item (or simply the full record if unambiguous)"
  [game item]
  (merge-records (possible-ids game item)))

(defn know-id?
  "Can the item be unambiguously identified?"
  [game item]
  (:name (item-id game item)))
