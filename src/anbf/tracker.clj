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

(defn- transfer-pair [{:keys [player] :as game} [old-monster monster]]
  (let [cur (monster-at game monster)]
    #_(log/debug "transfer:" \newline old-monster \newline "cur" \newline cur
               "to" \newline monster)
    (reset-monster game
      (as-> (or cur old-monster) monster
        (into monster (select-some old-monster
                                   [:type :cancelled :awake :first-known]))
        (if (not= :update (:peaceful monster))
          (assoc monster :peaceful (:peaceful old-monster))
          monster)
        (if (not= (position old-monster) (position monster))
          (assoc monster :awake true :just-moved true)
          monster)
        (case (compare (distance-manhattan player old-monster)
                       (distance-manhattan player monster))
          -1 (assoc monster :fleeing true)
          0 (assoc monster :fleeing (:fleeing old-monster))
          1 (assoc monster :fleeing false))
        (if (or (not cur) (= \I (:glyph cur)))
          (assoc monster :remembered true)
          monster)))))

(defn filter-visible-uniques
  "If a unique monster was remembered and now is visible, remove all remembered instances"
  [game]
  (let [monsters (curlvl-monsters game)]
    (reduce remove-monster game
            (for [m monsters
                  :when ((every-pred unique? (complement :remembered)) m)
                  n (filter #(and (= (typename m) (typename %)) (:remembered %))
                            monsters)]
              (position n)))))

(defn- transfer-unpaired [{:keys [player] :as game} unpaired]
  ;(log/debug "unpaired" unpaired)
  (let [tile (at-curlvl game unpaired)]
    (if (and (not (monster? tile))
             (not (and (blind? player)
                       (have-intrinsic? player :telepathy)
                       (not (mindless? unpaired))))
             (or (not (visible? game unpaired))
                 (and (#{\1 \2 \3 \4 \5} (:glyph unpaired))
                      ((some-fn stairs? boulder? :new-items) tile))))
      (reset-monster game (assoc unpaired :remembered true))
      game)))

(defn track-monsters
  "Try to transfer monster properties greedily from the old game snapshot to the new, even if the monsters moved slightly."
  [new-game old-game]
  (if (or (not= (:dlvl old-game) (:dlvl new-game))
          (hallu? (:player new-game)))
    new-game ; TODO track stair followers?
    (loop [pairs {}
           old-monsters (:monsters (curlvl old-game))
           new-monsters (if (and (blind? (:player new-game))
                                 (not (have-intrinsic? (:player new-game)
                                                       :telepathy)))
                          old-monsters
                          (:monsters (curlvl new-game)))
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
              (recur pairs old-monsters (dissoc new-monsters p) dist)
              (recur (assoc pairs p [cm m])
                     (dissoc old-monsters cp)
                     (dissoc new-monsters p)
                     dist))
            (recur pairs old-monsters (dissoc new-monsters p) dist))
          (recur pairs old-monsters
                 (apply dissoc (:monsters (curlvl new-game)) (keys pairs))
                 (inc dist)))
        (as-> new-game res
          (reduce transfer-unpaired res (->> (position (:player new-game))
                                             (dissoc old-monsters) vals))
          (reduce transfer-pair res (vals pairs)))))))

(defn- mark-kill [game old-game]
  (if-let [dir (and (not (dizzy? (:player old-game)))
                    (not (hallu? (:player old-game)))
                    (:dir (:last-action* game)))]
    (let [level (curlvl game)
          tile (in-direction level (:player old-game) dir)
          old-monster (or (monster-at (curlvl old-game) tile)
                          (unknown-monster (:x tile) (:y tile) (:turn game)))]
      (if (or (item? tile) (monster? tile) (pool? tile) ; don't mark deaths that clearly didn't leave a corpse
              (blind? (:player game)))
        (-> game
            (update-at tile dissoc :blocked)
            (update-at tile mark-death old-monster (:turn game))
            (remove-monster tile))
        game))
    game))

(defn death-tracker [anbf]
  (reify
    ToplineMessageHandler
    (message [_ msg]
      (if-let [old-game (:last-state @(:game anbf))]
        (if (not (hallu? (:player old-game)))
          (condp re-first-group msg
            #"You (kill|destroy) [^.!]*[.!]"
            (update-before-action anbf mark-kill old-game)
            ;#" is (killed|destroyed)" ...
            nil))))))

(defn- only-fresh-deaths? [tile corpse-type turn]
  (let [relevant-deaths (remove (fn [[death-turn {montype :type :as monster}]]
                                  (and (< 500 (- turn death-turn))
                                       montype
                                       (:name montype)
                                       (not= corpse-type montype)))
                                (:deaths tile))
        unsafe-deaths (filter (fn [[death-turn {montype :type :as monster}]]
                                (or (<= 30 (- turn death-turn))
                                    (not montype)
                                    (and (undead? monster)
                                         (.contains (:name montype)
                                                    (:name corpse-type)))))
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
