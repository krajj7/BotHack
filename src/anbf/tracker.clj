(ns anbf.tracker
  "Tracking and pairing monsters frame-to-frame"
  (:require [anbf.position :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.fov :refer :all]
            [anbf.player :refer :all]
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

(defn- transfer-unpaired [game unpaired]
  ; TODO don't transfer if we would know monsters position with ESP
  ;(log/debug "unpaired" unpaired)
  (if-not (visible? game unpaired)
    (reset-curlvl-monster game (assoc unpaired :remembered true))
    game))

(defn track-monsters
  "Try to transfer monster properties greedily from the old game snapshot to the new, even if the monsters moved slightly."
  [new-game old-game]
  (if (or (not= (:dlvl old-game) (:dlvl new-game))
          (-> new-game :player :state :hallu))
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
