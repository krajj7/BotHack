(ns anbf.tracker
  "Tracking and pairing monsters frame-to-frame and their deaths and corpses turn-by-turn"
  (:require [anbf.position :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.delegator :refer :all]
            [anbf.fov :refer :all]
            [anbf.tile :refer :all]
            [anbf.item :refer :all]
            [anbf.itemtype :refer :all]
            [anbf.monster :refer :all]
            [anbf.handlers :refer :all]
            [anbf.level :refer :all]
            [anbf.player :refer :all]
            [anbf.util :refer :all]
            [clojure.tools.logging :as log]))

(defn- transfer-pair [game [old-monster monster]]
  (log/debug "transfer:" \newline old-monster "to" \newline monster)
  (update-curlvl-monster
    game monster
    (fn [monster]
      (cond-> monster
        :always (into (select-keys old-monster
                                   [:type :peaceful :cancelled :awake]))
        (not= (position old-monster)
              (position monster)) (assoc :awake true)))))

(defn filter-visible-uniques
  "If a unique monster was remembered and now is visible, remove all remembered instances"
  [game]
  (let [monsters (vals (curlvl-monsters game))
        id (comp :name :type)]
    (reduce remove-curlvl-monster game
            (for [m monsters
                  :when ((every-pred (complement :remembered)
                                     (comp :unique :gen-flags :type)) m)
                  n (filter #(and (= (id m) (id %)) (:remembered %))
                            monsters)]
              (position n)))))

(defn- transfer-unpaired [game unpaired]
  ; TODO don't transfer if we would know monsters position with ESP
  ;(log/debug "unpaired" unpaired)
  (if-not (or (visible? game unpaired) (= (:glyph unpaired) \I))
    (reset-curlvl-monster game (assoc unpaired :remembered true))
    game))

(defn track-monsters
  "Try to transfer monster properties greedily from the old game snapshot to the new, even if the monsters moved slightly."
  [new-game old-game]
  (if (or (not= (:dlvl old-game) (:dlvl new-game))
          (hallu? (:player new-game)))
    new-game ; TODO track stair followers?
    (loop [pairs {}
           new-monsters (curlvl-monsters new-game)
           old-monsters (curlvl-monsters old-game)
           dist 0]
      (if (and (> 4 dist) (seq old-monsters))
        (if-let [[p m] (first new-monsters)]
          (if-let [[[cp cm] & more] (seq (filter
                                           (fn candidate? [[_ n]]
                                             (and (= (:glyph m) (:glyph n))
                                                  (= (:color m) (:color n))
                                                  (= (:friendly m)
                                                     (:friendly n))
                                                  (= dist (distance m n))))
                                           old-monsters))]
            (if more ; ignore ambiguous cases
              (recur pairs (dissoc new-monsters p) old-monsters dist)
              (recur (assoc pairs p [cm m])
                     (dissoc new-monsters p)
                     (dissoc old-monsters cp)
                     dist))
            (recur pairs (dissoc new-monsters p) old-monsters dist))
          (recur pairs (apply dissoc (curlvl-monsters new-game) (keys pairs))
                 old-monsters (inc dist)))
        (as-> new-game res
          (reduce transfer-unpaired res (vals old-monsters))
          (reduce transfer-pair res (vals pairs)))))))

(defn- mark-kill [game old-game]
  (if-let [dir (and (not (impaired? (:player old-game)))
                    (#{:move :attack} (typekw (:last-action old-game)))
                    (:dir (:last-action old-game)))]
    (let [pos (in-direction (:player old-game) dir)
          tile (at-curlvl game pos)
          old-monster (or (monster-at (curlvl old-game) pos)
                          (unknown-monster (:x pos) (:y pos) (:turn game)))]
      (if (or (item? tile) (monster? tile) (water? tile) ; don't mark deaths that clearly didn't leave a corpse
              (blind? (:player game)))
        (update-curlvl-at game old-monster
                          mark-death old-monster (:turn game))
        game))
    game))

(defn death-tracker [anbf]
  (let [old-game (atom nil)]
    (reify
      ActionChosenHandler
      (action-chosen [_ _]
        (reset! old-game @(:game anbf)))
      ToplineMessageHandler
      (message [_ msg]
        (if (and @old-game (not (hallu? (:player @old-game))))
          (condp re-first-group msg
            #"You (kill|destroy) [^.!]*[.!]"
            (update-before-action anbf mark-kill @old-game)
            ;#" is (killed|destroyed)"
            nil))))))

(defn- only-fresh-deaths? [tile corpse-type turn]
  (let [relevant-deaths (remove (fn [[death-turn
                                      {monster-type :type :as monster}]]
                                  (and (< 500 (- turn death-turn))
                                       monster-type
                                       (or (not (.contains (:name monster-type)
                                                           (:name corpse-type)))
                                           (not (.contains (:name monster-type)
                                                           " zombie")))
                                       (not= corpse-type monster-type)))
                                (:deaths tile))
        unsafe-deaths (filter (fn [[death-turn _]] (<= 30 (- turn death-turn)))
                              relevant-deaths)
        safe-deaths (filter (fn [[death-turn _]] (> 30 (- turn death-turn)))
                            relevant-deaths)]
    (and (empty? unsafe-deaths)
         (seq safe-deaths))))

(defn fresh-corpse?
  "Works only for corpses on the ground that haven't been moved"
  [game pos item]
  (if-let [corpse-type (and (corpse? item) (single? item)
                            (:monster (name->item (:name item))))]
    (or (:permanent corpse-type)
        (and (only-fresh-deaths?
               (at-curlvl game pos) corpse-type (:turn game))))))
