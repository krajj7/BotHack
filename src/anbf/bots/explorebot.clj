(ns anbf.bots.explorebot
  "a dungeon-exploring bot"
  (:require [clojure.tools.logging :as log]
            [flatland.ordered.set :refer [ordered-set]]
            [anbf.anbf :refer :all]
            [anbf.item :refer :all]
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
                     (or (adjacent? player %)
                         (and (> 10 (- (:turn game) (:known %)))
                              (> hostile-dist-thresh (distance player %))
                              (not (blind? player))
                              (not (hallu? player))
                              (not (digit? %))))))
       set))

(defn- pray-for-food [game]
  (if (and (weak? (:player game))
           (not (in-gehennom? game)))
    (with-reason "praying for food" ->Pray)))

(defn- enhance-handler [anbf]
  (reify ActionHandler
    (choose-action [_ game]
      (if (:can-enhance (:player game))
        (log/debug "TODO ->Enhance")
        #_(->Enhance)))
    ; TODO EnhanceHandler
    ))

(defn- handle-impairment [{:keys [player] :as game}]
  (or (if (:lycantrophy player)
        (if-not (in-gehennom? game)
          (with-reason "curing lycantrophy" ->Pray)))
      (when-let [[slot _] (and (unihorn-recoverable? game)
                               (have-unihorn game))]
        (with-reason "applying unihorn to recover" (->Apply slot)))
      (when (or (impaired? player) (:leg-hurt player))
        (with-reason "waiting out impairment" ->Wait))))

(defn progress [game]
  (or ;(explore game :mines :minetown)
      ;(explore game :mines)
      ;(visit game :quest)
      (explore game :quest :end)
      ;(explore game :quest :end)
      ;(explore game :sokoban :end)
      ;(visit game :main :medusa)
      (explore-level game :vlad :end)
      (explore-level game :main :end)
      (explore-level game :wiztower :end)
      (if (and (= :wiztower (branch-key game))
               (:end (curlvl-tags game))
               (unknown? (at-curlvl game {:x 40 :y 11})))
        (seek game {:x 40 :y 11}))
      ;(visit game :earth)
      ;(seek-level game :main "Dlvl:1")
      (log/debug "progress end")))

(defn- pause-condition?
  "For debugging - pause the game when something occurs"
  [game]
  (= :vlad (branch-key game))
  #_(have game "candelabrum")
  #_(have game "Orb of Fate")
  #_(= "Home 5" (:dlvl game)))

(def desired-weapons
  (ordered-set "Grayswandir" "Excalibur" "Mjollnir" "Stormbringer"
               "katana" "long sword"))

(def desired-items
  [(ordered-set "pick-axe" "dwarvish mattock") ; currenty-desired presumes this is the first category
   (ordered-set "skeleton key" "lock pick" "credit card")
   (ordered-set "ring of levitation" "boots of levitation")
   #{"ring of slow digestion"}
   #{"Orb of Fate"}
   (ordered-set "blindfold" "towel")
   #{"unicorn horn"}
   #{"Candelabrum of Invocation"}
   #{"Bell of Opening"}
   #{"Book of the Dead"}
   #{"Amulet of Yendor"}
   desired-weapons])

(defn entering-shop? [game]
  (some->> (nth (:last-path game) 0) (at-curlvl game) shop?))

(defn currently-desired
  "Returns the set of item names that the bot currently wants.
  Assumes the bot has at most 1 item of each category."
  [game]
  (loop [cs (if (or (entering-shop? game) (shop? (at-player game)))
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
          (or (with-reason "removing levitation to pick up item"
                (some->> (have-levi-on game) key (remove-use game)))
              (->PickUp (->> to-get set vec)))
          (log/debug "no desired items here"))
        (when-let [{:keys [step target]}
                   (navigate game #(some to-take? (:items %)))]
          (with-reason "want item at" target step))
        (log/debug "no desirable items anywhere"))))

(defn- wield-weapon [{:keys [player] :as game}]
  (if-let [[slot weapon] (some (partial have game) desired-weapons)]
    ; TODO can-wield?
    (when-not (:wielded weapon)
      (with-reason "wielding better weapon -" (:label weapon)
        (->Wield slot)))))

(defn light? [game item]
  (let [id (item-id game item)]
    (and (not= "empty" (:specific item))
         (= :light (:subtype id))
         (= :copper (:material id)))))

(defn uncurse-gear [game]
  ; TODO passive items (luckstone / orb of fate)
  (if-let [[_ item] (have game (every-pred cursed? :in-use))]
    (if-let [[slot _] (have-noncursed game "scroll of remove curse")]
      (with-reason "uncursing" (:label item)
        (->Read slot)))))

(defn reequip [game]
  (let [level (curlvl game)
        tile-path (mapv (partial at level) (:last-path game))
        step (first tile-path)]
    (or (uncurse-gear game)
        (if (and (not= :wield (some-> game :last-action typekw))
                 step (not (:dug step))
                 (not-any? (complement walkable?) tile-path))
          (with-reason "reequip - weapon"
            (wield-weapon game)))
        ; TODO multidrop
        (if-let [[slot _] (have game #(= "empty" (:specific %)))]
          (with-reason "dropping junk" (->Drop slot)))
        (if-let [[slot item] (have game (every-pred (partial light? game)
                                                    :lit))]
          (if (and (not= "magic lamp" (item-name game item))
                   (explored? game))
            (with-reason "saving energy" (->Apply slot)))
          (if-not (explored? game)
            (or (if-let [[slot lamp] (have game "magic lamp")]
                  (with-reason "using magic lamp" (->Apply slot)))
                (if-let [[slot lamp] (have game (partial light? game))]
                  (with-reason "using any light source" (->Apply slot))))))
        (if-let [[slot _] (and (not (needs-levi? (at-player game)))
                               (not-any? needs-levi? tile-path)
                               (have-levi-on game))]
          (with-reason "reequip - don't need levi"
            (remove-use game slot))))))

(defn fight [{:keys [player] :as game}]
  (if (:engulfed player)
    (with-reason "killing engulfer" (or (wield-weapon game)
                                        (->Move :E)))
    (let [tgts (hostile-threats game)]
      (when-let [{:keys [step target]} (navigate game tgts
                                          {:adjacent true :walking true
                                           :no-traps true :no-autonav true
                                           :max-steps hostile-dist-thresh})]
        (with-reason "targetting enemy at" target
          (or (wield-weapon game)
            step
            (if (blind? player)
              (->Attack (towards player target))
              (->Move (towards player target)))))))))

(defn- bribe-demon [prompt]
  (->> prompt
       log/spy
       (re-first-group #"demands ([0-9][0-9]*) zorkmids for safe passage")
       parse-int))

(defn- pause-handler [anbf]
  (reify FullFrameHandler
    (full-frame [_ _]
      (when (pause-condition? @(:game anbf))
        (log/debug "pause condition met")
        (pause anbf)))))

(defn init [anbf]
  (-> anbf
      (register-handler priority-bottom (pause-handler anbf))
      (register-handler (reify ChooseCharacterHandler
                          (choose-character [this]
                            (deregister-handler anbf this)
                            "nsm"))) ; choose samurai
      (register-handler (reify
                          OfferHandler
                          (offer-how-much [_ _]
                            (bribe-demon (:last-topline @(:game anbf))))
                          ReallyAttackHandler
                          (really-attack [_ _] false)))
      (register-handler -15 (enhance-handler anbf))
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
                               (reequip game))))
      (register-handler 2 (reify ActionHandler
                             (choose-action [_ game]
                               (consider-items game))))
      (register-handler 5 (reify ActionHandler
                            (choose-action [_ game]
                              (progress game))))))
