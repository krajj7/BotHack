(ns anbf.behaviors
  (:require [clojure.tools.logging :as log]
            [anbf.handlers :refer :all]
            [anbf.actions :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.pathing :refer :all]
            [anbf.player :refer :all]
            [anbf.item :refer :all]
            [anbf.itemid :refer :all]
            [anbf.position :refer :all]
            [anbf.tile :refer :all]
            [anbf.util :refer :all]))

(defn- invocation-complete? [game]
  (stairs-down? (at-player game)))

(defn attach-candles
  [game]
  {:pre [(have game candelabrum)]}
  (if-let [[slot _] (and (not= 7 (:candles (secondv (have game candelabrum))))
                         (have game candle?))]
    (with-reason "attaching candles to candelabrum"
      (->Apply slot))))

(defn- handle-candelabrum [game]
  {:pre [(have game candelabrum #{:noncursed})]}
  (if-let [[slot candelabrum] (have game candelabrum)]
    (if (or (and (:lit candelabrum) (invocation-complete? game))
            (and (not (:lit candelabrum)) (not (invocation-complete? game))))
      (with-reason "lighting or snuffing out candelabrum"
        (->Apply slot)))))

(defn- ring-bell [game]
  {:pre [(have game bell #{:noncursed})]}
  (if-not (or (invocation-complete? game)
              (.endsWith (:last-topline game)
                         " issues an unsettling shrill sound..."))
    (if-let [[slot _] (have game bell)]
      (with-reason "ringing the bell"
        (->Apply slot)))))

(defn- read-book [game]
  {:pre [(have game book #{:noncursed})]}
  (if-not (invocation-complete? game)
    (if-let [[slot _] (have game "Book of the Dead")]
      (with-reason "reading the book"
        (->Read slot)))))

(defn unbag
  "Return action to take out 1 or qty of the item out of a bag, returns nil if item is already present in main inventory or not found in any bags"
  ([game maybe-bag-slot item] (unbag game maybe-bag-slot item 1))
  ([game maybe-bag-slot item qty]
   (if (not= item (inventory-slot game maybe-bag-slot))
     (with-reason "preparing item -" (:name item)
       (take-out maybe-bag-slot (:label item) qty)))))

(defn bless
  "Take holy water out of a bag (if bagged) and dip item at slot into it"
  [game slot]
  {:pre [(:dungeon game) (char? slot)]}
  (if-let [[water-slot water] (and (-> (inventory-slot game slot)
                                       :buc blessed? not)
                                   (have game holy-water? #{:bagged}))]
    (with-reason "blessing" (inventory-slot game slot)
      (or (unbag game water-slot water)
          (->Dip slot water-slot)))))

(defn uncurse-invocation-artifacts [game]
  (with-reason "making sure invocation artifacts are not cursed"
    (if-let [[slot _] (have game #{bell candelabrum book} #{:unsafe-buc})]
      (bless game slot))))

(defn invocation
  "prerequisites: have non-cursed book, non-cursed candelabrum, non-cursed bell, 7 candles in main inventory"
  [game]
  (if-not (get-level game :main :sanctum)
    (or (seek-level game :main :end)
        (seek game :vibrating)
        (attach-candles game)
        (uncurse-invocation-artifacts game)
        (handle-candelabrum game)
        (ring-bell game)
        (read-book game)
        (descend game))))

(defn seek-high-altar [{:keys [player] :as game}]
  (with-reason "seeking high altar"
    (seek game #(and (altar? %)
                     (or (= (:alignment player) (:alignment %))
                         (not (:walked %)))))))
