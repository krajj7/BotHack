(ns anbf.sokoban
  (:require [clojure.tools.logging :as log]
            [anbf.actions :refer :all]
            [anbf.delegator :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.itemid :refer :all]
            [anbf.itemtype :refer :all]
            [anbf.level :refer :all]
            [anbf.pathing :refer :all]
            [anbf.position :refer :all]
            [anbf.player :refer :all]
            [anbf.tile :refer :all]
            [anbf.util :refer :all]))

; XXX boulders paths must not overlap within one boulder count
(def ^:private solutions
  "soko-tag => no. boulders => [[srcx srcy] [destx desty] ...]"
  {:soko-1a {10 [[40 9] [42 9] [42 9] [42 12] [42 15] [41 15] [41 16] [40 16]]
             9 [[43 16] [39 16]]
             8 [[41 13] [41 15] [42 16] [38 16]]
             7 [[40 14] [40 15] [41 16] [37 16]]
             6 [[40 12] [40 15] [41 16] [35 16] [34 17] [34 16]]
             5 [[42 12] [42 15] [43 16] [35 16] [34 17] [34 15]]
             4 [[42 7] [42 15] [43 16] [35 16] [34 17] [34 13] [33 12] [34 12]]
             3 [[33 9] [41 9] [42 8] [42 15] [43 16] [35 16]
                [34 17] [34 13] [33 12] [35 12]]
             2 [[34 7] [34 8] [33 9] [41 9] [42 8] [42 15]
                [43 16] [35 16] [34 17] [34 13] [33 12] [36 12]]}
   :soko-1b {12 [[37 7] [37 8] [37 8] [41 8] [41 10] [41 12]
                 [40 15] [40 14] [40 14] [39 14]]
             11 [[42 15] [42 14] [42 14] [38 14]]
             10 [[42 12] [42 13] [43 14] [37 14]]
             9 [[41 12] [41 13] [42 14] [36 14]]
             8 [[43 7] [43 8] [43 8] [38 8] [40 12] [40 13] [41 14] [34 14]
                [33 15] [33 14]]
             7 [[42 8] [42 9] [43 10] [42 10] [41 9] [41 13] [42 14] [36 14]
                [41 14] [34 14] [33 15] [33 13]]
             6 [[43 8] [43 9] [44 10] [42 10] [41 9] [41 13] [42 14] [36 14]
                [41 14] [34 14] [33 15] [33 12]]
             5 [[45 9] [42 9] [41 8] [41 13] [42 14] [36 14]
                [41 14] [34 14] [33 15] [33 11]]
             4 [[36 8] [41 8] [42 7] [42 9] [43 10] [42 10] [41 9] [41 13]
                [42 14] [36 14] [41 14] [34 14] [33 15] [33 9]]
             3 [[37 10] [37 9] [36 8] [41 8] [42 7] [42 9] [43 10] [42 10]
                [41 9] [41 13] [42 14] [36 14] [41 14] [34 14]
                [33 15] [33 9]]}
   :soko-2a {16 [[34 8] [34 10] [34 8] [34 10]
                 [36 10] [35 10] [28 17] [31 17] [32 18] [32 17]
                 [35 16] [37 16]]
             15 [[33 15] [33 16] [34 17] [29 17]
                 [37 15] [36 15] [35 12] [35 11]
                 [35 11] [33 11] [33 13] [33 14]
                 [34 15] [35 15]
                 [36 14] [36 15] [35 16] [38 16]]
             14 [[31 14] [31 17] [33 14] [33 16]
                 [32 17] [34 17] [35 18] [35 16] [34 15] [35 15]
                 [36 14] [36 15] [35 16] [39 16]]
             13 [[27 17] [34 17]
                 [35 18] [35 16] [34 15] [35 15]
                 [31 16] [32 16]
                 [36 14] [36 15] [35 16] [40 16]]
             12 [[33 15] [33 16] [32 17] [34 17]
                 [35 18] [35 16] [34 15] [35 15]
                 [36 14] [36 15] [35 16] [41 16]]
             11 [[32 12] [32 13] [31 14] [32 14]
                 [33 13] [33 16] [32 17] [34 17]
                 [35 18] [35 16] [34 15] [35 15]
                 [36 14] [36 15] [35 16] [42 16]]
             10 [[28 14] [32 14] [33 13] [33 16] [32 17] [34 17]
                 [35 18] [35 16] [34 15] [35 15]
                 [36 14] [36 15] [35 16] [43 16]]
             9 [[27 12] [29 12] [29 11] [30 11]
                [32 10] [32 13] [31 14] [32 14]
                [33 13] [33 16] [32 17] [34 17]
                [35 18] [35 16] [34 15] [35 15]
                [36 14] [36 15] [35 16] [44 16]]
             8 [[30 11] [31 11] [32 10] [32 13]
                [31 14] [32 14] [33 13] [33 16] [32 17] [34 17]
                [35 18] [35 16] [34 15] [35 15]
                [36 14] [36 15] [35 16] [45 16]]
             7 [[27 11] [31 11] [32 10] [32 13]
                [31 14] [32 14] [33 13] [33 16] [32 17] [34 17]
                [35 18] [35 16] [34 15] [35 15]
                [36 14] [36 15] [35 16] [46 16]]
             6 [[28 8] [28 10] [27 11] [31 11] [32 10] [32 13]
                [31 14] [32 14] [33 13] [33 16] [32 17] [34 17]
                [35 18] [35 16] [34 15] [35 15]
                [36 14] [36 15] [35 16] [46 16]]
             5 [[34 9] [34 10] [36 10] [35 10] [35 11] [33 11]
                [32 10] [32 13] [31 14] [32 14] [33 13] [33 16] [32 17] [34 17]
                [35 18] [35 16] [34 15] [35 15]
                [36 14] [36 15] [35 16] [47 16]]}
   :soko-2b {20 [[31 9] [28 9]
                 [26 14] [26 9] [25 9] [26 9]
                 [26 14] [31 14] [32 15] [33 15] [33 16] [35 16]]
             19 [[26 9] [30 9] [26 16] [26 10]
                 [33 15] [33 14] [34 14] [34 15] [33 16] [36 16]]
             18 [[31 16] [37 16]]
             17 [[34 12] [34 15] [33 16] [38 16]]
             16 [[33 12] [33 15] [32 16] [39 16]]
             15 [[31 14] [32 14] [33 13] [33 15] [32 16] [40 16]]
             14 [[32 12] [32 15] [31 16] [41 16]]
             13 [[28 16] [28 15] [27 14] [31 14] [32 13] [32 15]
                 [31 16] [42 16]]
             12 [[27 16] [27 15] [26 14] [31 14]
                 [32 13] [32 15] [31 16] [43 16]]
             11 [[28 13] [27 13] [26 12] [26 13]
                 [25 14] [31 14] [32 13] [32 15] [31 16] [44 16]]
             10 [[25 9] [26 9] [26 7] [26 13]
                 [25 14] [31 14] [32 13] [32 15] [31 16] [45 16]]
             9 [[28 9] [27 9] [26 8] [26 13]
                [25 14] [31 14] [32 13] [32 15] [31 16] [46 16]]
             8 [[27 7] [27 8]
                [28 9] [27 9] [26 8] [26 13]
                [25 14] [31 14] [32 13] [32 15] [31 16] [47 16]]
             7 [[32 9] [27 9] [26 8] [26 13]
                [25 14] [31 14] [32 13] [32 15] [31 16] [48 16]]
             6 [[30 16] [30 15] [30 14] [30 13]
                [26 14] [31 14] [32 13] [32 15] [31 16] [49 16]]}
   :soko-3a {16 [[36 16] [34 16] [34 17] [36 17]]
             15 [[36 14] [36 16] [35 17] [37 17]]
             14 [[34 12] [34 15] [33 17] [33 16] [33 16] [35 16]
                 [36 15] [36 16] [35 17] [38 17]]
             13 [[32 13] [33 13] [34 12] [34 16]
                 [32 15] [33 15] [33 17] [39 17]]
             12 [[36 13] [38 13] [34 14] [34 16] [33 17] [40 17]]
             11 [[40 13] [35 13] [34 12] [34 16] [33 17] [41 17]]
             10 [[39 13] [39 11] [36 11] [36 16] [35 17] [42 17]]
             9 [[34 11] [35 11] [36 10] [36 16] [35 17] [43 17]]
             8 [[33 9] [33 12] [32 13] [33 13] [34 12] [34 16] [33 17] [44 17]]
             7 [[31 10] [32 10]
                [33 9] [33 12] [32 13] [33 13] [34 12] [34 16] [33 17] [45 17]]
             6 [[33 8] [35 8] [35 8] [35 10] [34 11] [35 11]
                [36 10] [36 16] [35 17] [46 17]]}
   :soko-3b {13 [[34 15] [33 15] [35 10] [35 9] [38 7] [38 9] [38 9] [35 9]
                 [36 11] [36 14] [35 15] [37 15]]
             12 [[31 15] [38 15]]
             11 [[31 13] [32 13] [32 13] [32 14]
                 [31 15] [39 15]]
             10 [[33 12] [33 14] [32 15] [40 15]]
             9 [[32 7] [32 14] [31 15] [41 15]]
             8 [[34 8] [33 8] [32 7] [32 14] [31 15] [42 15]]
             7 [[34 10] [34 9] [33 8] [33 8] [32 7] [32 14] [31 15] [43 15]]
             6 [[38 9] [35 9]
                [34 10] [34 9] [33 8] [33 8] [32 7] [32 14] [31 15] [44 15]]
             ;...
             }
   :soko-4a {18 [[31 14] [30 14] [32 15] [35 15] [28 18] [31 18]
                 [30 16] [32 16] [33 15] [33 18] [34 19] [30 19]
                 ]}
   :soko-4b {20 [[32 18] [35 18] [36 19] [36 15] [37 15] [37 14]
                 [38 14] [38 12] [37 12] [37 11] [37 11] [34 11]
                 [32 12] [30 12] [30 12] [30 13] [31 12] [31 11]
                 [30 12] [30 11] [28 11] [28 10] [28 8] [28 9]
                 [30 9] [30 6] [29 5] [30 5]]
             19 [[31 9] [31 11] [30 11] [30 6] [29 5] [31 5]]
             18 [[31 13] [31 11] [28 11] [28 10] [27 9] [29 9]
                 [30 10] [30 6] [29 5] [32 5]]
             17 [[28 10] [29 10] [30 11] [30 6] [29 5] [33 5]]
             16 [[30 15] [30 6] [29 5] [34 5]]
             15 [[28 14] [29 14] [30 15] [30 6] [29 5] [35 5]]
             14 [[28 13] [29 13] [30 14] [30 6] [29 5] [36 5]]
             ;...
             }})

