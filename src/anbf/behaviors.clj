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

(defn attach-candles [game]
  (if-let [[slot _] (and (not= 7 (:candles (val (have game "candelabrum"))))
                         (have game candle?))]
    (with-reason "attaching candles to candelabrum"
      (->Apply slot))))

(defn- handle-candelabrum [game]
  (if-let [[slot candelabrum] (have game "candelabrum")]
    (if (or (and (:lit candelabrum) (invocation-complete? game))
            (and (not (:lit candelabrum)) (not (invocation-complete? game))))
      (with-reason "lighting or snuffing out candelabrum"
        (->Apply slot)))))

(defn- ring-bell [game]
  (if-not (or (invocation-complete? game)
              (.endsWith (:last-topline game)
                         " issues an unsettling shrill sound..."))
    (if-let [[slot _] (have game "silver bell")]
      (with-reason "ringing the bell"
        (->Apply slot)))))

(defn- read-book [game]
  (if-not (invocation-complete? game)
    (if-let [[slot _] (have game "Book of the Dead")]
      (with-reason "reading the book"
        (->Read slot)))))

(defn invocation
  "prerequisites: have non-cursed book, non-cursed candelabrum, non-cursed bell, 7 candles in main inventory"
  [game]
  (if-not (get-level game :main :sanctum)
    (or (seek-level game :main :end)
        (seek game :vibrating)
        (attach-candles game)
        (handle-candelabrum game)
        (ring-bell game)
        (read-book game)
        (descend game))))

(defn examine-containers [game]
  ; TODO navigate
  ; TODO explorable or can unlock
  (if-let [[slot item] (have game explorable-container?)]
    (with-reason "learning contents of" item
      (->Apply slot))))

(defn examine-containers-here [game]
  ; TODO unlock
  (if (some explorable-container? (:items (at-player game)))
    (with-reason "learning contents of containers on ground"
      (->Loot))))

(defn unbag
  "Return action to take qty of the item out of a bag, nil if item is already present in main inventory or not found in any bags"
  ([game maybe-bag-slot item] (unbag game maybe-bag-slot item 1))
  ([game maybe-bag-slot item qty]
   (if (container? (inventory-slot game maybe-bag-slot))
     (with-reason "preparing item -" (:name item)
       (take-out maybe-bag-slot (:label item) qty)))))
