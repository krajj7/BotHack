(ns anbf.bots.explorebot
  "a dungeon-exploring bot"
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

(defn- hostile-threats [{:keys [player] :as game}]
  (->> (-> game curlvl-monsters vals)
       (filter #(and (hostile? %)
                     (if (blind? player)
                       (adjacent? player %)
                       (and (> 10 (- (:turn game) (:known %)))
                            (> 10 (distance player %)))))) ; TODO should be navigation distance (maybe pre-compute floodfill reachable)
       (into #{})))

(defn- fight [{:keys [player] :as game}]
  (if (:engulfed player)
    (->Move :E)
    (let [tgts (hostile-threats game)]
      (when-let [{:keys [step target]} (navigate game tgts :explored
                                                 :walk :adjacent)]
        (log/debug "targetting enemy at" target)
        (or step (if (blind? player)
                   (->Attack (towards player target))
                   (->Move (towards player target))))))))

(defn- pray-for-food [game]
  (if (weak? (:player game))
    (->Pray)))

(defn- handle-impairment [{:keys [player]}]
  (or (when (or (impaired? player) (:leg-hurt player))
        (log/debug "waiting out imparment")
        (->Wait))))

(defn progress [game]
  (or (visit game :quest)
      (visit game :sokoban)
      (visit game :mines :minetown)
      (explore game :main "Dlvl:2")
      (visit game :mines :end)
      ;(explore game :mines)
      (visit game :main :medusa)
      (visit game :quest :end)
      ;(seek-level game :main "Dlvl:1")
      (log/debug "progress end")))

(defn init [anbf]
  (-> anbf
      (register-handler (reify ChooseCharacterHandler
                          (choose-character [this]
                            (deregister-handler anbf this)
                            "nsm"))) ; choose samurai
      (register-handler (reify ReallyAttackHandler
                          (really-attack [_ _] false)))
      (register-handler -10 (reify ActionHandler
                              (choose-action [_ game]
                                (pray-for-food game))))
      (register-handler -5 (reify ActionHandler
                             (choose-action [_ game]
                               (fight game))))
      (register-handler -3 (reify ActionHandler
                             (choose-action [_ game]
                               (handle-impairment game))))
      (register-handler 5 (reify ActionHandler
                            (choose-action [_ game]
                              (progress game))))))
