(ns bothack.tracker
  "Tracking and pairing monsters frame-to-frame and their deaths and corpses turn-by-turn"
  (:require [bothack.position :refer :all]
            [bothack.dungeon :refer :all]
            [bothack.delegator :refer :all]
            [bothack.fov :refer :all]
            [bothack.tile :refer :all]
            [bothack.item :refer :all]
            [bothack.itemtype :refer :all]
            [bothack.monster :refer :all]
            [bothack.handlers :refer :all]
            [bothack.level :refer :all]
            [bothack.player :refer :all]
            [bothack.util :refer :all]
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
                  n (filter #(and (= (typename m) (typename %))
                                  (not (rodney? %))
                                  (:remembered %))
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
                      ((some-fn stairs? boulder? fountain? altar? :new-items)
                       tile))))
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
                          (dissoc old-monsters (position (:player new-game)))
                          (:monsters (curlvl new-game)))
           dist 0]
      (if (and (> 4 dist) (seq old-monsters))
        (if-let [[p m] (first new-monsters)]
          (if-let [[[cp cm] & more]
                   (seq (filter (fn candidate? [[_ n]]
                                  (or (and (= \5 (:glyph m))
                                           (covetous? n)
                                           (zero? (distance m n)))
                                      (and (= (:glyph m) (:glyph n))
                                           (= (:color m) (:color n))
                                           (= (:friendly m) (:friendly n))
                                           (= dist (distance m n)))))
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

(defn death-tracker [bh]
  (reify
    ToplineMessageHandler
    (message [_ msg]
      (if-let [old-game (:last-state @(:game bh))]
        (if (not (hallu? (:player old-game)))
          (condp re-first-group msg
            #"You (kill|destroy) [^.!]*[.!]"
            (update-before-action bh mark-kill old-game)
            ;#" is (killed|destroyed)" ...
            nil))))))

(defn- only-fresh-deaths? [tile corpse-type turn]
  (let [relevant-deaths (remove (fn [[death-turn {montype :type :as monster}]]
                                  (and (< 500 (- turn death-turn))
                                       (:name montype)
                                       (not= corpse-type montype)))
                                (:deaths tile))
        unsafe-deaths (filter (fn [[death-turn {montype :type :as monster}]]
                                (or (<= 30 (- turn death-turn))
                                    (not montype)
                                    (and (undead? monster)
                                         (not= "wraith" (:name montype))
                                         (.contains (:name montype)
                                                    (:name corpse-type)))))
                              relevant-deaths)
        safe-deaths (filter (fn [[death-turn {montype :type :as monster}]]
                              (and (> 30 (- turn death-turn))
                                   (= corpse-type montype)))
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
