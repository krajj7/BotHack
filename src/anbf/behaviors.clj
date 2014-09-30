(ns anbf.behaviors
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [anbf.handlers :refer :all]
            [anbf.actions :refer :all]
            [anbf.player :refer :all]
            [anbf.item :refer :all]
            [anbf.itemid :refer :all]
            [anbf.util :refer :all]))

(defn use-action [item]
  (case (typekw item)
    :ring ->PutOn
    :amulet ->PutOn
    :tool ->PutOn
    :armor ->Wear))

(defn make-use [game slot]
  (if-let [itemtype (some->> slot (inventory-slot game) (item-id game))]
    ; TODO if already occupied
    ((use-action itemtype) slot)))

(defn remove-action [item]
  (case (typekw item)
    :ring ->Remove
    :amulet ->Remove
    :tool ->Remove
    :armor ->TakeOff))

(defn remove-use [game slot]
  (let [item (inventory-slot game slot)
        itemtype (item-id game item)]
    (if (and itemtype (not= :cursed (:buc item)))
      ((remove-action itemtype) slot))))

(defn without-levitation [game action]
  ; XXX doesn't work for intrinsic levitation
  (if-let [[slot _] (have-levi-on game)]
    (with-reason "action" (typekw action) "forbids levitation"
      (remove-use game slot))
    action))

(defn dig [[slot item] dir]
  (if (:in-use item)
    (->ApplyAt slot dir)
    (->Wield slot)))

(defn attach-candles [game]
  ; TODO
  nil)
