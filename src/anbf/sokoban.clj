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
            [anbf.tile :refer :all]
            [anbf.util :refer :all]))

; XXX paths must not overlap during one boulder
(def ^:private solutions
  "soko-tag => no. boulders => [[srcx srcy] [destx desty] ...]"
  {:soko-1a {10 [[40 9] [42 9] [42 9] [42 12] [42 15] [41 15] [41 16] [40 16]]
             9 [[43 16] [39 16]]
             8 [[41 13] [41 15] [42 16] [38 16]]
             7 [[40 14] [40 15] [41 16] [37 16]]
             6 [[40 12] [40 15] [41 16] [35 16] [34 17] [34 16]]
             5 [[42 12] [42 15]
                [43 16] [35 16] ; pred zatackou
                [34 17] [34 15]]
             4 [[42 7] [42 15] ; chodbou dolu uplne shora
                [43 16] [35 16]
                [34 17] [34 13] ; nahoru do druhe zatacky
                [33 12] [34 12]]
             3 [[33 9] [41 9] ; pred chodbou dolu
                [42 8] [42 15] ; chodbou dolu
                [43 16] [35 16] ; pred zatackou
                [34 17] [34 13] ; nahoru do druhe zatacky
                [33 12] [35 12]]
             2 [[34 7] [34 8]
                [33 9] [41 9] ; pred chodbou dolu
                [42 8] [42 15] ; chodbou dolu
                [43 16] [35 16] ; pred zatackou
                [34 17] [34 13] ; nahoru do druhe zatacky
                [33 12] [36 12]]}
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
                [33 15] [33 9]]}})

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
  (apply (fnil <= 0 -1 -2) lst (map :walked tiles)))

(def ^:private soko-items
  ; TODO autoid soko prize
  {:soko-1a {{:x 34 :y 17} "scroll of earth"
             {:x 35 :y 17} "scroll of earth"}
   :soko-1b {{:x 33 :y 15} "scroll of earth"
             {:x 34 :y 15} "scroll of earth"}})

; TODO mimics
; TODO stuck I's
(defn do-soko [{:keys [player last-fill] :as game}]
  (when-let [[tag s] (and (= :sokoban (branch-key game))
                          (some (partial find solutions) (curlvl-tags game)))]
    (let [level (curlvl game)
          boulders (count (filter #(and (boulder? %) (not (monster-at level %)))
                                  (tile-seq level)))]
      (with-reason "solving soko" tag
        (if-let [[[src dest] & _] (->> (s boulders) (partition 2)
                                       (drop-while (fn [[[sx sy] [dx dy]]]
                                                     (walked-in-order?
                                                       last-fill
                                                       (at level sx sy)
                                                       (at level dx dy)))) seq)]
          (let [moves (map (partial at level) (moves-for src dest))
                [msrc mdest] (->> (interleave moves (drop 1 moves))
                                  (partition 2)
                                  (drop-while (fn [[src dest]]
                                                (walked-in-order? last-fill
                                                                  src dest)))
                                  first)]
            (log/debug ">>>" (map position moves))
            (if (= (position msrc) (position player))
              (with-reason "push" (->Move (towards msrc mdest)))
              (with-reason "boulder start" (:step (navigate game msrc
                                                            #{:no-autonav})))))
          (log/debug "soko no more moves"))))))

(defn soko-handler [{:keys [game] :as anbf}]
  (reify
    FoundItemsHandler
    (found-items [_ found]
      (let [tile (at-player @game)]
        (if-let [items (and (= (:turn @game) (:first-walked tile))
                            (some soko-items (curlvl-tags game)))]
          (if-let [id (items (position (:player @game)))]
            (if-let [matching (->> (:items tile)
                                   (filter #(= (item-type (name->item id))
                                               (item-type %)))
                                   (map :name) set seq)]
              (if (less-than? 2 matching)
                (swap! game add-discovery (first matching) id)))))))
    ToplineMessageHandler
    (message [_ msg]
      (if (re-seq #"The boulder fills a pit|You hear the boulder fall" msg)
        (swap! game #(assoc % :last-fill (inc (:turn %))))))))
