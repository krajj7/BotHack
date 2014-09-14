(ns anbf.bots.explorebot
  "a dungeon-exploring bot"
  (:require [clojure.tools.logging :as log]
            [flatland.ordered.set :refer [ordered-set]]
            [anbf.itemid :refer :all]
            [anbf.handlers :refer :all]
            [anbf.player :refer :all]
            [anbf.pathing :refer :all]
            [anbf.monster :refer :all]
            [anbf.position :refer :all]
            [anbf.game :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.tile :refer :all]
            [anbf.delegator :refer :all]
            [anbf.util :refer :all]
            [anbf.actions :refer :all]))

(def hostile-dist-thresh 10)

(defn- hostile-threats [{:keys [player] :as game}]
  (->> game curlvl-monsters vals
       (filter #(and (hostile? %)
                     (if (or (blind? player) (:hallu (:state player)))
                       (adjacent? player %)
                       (and (> 10 (- (:turn game) (:known %)))
                            (> hostile-dist-thresh (distance player %))))))
       (into #{})))

(defn- pray-for-food [game]
  (if (weak? (:player game))
    (with-reason "praying for food" ->Pray)))

(defn- handle-impairment [{:keys [player] :as game}]
  (or (if (:lycantrophy player)
        (with-reason "curing lycantrophy" ->Pray))
      (when-let [[slot _] (and (unihorn-recoverable? game)
                               (have-unihorn game))]
        (with-reason "applying unihorn to recover" (->Apply slot)))
      (when (or (impaired? player) (:leg-hurt player))
        (with-reason "waiting out impairment" ->Wait))))

(defn progress [game]
  (or (explore game :main "Dlvl:1")
      (explore game :mines :minetown)
      ;(visit game :sokoban :end)
      (visit game :main :medusa)
      ;(visit game :quest :end)
      ;(visit game :mines :minetown)
      ;(visit game :mines :end)
      ;(visit game :quest :end)
      ;(explore game :mines)
      ;(explore game :main "Dlvl:20")
      ;(seek-level game :main "Dlvl:1")
      (log/debug "progress end")))

(def desired-weapons
  (ordered-set "Grayswandir" "Excalibur" "katana" "long sword"))

(def desired-items
  [(ordered-set "pick-axe" "dwarvish mattock")
   (ordered-set "skeleton key" "lock pick" "credit card")
   desired-weapons
   #{"blindfold" "towel"}
   #{"unicorn horn"}
   #{"Candelabrum of Invocation"}
   #{"Bell of Opening"}
   #{"Book of the Dead"}
   #{"Amulet of Yendor"}])

(defn entering-shop? [game]
  (some->> (nth (:last-path game) 0) (at-curlvl game) shop?))

(defn currently-desired
  "Returns the set of item names that the bot currently wants.
  Assumes the bot has at most 1 item of each category."
  [game]
  (loop [cs (if (entering-shop? game)
              (rest desired-items) ; don't pick that pickaxe back up
              desired-items)
         res #{}]
    (if-let [c (first cs)]
      (if-let [[slot i] (have game c)]
        (recur (rest cs)
               (into res (take-while (partial not= (item-name game i)) c)))
        (recur (rest cs) (into res c)))
      res)))

(defn can-take? [item]
  (not (:cost item)))

(defn consider-items [game]
  (let [desired (currently-desired game)
        to-take? #(and (desired (item-name game %)) (can-take? %))]
    (or (if-let [to-get (seq (for [item (:items (at-player game))
                                   :let [i (item-name game item)]
                                   :when (to-take? item)]
                               (:label item)))]
          (->PickUp (into [] to-get))
          (log/debug "no desired items here"))
        (when (pos? (exploration-index game)) ; all explored
          (log/debug "considering all items on level")
          (when-let [{:keys [step target]}
                     (navigate game #(some to-take? (:items %)))]
            (log/debug "going to get item at" target)
            step)))))

(defn- wield-weapon [{:keys [player] :as game}]
  (if-let [[slot weapon] (some (partial have game) desired-weapons)]
    ; TODO can-wield?
    (when-not (:wielded weapon)
      (log/debug "wielding better weapon -" (:label weapon))
      (->Wield slot))))

(defn- maybe-wield-weapon [{:keys [last-path] :as game}]
  (let [level (curlvl game)
        step (some->> (nth last-path 0) (at level))]
    (if (and (not-any? #(not (walkable? (at level %))) last-path)
             step
             (not (:dug step)))
      (wield-weapon game))))

(defn- fight [{:keys [player] :as game}]
  (if (:engulfed player)
    (or (wield-weapon game)
        (->Move :E))
    (let [tgts (hostile-threats game)]
      (when-let [{:keys [step target]} (navigate game tgts
                                          {:walking true :adjacent true
                                           :max-steps hostile-dist-thresh})]
        (log/debug "targetting enemy at" target)
        (or (wield-weapon game)
            step
            (if (blind? player)
              (->Attack (towards player target))
              (->Move (towards player target))))))))

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
      (register-handler 1 (reify ActionHandler
                             (choose-action [_ game]
                               (maybe-wield-weapon game))))
      (register-handler 2 (reify ActionHandler
                             (choose-action [_ game]
                               (consider-items game))))
      (register-handler 5 (reify ActionHandler
                            (choose-action [_ game]
                              (progress game))))))
