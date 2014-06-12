; tracking and pairing monsters frame-to-frame

(ns anbf.tracker
  (:require [anbf.position :refer :all]
            [anbf.dungeon :refer :all]
            [clojure.tools.logging :as log]))

(defn- transfer-pair [game [old-monster monster]]
  (log/debug "transfer:" \newline old-monster "to" \newline monster)
  (update-curlvl-monster game monster into ; TODO type
                         (select-keys old-monster [:peaceful :cancelled])))

(defn- transfer-unpaired [game unpaired]
  (log/debug "unpaired" unpaired) ; TODO
  game
  #_(if (not (in-los? (at-curlvl game unpaired)))
    (update-curlvl-monster game unpaired (constantly unpaired))
    game))

(defn track-monsters
  "Try to transfer monster properties greedily from the old game snapshot to the new, even if the monsters moved slightly."
  [new-game old-game]
  (if (not= (-> old-game :dungeon :dlvl)
            (-> new-game :dungeon :dlvl))
    new-game ; TODO track stair followers?
    (loop [pairs {}
           new-monsters (curlvl-monsters new-game)
           old-monsters (curlvl-monsters old-game)
           dist 0]
      (if (and (> 3 dist) (seq old-monsters))
        (if-let [[p m] (first new-monsters)]
          (if-let [[[cp cm] & more] (seq (filter
                                           (fn candidate? [[_ n]]
                                             (and (= (:glyph m) (:glyph n))
                                                  (= (:color m) (:color n))
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
          (reduce transfer-pair res (vals pairs))
          (reduce transfer-unpaired res old-monsters))))))