(def initial-boulders
  {:soko-4b #{{:y 8 :x 30} {:y 9 :x 37} {:y 10 :x 28} {:y 10 :x 29} {:y 10 :x 38} {:y 11 :x 30} {:y 11 :x 31} {:y 11 :x 36} {:y 11 :x 37} {:y 12 :x 28} {:y 12 :x 31} {:y 12 :x 36} {:y 13 :x 29} {:y 13 :x 30} {:y 13 :x 38} {:y 14 :x 29} {:y 14 :x 31} {:y 14 :x 37} {:y 16 :x 32} {:y 18 :x 33}}
   :soko-4a #{{:y 9 :x 29} {:y 9 :x 31} {:y 9 :x 33} {:y 9 :x 35} {:y 9 :x 37} {:y 11 :x 30} {:y 11 :x 32} {:y 11 :x 35} {:y 11 :x 37} {:y 12 :x 30} {:y 14 :x 30} {:y 14 :x 32} {:y 14 :x 34} {:y 15 :x 33} {:y 16 :x 29} {:y 16 :x 31} {:y 16 :x 35} {:y 18 :x 29}}})

(defn- moves-for [src dest]
  {:pre [(not (and (not= (firstv src) (firstv dest))
                   (not= (secondv src) (secondv dest))))]}
  (let [idx (if (not= (firstv src) (firstv dest)) 0 1)
        update-fn (if (> (dest idx) (src idx)) inc dec)
        succesor #(update % idx update-fn)]
    (for [[x y] (concat (take-while (partial not= dest)
                                    (iterate succesor src))
                        [dest])]
      (position x y))))

(defn- walked-in-order? [lst & tiles]
  (apply (fnil <= 0 -1 -2) lst (map :pushed* tiles)))

(def ^:private soko-items
  ; TODO autoid soko prize
  {:soko-1a {{:x 34 :y 17} "scroll of earth"
             {:x 35 :y 17} "scroll of earth"}
   :soko-1b {{:x 33 :y 15} "scroll of earth"
             {:x 34 :y 15} "scroll of earth"}
   :soko-4a {{:x 42 :y 15} "bag of holding"
             {:x 42 :y 16} "bag of holding"
             {:x 42 :y 17} "bag of holding"}})

(defn do-soko [{:keys [player last-fill] :as game}]
  (when-let [[tag s] (and (= :sokoban (branch-key game))
                          (some (partial find solutions) (curlvl-tags game)))]
    (let [level (curlvl game)
          boulders (count (filter #(and (boulder? %) (not (monster-at level %)))
                                  (tile-seq level)))]
      (with-reason "solving soko" tag
        (if-let [[[src dest] & _] (->> (s boulders) (partition 2)
                                       (drop-while
                                         (fn [[[sx sy] [dx dy]]]
                                           (or (->> (position dx dy)
                                                    (towards (position sx sy))
                                                    (in-direction
                                                      level (position dx dy))
                                                    boulder?)
                                               (walked-in-order?
                                                 last-fill
                                                 (at level sx sy)
                                                 (at level dx dy))))) seq)]
          (let [moves (map (partial at level) (moves-for src dest))
                [msrc mdest] (->> (interleave moves (drop 1 moves))
                                  (partition 2)
                                  (drop-while (fn [[src dest]]
                                                (and (not (boulder? dest))
                                                     (walked-in-order?
                                                       last-fill src dest))))
                                  first)]
            (log/debug ">>>" (map position moves))
            (if (= (position msrc) (position player))
              (or (if-let [monster (->> (towards msrc mdest)
                                        (in-direction mdest)
                                        (monster-at game))]
                    (if (and (or (= \I (:glyph monster))
                                 (not (:remembered monster)))
                             (or (not= \I (:glyph monster))
                                 (= :move (typekw (:last-action game)))))
                      (with-reason "soko blocked by monster"
                        ->Search)))
                  (with-reason "push" (->Move (towards msrc mdest))))
              (with-reason "boulder start"
                (:step (navigate game msrc #{:no-autonav})))))
          (do ;(@#'anbf.main/p)  ; XXX DEBUG
              (log/debug "soko no more moves")))))))

(defn soko-handler [{:keys [game] :as anbf}]
  (reify
    AboutToChooseActionHandler
    (about-to-choose [_ {:keys [player last-state last-action turn*] :as game}]
      (if-let [dir (and (= :move (typekw last-action)) (:dir last-action))]
        (let [old-tile (at-curlvl last-state player)
              old-player (:player last-state)]
          (when (and (not (dizzy? old-player))
                     (boulder? old-tile)
                     (boulder? (in-direction (curlvl game) player dir))
                     (not (boulder? (->> (in-direction old-tile dir)
                                         (at-curlvl last-state)))))
            (swap! (:game anbf) update-at-player assoc :pushed* turn*)
            (swap! (:game anbf) update-at old-player assoc :pushed* turn*)
            (swap! (:game anbf) update-at player dissoc :pushed)
            (swap! (:game anbf) update-at (in-direction player dir)
                   assoc :pushed true)))))
    ActionChosenHandler
    (action-chosen [_ action]
      (if-let [bohname (and (:soko-4a (curlvl-tags @game))
                            (= :call (typekw action))
                            ((:soko-4a soko-items) (position (:player game)))
                            (:name action))]
        (swap! game add-discovery bohname "bag of holding")))
    FoundItemsHandler
    (found-items [_ found]
      (let [tile (at-player @game)]
        (if-let [items (and (= (:turn @game) (:first-walked tile))
                            (some soko-items (curlvl-tags @game)))]
          (if-let [id (items (position (:player @game)))]
            (if-let [matching (->> (:items tile)
                                   (filter #(= (item-type (name->item id))
                                               (item-type %)))
                                   (map :name) set seq)]
              (if (less-than? 2 matching)
                (swap! game add-discovery (first matching) id)))))))
    ToplineMessageHandler
    (message [_ msg]
      (if (re-seq boulder-plug-re msg)
        (swap! game #(assoc % :last-fill (inc (:turn* %))))))))
