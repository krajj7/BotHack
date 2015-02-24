(ns bothack.behaviors
  (:require [clojure.tools.logging :as log]
            [bothack.handlers :refer :all]
            [bothack.actions :refer :all]
            [bothack.dungeon :refer :all]
            [bothack.pathing :refer :all]
            [bothack.player :refer :all]
            [bothack.game :refer :all]
            [bothack.item :refer :all]
            [bothack.itemid :refer :all]
            [bothack.position :refer :all]
            [bothack.tile :refer :all]
            [bothack.util :refer :all]))

(defn- invocation-complete? [game]
  (stairs-down? (at-player game)))

(defn attach-candles
  [game]
  {:pre [(have game candelabrum)]}
  (if-let [[slot _] (and (some->> (have game candelabrum) val :candles (not= 7))
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
  "prerequisites: have non-cursed book, non-cursed candelabrum, non-cursed
  bell, 7 candles in main inventory"
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

(defn pray [game]
  (with-reason "pray"
    (if (can-pray? game)
      (->Pray))))

(defn enhance [game]
  (if (:can-enhance (:player game))
    (enhance-all)))
