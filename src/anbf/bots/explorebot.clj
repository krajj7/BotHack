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
            (or step (->Move (towards player target)))))))))

(defn- dead-end? [level tile]
  (and (walkable? tile)
       (not (:dug tile))
       (not (in-corridor? level tile))
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

(defn- explore []
  (reify ActionHandler
    (choose-action [_ game]
      (let [level (curlvl game)]
        (or (search-dead-end game 15)
            (when-let [path (navigate game #(explorable-tile? level %))]
              (log/debug "chose exploration target" (:target path))
              (:step path))
            (log/debug "nothing to explore"))))))

(defn- descend-main-branch []
  (reify ActionHandler
    (choose-action [_ game]
      (if-let [path (navigate game #(and (= :stairs-down (:feature %))
                                         (not= :mines (branch-key game %))))]
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
                        dest (at level (in-direction % dir))]
                    (and (not (monster-at level dest))
                         ; TODO boulders in doors don't work reliably
                         (pushable-through level % dest))))
               (neighbors level pos))))

; TODO check if it makes sense, the boulder might not block
(defn- push-boulders []
  (reify ActionHandler
    (choose-action [_ {:keys [player] :as game}]
      (let [level (curlvl game)]
        (if-let [path (navigate game #(pushable-from level %))]
          (or (:step path)
              (->Move (towards player
                               (first (pushable-from level player)))))
          (log/debug "no boulders to push"))))))

(defn- search []
  (reify ActionHandler
    (choose-action [_ game]
      (let [level (curlvl game)]
        (loop [mul 1]
          (or (log/debug "searching for stairs" mul)
              (if-let [p (navigate game #(and (dead-end? level %)
                                              (< (searched level %)
                                                 (* mul 30))))]
                (or (:step p) (->Search)))
              (log/debug "searching walls" mul)
              (if-let [p (navigate game (fn searchable-wall? [tile]
                                          (and (= :wall (:feature tile))
                                               (< 2 (:y tile) 21)
                                               (< 1 (:x tile) 79)
                                               (not (shop? tile))
                                               (< (:searched tile) (* mul 10))
                                               (->> (neighbors level tile)
                                                    (remove :seen) count
                                                    (< 1)))) :adjacent)]
                (or (:step p) (->Search)))
              (recur (inc mul))))))))

(defn- pray-for-food []
  (reify ActionHandler
    (choose-action [_ game]
      (if (weak? (:player game))
        (->Pray)))))

(defn- handle-impairment []
  (reify ActionHandler
    (choose-action [_ {:keys [player] :as game}]
      (if (:trapped player)
        (do (log/debug "trying to untrap self")
            (fidget game (curlvl game)))
        (when (or (impaired? player) (:leg-hurt player))
          (log/debug "waiting out imparment")
          (->Search))))))

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
      (register-handler 4 (descend-main-branch))
      (register-handler 6 (escape-mines))
      (register-handler 8 (push-boulders))
      (register-handler 9 (search))))
