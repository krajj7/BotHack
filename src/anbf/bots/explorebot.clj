(ns anbf.bots.explorebot
  "a dumb level-exploring bot"
  (:require [clojure.tools.logging :as log]
            [anbf.handlers :refer :all]
            [anbf.player :refer :all]
            [anbf.pathing :refer :all]
            [anbf.monster :refer :all]
            [anbf.position :refer :all]
            [anbf.game :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.tile :refer :all]
            [anbf.delegator :refer :all]
            [anbf.actions :refer :all]))

(defn- fight []
  (reify ActionHandler
    (choose-action [_ {:keys [player] :as game}]
      (let [tgts (->> (-> game curlvl-monsters vals)
                         (filter #(and (hostile? %)
                                       (> 10 (- (:turn game) (:known %)))
                                       (> 10 (distance player %))))
                         (map position) (into #{}))]
        (if (seq tgts)
          (when-let [{:keys [step target]} (navigate game (comp tgts position)
                                                     :walk :adjacent)]
            (log/debug "targetting enemy at" target)
            (or step
                (if (blind? player)
                  (->Attack (towards player target))
                  (->Move (towards player target))))))))))

(defn- dead-end? [level tile]
  (and (walkable? tile)
       (not (:dug tile))
       (not (in-maze-corridor? level tile))
       (> 2 (count (remove #(or (#{:rock :wall} (:feature %))
                                (diagonal? tile %))
                           (neighbors level tile))))))

(defn- search-dead-end
  [game num-search]
  (let [tile (at-player game)]
    (when (and (= :main (branch-key game))
               (dead-end? (curlvl game) tile)
               (< (:searched tile) num-search))
      (log/debug "searching dead end")
      (->Search))))

(defn- explorable-tile? [level tile]
  (or (not (:feature tile))
      (some (complement :seen) (neighbors level tile))))

(defn- unexplored-column
  "Look for a column of unexplored tiles on segments of the screen."
  [game level]
  (if (#{:mines :main} (branch-key game level))
    (first (remove (fn column-explored? [x]
                     (some :feature (for [y (range 2 19)]
                                      (at level x y))))
                   [17 40 63]))))

(defn- descend []
  (reify ActionHandler
    (choose-action [_ game]
      (if-let [path (navigate game #(= :stairs-down (:feature %)))]
        (or (:step path) (->Descend))
        (log/debug "cannot find downstairs")))))

(defn- found-minetown? [game]
  (some #((:tags %) :minetown) (-> game :dungeon :levels :mines vals)))

(defn- escape-mines []
  (reify ActionHandler
    (choose-action [_ game]
      (if (and (= :mines (branch-key game))
               (found-minetown? game))
        (if-let [path (navigate game #(= :stairs-up (:feature %)))]
          (or (:step path) (->Ascend))
          (log/debug "cannot find upstairs"))))))

(defn- pushable-through [level from to]
  (and (or (or (walkable? to) (#{:water :lava} (:feature to)))
           (and (not (boulder? to))
                (nil? (:feature to)))) ; try to push via unexplored tiles
       (or (straight (towards from to))
           ; TODO squeezing boulders?
           (not= :door-open (:feature from))
           (not= :door-open (:feature to)))))

(defn- pushable-from [level pos]
  ; TODO soko
  (seq (filter #(if (boulder? %)
                  (let [dir (towards pos %)
                        dest (some->> (in-direction % dir) (at level))]
                    (and dest
                         (not (monster-at level dest))
                         ; TODO boulders in doors don't work reliably
                         (pushable-through level % dest))))
               (neighbors level pos))))

; TODO check if it makes sense, the boulder might not block
(defn- push-boulders [{:keys [player] :as game} level]
  (if-let [path (navigate game #(pushable-from level %))]
    (or (:step path)
        (->Move (towards player (first (pushable-from level player)))))
    (log/debug "no boulders to push")))

(defn- recheck-dead-ends [{:keys [player] :as game} level howmuch]
  (if (= :main (branch-key game))
    (if-let [p (navigate game #(and (dead-end? level %)
                                    (< (searched level %) howmuch)))]
      (or (:step p) (->Search)))))

(defn- searchable-position? [pos]
  (and (< 2 (:y pos) 20)
       (< 1 (:x pos) 78)))

(defn- search-walls [game level howmuch]
  (if-let [p (navigate game (fn searchable? [{:keys [feature] :as tile}]
                              (and (= :wall feature)
                                   (searchable-position? tile)
                                   (not (shop? tile))
                                   (< (:searched tile) howmuch)
                                   (->> (neighbors level tile)
                                        (remove :seen) count
                                        (< 1)))) :adjacent)]
    (or (:step p) (->Search))))

(defn- search-corridors [game level howmuch]
  (if-let [p (navigate game (fn searchable? [{:keys [feature] :as tile}]
                              (and (= :corridor feature)
                                   (searchable-position? tile)
                                   (< (searched level tile) howmuch))))]
    (or (:step p) (->Search))))

(defn- searchable-extremity [level y xs howmuch]
  (if-let [tile (->> xs (map #(at level % y)) (filter walkable?) first)]
    (if (and (= :floor (:feature tile))
             (< (:searched tile) howmuch)
             (not= \- (:glyph (at level (update-in tile [:x] dec))))
             (not= \- (:glyph (at level (update-in tile [:x] inc))))
             (not (shop? tile)))
      tile)))

(defn- unsearched-extremities
  "Returns a set of tiles that are facing a large blank vertical space on the map – good candidates for searching."
  [game level howmuch]
  (if-let [col (unexplored-column game level)]
    (as-> #{} res
      (into res (for [y (range 1 21)]
                  (searchable-extremity level y (range col 80) howmuch)))
      (into res (for [y (range 1 21)]
                  (searchable-extremity level y (range col -1 -1) howmuch)))
      (disj res nil))))

(defn- search-extremities [game level howmuch]
  (if (= :main (branch-key game))
    (if-let [goals (unsearched-extremities game level howmuch)]
      (if-let [p (navigate game goals)]
        (or (:step p) (->Search))))))

(defn- search
  ([] (search 10))
  ([max-iter]
   (reify ActionHandler
     (choose-action [_ game]
       (let [level (curlvl game)]
         (loop [mul 1]
           (or (log/debug "search iteration" mul)
               (if (= 1 mul) (push-boulders game level))
               (recheck-dead-ends game level (* mul 30))
               (search-extremities game level (* mul 20))
               (if (> mul 1) (search-corridors game level (* mul 5)))
               (search-walls game level (* mul 15))
               (if (> mul (dec max-iter))
                 (log/debug "stuck :-(")
                 (recur (inc mul))))))))))

(defn- explore []
  (reify ActionHandler
    (choose-action [_ game]
      (let [level (curlvl game)]
        (or (search-dead-end game 15)
            (when-let [path (navigate game (partial explorable-tile? level))]
              (log/debug "chose exploration target" (at level (:target path)))
              (:step path))
            (when (unexplored-column game level)
              (log/debug "level not explored enough, searching")
              (choose-action (search 1) game))
            (log/debug "nothing to explore"))))))

(defn- pray-for-food []
  (reify ActionHandler
    (choose-action [_ game]
      (if (weak? (:player game))
        (->Pray)))))

(defn- handle-impairment []
  (reify ActionHandler
    (choose-action [_ {:keys [player] :as game}]
      (or (when (or (impaired? player) (:leg-hurt player))
            (log/debug "waiting out imparment")
            (->Wait))))))

(defn init [anbf]
  (-> anbf
      (register-handler (reify ChooseCharacterHandler
                          (choose-character [this]
                            (deregister-handler anbf this)
                            "nsm"))) ; choose samurai
      (register-handler (reify ReallyAttackHandler
                          (really-attack [_ _] false)))
      (register-handler -10 (pray-for-food))
      (register-handler -5 (fight))
      (register-handler -3 (handle-impairment))
      (register-handler 2 (explore))
      (register-handler 4 (descend))
      (register-handler 6 (escape-mines))
      (register-handler 9 (search))))
