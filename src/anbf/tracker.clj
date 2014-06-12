; tracking and pairing monsters frame-to-frame

(ns anbf.tracker
  (:require [anbf.position :refer :all]
            [anbf.dungeon :refer :all]
            [clojure.tools.logging :as log]))

(defn- track-transfer [game old-monster monster]
  (log/debug "transfer:" \newline old-monster "to" \newline monster)
  (update-curlvl-monster game monster into ; TODO type
                         (select-keys old-monster [:peaceful :cancelled])))

(defn track-monsters
  "Try to transfer monster properties from the old game snapshot to the new, even if the monsters moved slightly."
  [new-game old-game]
  (if (not= (-> old-game :dungeon :dlvl)
            (-> new-game :dungeon :dlvl))
    new-game ; TODO track stair followers?
    (loop [res new-game
           new-monsters (curlvl-monsters new-game)
           old-monsters (curlvl-monsters old-game)
           dist 0]
      (if (> 3 dist)
        (if-let [[p m] (first new-monsters)]
          (if-let [candidates (seq (->> (vals old-monsters)
                                        (filter
                                          (fn candidate? [n]
                                            (and (= (:glyph m) (:glyph n))
                                                 (= (:color m) (:color n))
                                                 (= dist (distance m n)))))))]
            (if (next candidates) ; ignore ambiguous cases
              (recur res (dissoc new-monsters p) old-monsters dist)
              (recur (track-transfer res (first candidates) m)
                     (dissoc new-monsters p)
                     (dissoc old-monsters (position (first candidates)))
                     dist))
            (recur res (dissoc new-monsters p) old-monsters dist))
          (recur res new-monsters old-monsters (inc dist)))
        res)))) ; TODO remember/unremember unignored old/new
