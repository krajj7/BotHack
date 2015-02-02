(ns bothack.bots.mainbot
  (:require [clojure.tools.logging :as log]
            [flatland.ordered.set :refer [ordered-set]]
            [bothack.bothack :refer :all]
            [bothack.item :refer :all]
            [bothack.itemtype :refer :all]
            [bothack.itemid :refer :all]
            [bothack.handlers :refer :all]
            [bothack.fov :refer :all]
            [bothack.player :refer :all]
            [bothack.pathing :refer :all]
            [bothack.monster :refer :all]
            [bothack.position :refer :all]
            [bothack.game :refer :all]
            [bothack.dungeon :refer :all]
            [bothack.level :refer :all]
            [bothack.tile :refer :all]
            [bothack.delegator :refer :all]
            [bothack.util :refer :all]
            [bothack.behaviors :refer :all]
            [bothack.sokoban :refer :all]
            [bothack.tracker :refer :all]
            [bothack.actions :refer :all]))

(defn- hostile-dist-thresh [game]
  (cond
    (at-planes? game) 1
    (= :sokoban (branch-key game)) 500
    :else 5))

(defn- hostile-threats [{:keys [player] :as game}]
  (->> (curlvl-monsters game)
       (filter #(and (hostile? %)
                     (or (adjacent? player %)
                         (covetous? %)
                         (and (not (and (blind? player) (:remembered %)))
                              (> (+ 5 (hostile-dist-thresh game))
                                 (- (:turn game) (:known %)))
                              (< (distance player %) (hostile-dist-thresh game))
                              (not (blind? player))
                              (not (hallu? player))))))
       set))

(defn- threat-map [game]
  (into {} (for [m (hostile-threats game)] [(position m) m])))

(defn- choose-food [{:keys [player] :as game}]
  (or (min-by (comp nw-ratio val)
              (have-all game (every-pred (partial edible? player)
                                         (comp (partial not= "lizard corpse")
                                               (partial item-name game))
                                         (complement tin?)) #{:bagged}))
      (have game "lizard corpse" #{:bagged})))

(declare farming? farm-done? farm-level? farm-spot? farm-sink)

(defn- handle-starvation [{:keys [player] :as game}]
  (or (if (and (weak? player)
               (or (not (can-pray? game))
                   (< 1200 (nutrition-sum game)))
               (not (overtaxed? player))
               (or (not (farming? game))
                   (farm-spot? game player)))
        (if-let [[slot food] (choose-food game)]
          (with-reason "weak or worse, eating" food
            (or (unbag game slot food)
                (->Eat slot)))))
      (if (and (or (fainting? player)
                   (and (weak? player) (not (farming? game))))
               (can-pray? game))
        (with-reason "praying for food" ->Pray))))

(defn- cursed-levi [{:keys [player] :as game}]
  (if (and (have game #{"boots of levitation"
                        "ring of levitation"} #{:cursed :worn})
           (not (have game holy-water? #{:bagged}))
           (not (have game "scroll of remove curse" #{:bagged :noncursed})))
    (with-reason "cursed levitation"
      ; won't work if we're in wiztower
      (or (pray game)
          (if (in-gehennom? game)
            (seek-level game :main :castle))
          (search 10)))))

(defn- handle-illness [{:keys [player] :as game}]
  (or (if (overtaxed? player)
        (with-reason "overtaxed" (search 10)))
      (if (:stoning player)
        (with-reason "fix stoning"
          (if-let [[slot item] (have game "lizard corpse" #{:bagged})]
            (or (unbag game slot item)
                (->Eat slot))
            (pray game))))
      (if (:lycantrophy player)
        (with-reason "fix lycantrophy"
          (if-let [[slot item] (have game "sprig of wolfsbane" #{:bagged})]
            (or (unbag game slot item)
                (->Eat slot))
            (pray game))))
      (if-let [[slot _] (and (unihorn-recoverable? game)
                             (or (some (:state player) #{:conf :stun :ill})
                                 (and (not (have-intrinsic? player :telepathy))
                                      (blind? player)))
                             (have-unihorn game))]
        (with-reason "applying unihorn to recover" (->Apply slot)))
      (if (:ill (:state player))
        (with-reason "fixing illness"
          (or (if-let [[slot _] (have game "eucalyptus leaf" #{:noncursed})]
                (->Eat slot))
              (if-let [[slot item] (or (have game "potion of healing"
                                          {:buc :blessed :bagged true})
                                       (have game #{"potion of extra healing"
                                                    "potion of full healing"}
                                          #{:noncursed :bagged}))]
                (or (unbag game slot item)
                    (->Quaff slot)))
              (pray game))))))

(defn name-first-amulet [bh]
  (reify ActionHandler
    (choose-action [this game]
      (when-let [[slot _] (have game "Amulet of Yendor")]
        (deregister-handler bh this)
        (with-reason "naming the real amulet"
          (->Name slot "REAL"))))))

(defn real-amulet? [item]
  (and (= "Amulet of Yendor" (:name item))
       (= "REAL" (:specific item))))

(defn get-amulet [game]
  (if-not (have game real-amulet?)
    (with-reason "searching for the amulet"
      (or (explore game)
          (search-level game 1)
          (seek game stairs-up?)))))

(defn have-throwable [game]
  (or (have game (some-fn dagger? short-sword?) #{:can-remove})
      (have game (some-fn dart? ammo?) #{:can-remove})
      (have game rocks? #{:can-remove})))

(defn castle-plan-b [{:keys [player] :as game}]
  (let [level (curlvl game)]
    (if (and (:castle (:tags level))
             (< (:x player) 13)
             (not-any? walkable? (for [y [11 12 13]]
                                   (at level 13 y)))
             (or (not (have-levi game))
                 (not-any? (:genocided game) #{";" "electric eel"})
                 (not (reflection? game))))
      (with-reason "castle plan B"
        (or (with-reason "find stairs"
              (if-not (some stairs-up? (tile-seq level))
                (seek game stairs-up?)))
            (if-let [[slot _] (and (not (and (farm-done? game)
                                             (have game "scroll of earth"
                                                   #{:noncursed :bagged})))
                                   (have game "wand of cold"))]
              (with-reason "using wand of cold"
                (if-let [pool (find-first pool? (for [y [11 12 13]]
                                                  (at level 13 y)))]
                  (if-let [{:keys [step]}
                           (navigate game #(and (= 2 (distance pool %))
                                                (in-line pool %)))]
                    (or step (->ZapWandAt slot (towards player pool)))))))
            (if-let [[slot scroll] (have game "scroll of earth"
                                         #{:noncursed :bagged})]
              (with-reason "using scroll of earth"
                (if-let [{:keys [step]} (navigate game (position 12 13))]
                  (or step (unbag game slot scroll) (->Read slot)))))
            (with-reason "visit Medusa"
              (if-let [{:keys [step]}
                       (navigate game #(and (stairs-up? %)
                                            (not (visited-stairs? %))))]
                (or step ->Ascend)))
            (with-reason "no way to cross moat"
              (if-let [{:keys [step target]}
                       (and (not (drawbridge? (at-curlvl game 14 12)))
                            (navigate game {:x 13 :y 12} #{:adjacent}))]
                (or step (->Move (towards player target))))))))))

(defn- have-dsm
  ([game] (have-dsm game {}))
  ([game opts]
   (have game #{"silver dragon scale mail" "gray dragon scale mail"} opts)))

(defn full-explore [{:keys [player] :as game}]
  (with-reason "full-explore"
    (if-not (get-level game :main :sanctum)
      (or (explore game :mines :minetown)
          (explore game :main :sokoban)
          (if (and (have game "Excalibur") (have-throwable game))
            (do-soko game))
          (explore game :main :quest)
          (let [minetown (get-level game :mines :minetown)]
            (if (and (not (below-medusa? game))
                     (or (some->> game have-key val key?)
                         (not (:minetown-grotto (:tags minetown)))
                         (:seen (at minetown 48 5)))
                     (or (< -7 (:ac player))
                         (not (have-pick game))
                         (not (have-key game))))
              (explore game :mines)))
          (if-not (farm-done? game)
            (explore game :main "Dlvl:20"))
          (castle-plan-b game)
          (if (and (or (> -7 (:ac player))
                       (not (have-dsm game))
                       (not (some (:genocided game) #{";" "electric eel"})))
                   (have game "wand of striking" #{:bagged}))
            (explore-level game :main :castle))
          (if (and (have-levi game)
                   (<= 14 (:xplvl (:player game)))
                   (have-dsm game))
            (explore-level game :quest :end))
          ;(explore game :main :castle :exclusive)
          (explore-level game :vlad :end)
          (explore-level game :main :end)
          (explore-level game :wiztower :end)
          (invocation game)))))

(defn endgame? [game]
  (get-level game :main :sanctum))

(defn progress [game]
  (with-reason "progress"
    (if-not (endgame? game)
      (full-explore game)
      (or (get-amulet game)
          (visit game :astral)
          (seek-high-altar game)))))

(defn- pause-condition?
  "For debugging - pause the game when something occurs"
  [game]
  #_(< 3205 (:turn game))
  #_(soko-done? game)
  #_(:oracle (curlvl-tags game))
  #_(= :astral (branch-key game))
  #_(= "Dlvl:46" (:dlvl game))
  #_(explored? game :main :end)
  #_(and (= :wiztower (branch-key game))
       (:end (curlvl-tags game))
  #_(and (= :vlad (branch-key game))
       (:end (curlvl-tags game)))
  #_(have game candelabrum)
  #_(have game "Orb of Fate")
  ))

(def desired-weapons
  (ordered-set "Excalibur" "long sword"))

(def desired-suit
  (ordered-set "gray dragon scale mail" "silver dragon scale mail" "dwarvish mithril-coat" "elven mithril-coat" "scale mail"))

(def desired-shirt
  (ordered-set "T-shirt" "Hawaiian shirt"))

(def desired-boots
  (ordered-set "speed boots" "high boots" "iron shoes"))

(def desired-shield
  (ordered-set "shield of reflection" "small shield"))

(def desired-cloak
  (ordered-set "cloak of magic resistance" "cloak of protection" "oilskin cloak" "elven cloak" "cloak of displacement" "cloak of invisibility" "dwarvish cloak"))

(def desired-helmet
  (ordered-set "helm of telepathy" "helm of brilliance" "dwarvish iron helm" "orcish helm"))

(def desired-gloves
  (ordered-set "gauntlets of power" "gauntlets of dexterity" "leather gloves"))

(def blind-tool (ordered-set "blindfold" "towel"))

(def farm-tool (ordered-set "skeleton key" "lock pick"))

(def always-desired #{"magic lamp" "wand of wishing" "scroll of genocide" "potion of gain level" "potion of full healing" "potion of extra healing" "tallow candle" "wax candle"})

(def limited-desired
  {"wand of death" 5
   "scroll of identify" 5
   "scroll of remove curse" 18
   "scroll of enchant armor" 5
   "scroll of earth" 3
   "scroll of charging" 5
   "scroll of enchant weapon" 4
   "amulet of life saving" 5})

(def desired-bag (ordered-set "oilskin sack" "sack" "bag of holding"))

(def desired-items
  [(ordered-set "pick-axe" #_"dwarvish mattock") ; currenty-desired presumes this is the first category
   (ordered-set "skeleton key" "lock pick" "credit card")
   (ordered-set "ring of levitation" "boots of levitation")
   #{"ring of conflict"}
   #{"ring of regeneration"}
   #{"ring of invisibility"}
   #{"Orb of Fate"}
   blind-tool
   (ordered-set "oil lamp" "brass lantern")
   #{"unicorn horn"}
   #{candelabrum}
   #{bell}
   #{book}
   #{"lizard corpse"}
   #{"ring of slow digestion"}
   #{"sprig of wolfsbane"}
   ;#{"bag of holding"}
   desired-cloak
   desired-suit
   desired-shield
   desired-shirt
   desired-boots
   desired-helmet
   desired-gloves
   #{"scroll of teleportation"}
   #{"amulet of reflection"}
   #{"amulet of ESP"}
   #{"wand of fire"}
   #{"wand of cold"}
   #{"wand of lightning"}
   #{"wand of teleportation"}
   #{"wand of striking"}
   ;#{"wand of digging"}
   desired-weapons
   desired-bag])

(def desired-singular (set (apply concat desired-items)))

(defn desired-food [game]
  (let [min-nw (if (< 2400 (nutrition-sum game))
                 (nw-ratio-avg game)
                 24)]
    (for [food (:food item-kinds)
          :when (and (not (egg? food))
                     (not (tin? food))
                     (not (corpse? food))
                     (> (nw-ratio food) min-nw))]
      (:name food))))

(defn desired-throwables [game]
  (let [amt-daggers (have-sum game dagger? #{:noncursed})
        amt-ammo (have-sum game dart? #{:noncursed})
        amt-rocks (have-sum game rocks? #{:noncursed})]
    (cond
      (< 3 amt-daggers) []
      (< 6 amt-ammo) daggers
      (and (< 6 amt-rocks)
           (less-than? 35 (inventory game))) (conj daggers "dart")
      (less-than? 30 (inventory game)) (concat daggers ["dart" "rock"]))))

(defn utility
  ([item]
   (cond-> 0
     (artifact? item) (+ 50)
     (and (:erosion item) (not (key? item))) (- (:erosion item))
     (:enchantment item) (+ (:enchantment item))
     (and (wand? item) (not (:charges item))) (+ 3)
     (:charges item) (+ (:charges item))
     (:proof item) (+ 3)
     (blessed? item) (+ 2)
     (uncursed? (:buc item)) inc
     (and (cursed? item) (not (:in-use item))) dec))
  ([game item]
   (+ (utility item)
      (let [iname (item-name game item)
            cat (find-first #(% iname) desired-items)]
        (if-let [cat-vec (and (some? cat) (vec cat))]
          (* 15 (- (count cat-vec) (.indexOf cat-vec iname)))
          0)))))

(defn- want-protection? [game]
  (and (< (:protection (:player game)) 3) (> 15 (:xplvl (:player game)))))

(defn- want-gold? [game]
  (and (want-protection? game)
       (< (available-gold game) (* 400 (inc (:xplvl (:player game)))))))

(defn- currently-desired
  "Returns the set of item names that the bot currently wants."
  [{:keys [player] :as game}]
  (loop [cs (if (or (entering-shop? game) (shop? (at-player game)))
              (rest desired-items) ; don't pick that pickaxe back up
              desired-items)
         res (into always-desired
                   (for [[item amt] limited-desired
                         :when (> amt (have-sum game item #{:bagged}))]
                     item))]
    (if-let [c (first cs)]
      (if-let [[slot i] (max-by (comp (partial utility game) val)
                                (have-all game c #{:bagged}))]
        (recur (rest cs)
               (into (conj res (item-name game i))
                     (take-while (partial not= (item-name game i)) c)))
        (recur (rest cs) (into res c)))
      (as-> res res
        (into res (desired-food game))
        (into res (desired-throwables game))
        (or (if-let [sanctum (get-level game :main :sanctum)]
              (if (and (not (have game real-amulet?))
                       (:seen (at sanctum 20 11)))
                (conj res "Amulet of Yendor")))
            res)
        (cond-> res
          (not (fast? player)) (conj "wand of speed monster")
          (and (get-level game :main :votd)
               (farm-done? game)) (disj "scroll of earth")
          (endgame? game) (disj "scroll of enchant armor")
          (want-gold? game) (conj "gold piece"))))))

(defn- handle-impairment [{:keys [player] :as game}]
  (with-reason "impairment"
    (or (if (and (has-hands? player)
                 (:ext-blind (:state player)))
          (with-reason "fixing external blindness" ->Wipe))
        (if-let [[slot _] (and (unihorn-recoverable? game)
                               (have-unihorn game))]
          (with-reason "applying unihorn to recover" (->Apply slot)))
        (if-let [[slot _] (have game blind-tool #{:worn :noncursed})]
          (with-reason "unblinding self"
            (->Remove slot)))
        (if (or (impaired? player) (:polymorphed player))
          (with-reason "waiting out impairment" (->Repeated (->Wait) 10))))))

(defn- take-cursed? [game item]
  (or (#{"levitation boots" "speed boots" "water walking boots" "cloak of displacement" "cloak of invisibility" "cloak of magic resistance" "cloak of protection" "elven cloak" "gauntlets of dexterity" "gauntlets of power" "helm of brilliance" "helm of opposite alignment" "helm of telepathy" "shield of reflection" "long sword" "unicorn horn" "scroll of identify" "skeleton key"} (item-name game item))
      (and (not (could-be? game "bag of holding" item))
           ((some-fn ring? amulet? tool? artifact? wand?) item))))

(defn want-buy? [game item]
  false) ; TODO

(def ^:private desired* (atom nil))

(defn- desired [game]
  (or @desired* (currently-desired game)))

(defn- should-try?
  [game item]
  (and (not (:cost item))
       (not (know-id? game item))
       (or (and (wand? item)
                ((some-fn (every-pred (complement :engrave)
                                      (complement (partial tried? game)))
                          (comp nil? :target)) (item-id game item)))
           (and ((some-fn scroll? potion? ring? amulet? armor?) item)
                (not (tried? game item))
                (safe? game item)))))

(defn- worthwhile? [game item]
  (let [id (item-name game item)]
    (or (real-amulet? item)
        (and (not (> -1 (enchantment item)))
             (not (tin? item))
             (or ((some-fn food? dart? dagger? gold? rocks? pick?) item)
                 ((desired game) id)
                 (limited-desired id)
                 (should-try? game item)
                 (some (desired game) (possible-names game item)))
             (not= "bag of tricks" id)
             (not (and (fast? (:player game)) (= "wand of speed monster" id)))
             (or (charged? item)
                 (= "wand of death" id)
                 (= "wand of wishing" id)
                 (and (:castle (curlvl-tags game)) (= "wand of striking" id)))
             (or (and (not= :cursed (:buc item))
                      (> 2 (:erosion item)))
                 (< 2 (enchantment item))
                 (take-cursed? game item))
             (or (not (:cost item)) (want-buy? game item))))))

(defn- have-spare [game itemname]
  (or (have game itemname #{:can-remove :bagged})
      (have game itemname {:can-remove false})))

(defn take-selector [{:keys [player] :as game}]
  (let [farm? (and (farming? game) (sink? (at-player game)))
        need-bag? (and (not (have game bag?)) (have game scroll?))]
    (fn [item]
      (or (real-amulet? item)
          (and (can-take? item)
               (or (worthwhile? game item)
                   (and farm? (could-be? game "scroll of scare monster" item)))
               (or (not (farm-level? game))
                   (not (:pudding (:tags (farm-sink game))))
                   (have game "scroll of identify" #{:bagged})
                   (and (bag? item) need-bag?)
                   (food? item)
                   (scroll? item))
               (or (less-than? 49 (inventory game))
                   (and (less-than? 52 (inventory game))
                        (some (cond-> #{"scroll of scare monster"
                                        "scroll of identify"}
                                need-bag? (conj "sack" "oilskin sack")
                                (not (have game food?)) (conj "food ration"))
                              (possible-names game item))))
               (let [id (item-name game item)]
                 (and (or (> 16 (:qty item)) (not (rocks? item)))
                      (or (not (candle? item)) (= 7 (:qty item)))
                      (or (not (potion? item)) (know-id? game item))
                      (or (should-try? game item)
                          (and farm?
                               (could-be? game "scroll of scare monster" item))
                          (if-let [want (some (desired game)
                                              (possible-names game item))]
                            (if-let [[_ o] (and (desired-singular want)
                                                (have-spare game
                                                            (:name item)))]
                              (> (utility item) (utility o))
                              true)))
                      (if-let [[_ o] (and (desired-singular id)
                                          (have-spare game id))]
                        (> (utility item) (utility o))
                        true))))))))

(defn examine-containers [game]
  (if-let [[slot item] (have game explorable-container?)]
    (with-reason "learning contents of" item
      (->Apply slot))))

(defn- unlockable-chest? [game tile]
  (and (not (shop? tile))
       (some :locked (:items tile))
       (or (have-key game)
           (have game dagger? #{:noncursed}))))

(defn examine-containers-here [game]
  (or (if (and (some explorable-container? (:items (at-player game)))
               (not (sink? (at-player game))))
        (with-reason "learning contents of containers on ground"
          (without-levitation game ->Loot)))
      (if (unlockable-chest? game (at-player game))
        (without-levitation game
          (with-reason "unlock chest"
            (or (if-let [[slot _] (have-key game)]
                  (->Unlock slot :.))
                (if-let [[slot _]
                         (or (have game dagger? #{:safe :can-use})
                             (have game dagger? #{:noncursed :can-use}))]
                  (or (make-use game slot)
                      ->ForceLock))))))))

(defn consider-items-here [{:keys [player] :as game}]
  (if (seq (:items (at-player game)))
    (let [to-take? (take-selector game)
          level (curlvl game)
          tile (at level player)]
      (or (if-let [to-get (and (not (shop? tile))
                               (seq (for [item (lootable-items tile)
                                          :when (to-take? item)]
                                      (:label item))))]
            (with-reason "looting desirable items"
              (without-levitation game
                (take-out \. (reduce #(assoc %1 %2 nil) {} to-get))))
            (log/debug "no desired lootable items"))
          (if-let [to-get (seq (for [item (:items tile)
                                     :when (to-take? item)]
                                 (:label item)))]
            (with-reason "getting desirable items"
              (without-levitation game
                (if (and (or (pit? tile) (spikepit? tile))
                         (not (:trapped player)))
                  (with-reason "getting into a pit"
                    (arbitrary-move game level))
                  (->PickUp (->> to-get set vec)))))
            (log/debug "no desired items here"))))))

(defn- remove-levi
  ([game] (remove-levi game nil))
  ([game path]
   (if-let [[slot _] (and (not (needs-levi? (at-player game)))
                          (not (#{:water :air} (:branch-id game)))
                          (not-any? needs-levi? path)
                          (have-levi-on game))]
     (with-reason "don't want levi"
       (remove-use game slot)))))

(defn consider-items [{:keys [player] :as game}]
  (if-not (and (have game real-amulet?)
               (have game desired-weapons)
               (at-planes? game))
    (let [to-take? (take-selector game)]
      (if-let [{:keys [step target]}
               (navigate game #(or (:new-items %)
                                   (and (or (some explorable-container?
                                                  (:items %))
                                            (unlockable-chest? game %))
                                        (not (sink? %)))
                                   (some to-take? (concat (:items %)
                                                          (lootable-items %))))
                         #{:no-fight :no-levitation})]
        (with-reason "new or desired item at" target
          (or step (remove-levi game)))
        (log/debug "no desirable items anywhere")))))

(defn uncurse-weapon [game]
  (if-let [[_ weapon] (wielding game)]
    (if-let [[slot scroll] (and (cursed? weapon)
                                (have game "scroll of remove curse"
                                      #{:noncursed :bagged}))]
      (with-reason "uncursing weapon" (:label weapon)
        (or (unbag game slot scroll)
            (->Read slot))))))

(defn- wield-weapon [{:keys [player] :as game}]
  (or (if-let [excal (and (not (overloaded? player))
                          (has-hands? player)
                          (find-first #(= "Excalibur" (item-name game %))
                                      (:items (at-player game))))]
        (->PickUp (:label excal)))
      (if-let [[slot weapon] (some (partial have-usable game) desired-weapons)]
        (if-not (or (:wielded weapon)
                    (#{:rub :wield} (typekw (:last-action game))))
          (or (uncurse-weapon game)
              (with-reason "wielding better weapon -" (:label weapon)
                (make-use game slot)))))))

(defn- choose-amulet [{:keys [player] :as game}]
  (or (and (not (have game #{"silver dragon scale mail"
                             "shield of reflection"} #{:worn}))
           (have game "amulet of reflection" #{:can-use :bagged}))
      (have game "amulet of life saving" #{:can-use :bagged})
      (have game "amulet of ESP" #{:can-use :bagged})))

(defn- wear-amulet [game]
  (with-reason "wear amulet"
    (if-let [[slot item] (and (not= :remove (typekw (:last-action game)))
                              (choose-amulet game))]
      (or (unbag game slot item)
          (make-use game slot)))))

(defn- wear-armor [{:keys [player] :as game}]
  (first (for [category [desired-shield desired-boots desired-shirt
                         desired-suit desired-cloak desired-helmet
                         desired-gloves]
               :let [[slot armor] (some (partial have-usable game) category)]
               :when (and armor (not (:worn armor))
                          (not (and (farming? game) (boots? armor)))
                          (or (not= "cloak of invisibility"
                                    (item-name game armor))
                              (not (shop? (at-player game))))
                          (not= :takeoff (typekw (:last-action game))))]
           (with-reason "wearing better armor"
             (make-use game slot)))))

(defn light? [item]
  (and (not= "empty" (:specific item))
       (not (:cost item))
       (= :light (item-subtype item))
       (= :copper (:material (item-id item)))))

(defn- wearable? [item]
  ((some-fn armor? ring? amulet?) item))

(defn bless-gear [game]
  (or (if-let [[slot item] (have game #{"Orb of Fate" "unicorn horn"
                                        "luckstone" "bag of holding"}
                                 #{:nonblessed :know-buc})]
        (bless game slot))
      (if-let [[slot scroll] (have game "scroll of remove curse"
                                   #{:noncursed :bagged})]
        (or (if-let [[s item] (have game #(and (wearable? %) (know-id? game %))
                                    {:can-use true :cursed true :in-use false})]
              (with-reason "put on to uncurse" (:label item)
                (make-use game s)))
            (if-let [[_ item] (have game (every-pred cursed? :in-use))]
              (with-reason "uncursing" (:label item)
                (or (unbag game slot scroll)
                    (if-not (cursed? (wielded-item game))
                      (if-let [[slot item] (have game cursed? {:in-use false})]
                        (with-reason "wield for extra uncurse"
                          (wield game slot))))
                    (->Read slot))))
            (if-let [[uslot _] (have game #{"unicorn horn"
                                            "Orb of Fate"} #{:cursed})]
              (with-reason "misc uncurse"
                (or (unbag game slot scroll)
                    (wield game uslot))))))))

(defn lit-mines? [game level]
  (and (= :mines (branch-key game))
       (if-let [floors (seq (filter #(and (floor? %)
                                          (< 5 (distance % (:player game))))
                                    (tile-seq level)))]
         (not-any? blank? floors))))

(defn- want-light? [game level]
  (not (or (explored? game)
           (:minetown (:tags level))
           (#{:air :water} (branch-key game))
           (lit-mines? game level))))

(defn use-light [game level]
  (or (if-let [[slot item] (have game (every-pred :lit light?))]
        (if (and (not (could-be? game "magic lamp" item))
                 (not (want-light? game level)))
          (with-reason "saving energy" (->Apply slot))))
      (if-let [[slot _] (have game #(and (could-be? game "magic lamp" %)
                                         (not (or (:cost %) (:lit %)))))]
        (with-reason "using magic lamp" (->Apply slot)))
      (if (and (want-light? game level) (not (have game :lit)))
        (if-let [[slot _] (have game light?)]
          (with-reason "using any light source" (->Apply slot))))))

(defn remove-rings [{:keys [player] :as game}]
  (or (if-let [[slot _] (and (not (or (farming? game) (endgame? game)))
                             (have game #{"ring of slow digestion"} #{:worn}))]
        (with-reason "don't need SD"
          (remove-use game slot)))
      (if-let [[slot _] (have game #{"ring of invisibility"
                                     "ring of conflict"} #{:worn})]
        (with-reason "don't need ring"
          (remove-use game slot)))
      (if-let [[slot _] (and (= (:hp player) (:maxhp player))
                             (have game "ring of regeneration" #{:worn}))]
        (with-reason "don't need regen"
          (remove-use game slot)))))

(defn drop-junk [game]
  (or (if-let [[slot item] (have game (complement (partial worthwhile? game))
                                 #{:can-remove :bagged})]
        (with-reason "dropping junk"
          (or (remove-use game slot)
              (unbag game slot item)
              (->Drop slot (:qty item)))))
      (loop [[cat & more] desired-items]
        (let [cat-items (have-all game cat #{:bagged})
              stuck? (fn [[stuck-slot stuck-item]]
                       (and (:in-use stuck-item)
                            (not (can-remove? game stuck-slot))))]
          (if (or (more-than? 2 cat-items)
                  (and (more-than? 1 cat-items)
                       (not (some stuck? cat-items))))
            (if-let [[slot item] (min-by (comp (partial utility game) val)
                                         (remove stuck? cat-items))]
              (with-reason "dropping less useful duplicate"
                (or (remove-use game slot)
                    (unbag game slot item)
                    (if-not (:in-use item)
                      (->Drop slot)))))
            (if (seq more)
              (recur more)))))
      (if-let [[slot item] (or (and (more-than? 35 (inventory game))
                                    (have game rocks?))
                               (and (more-than? 44 (inventory game))
                                    (have game dart?)))]
        (with-reason "dropping ammo - low on inventory slots"
          (->Drop slot (:qty item))))
      (if-let [[slot item] (have game #(and (rocks? %) (< 7 (:qty %))))]
        (with-reason "dropping rock excess"
          (->Drop slot (- (:qty item) 7))))
      (if (and (not (want-protection? game)) (pos? (available-gold game)))
        (with-reason "don't want gold"
          (->Drop \$ (available-gold game))))))

(defn- remove-unsafe [game]
  (if-let [[slot _] (have game #(not (safe? game %)) #{:can-remove})]
    (with-reason "removing potentially unsafe item"
      (remove-use game slot))))

(defn- enchant-gear [{:keys [player] :as game}]
  (if-let [[slot item] (and (not (endgame? game))
                            (have game "scroll of enchant armor"
                                  #{:bagged :noncursed}))]
    (with-reason "enchant armor"
      (if (have game (every-pred safe-enchant? armor?))
        (if-let [slots (keys (have-all game (every-pred
                                              (complement safe-enchant?)
                                              armor? :worn)))]
          (if (every? #(can-remove? game %) slots)
            (remove-use game (first slots)))
          (or (unbag game slot item)
              (->Read slot)))))))

(defn reequip [game]
  (let [level (curlvl game)
        tile-path (mapv (partial at level) (:last-path game))
        step (first tile-path)
        branch (branch-key game)
        farm? (farming? game)]
    (or (wear-amulet game)
        (drop-junk game)
        (enchant-gear game)
        (bless-gear game)
        (wear-armor game)
        (remove-unsafe game)
        (remove-rings game)
        (if-let [[slot item] (and (or farm? (endgame? game))
                                  (free-finger? (:player game))
                                  (not= :remove (typekw (:last-action game)))
                                  (have game "ring of slow digestion"))]
          (make-use game slot))
        (if-let [[slot item] (and farm? (not (farm-done? game))
                                  (have game "speed boots"))]
          (remove-use game slot))
        (if-let [[slot i] (and (not (fast? (:player game)))
                               (have game "wand of speed monster" #{:bagged}))]
          (with-reason "zapping self with /oSpeed"
            (or (unbag game slot i) (->ZapWandAt slot :.))))
        (if (and (not= :wield (some-> game :last-action typekw))
                 step (not (:dug step))
                 (every? walkable? tile-path))
          (if-let [[slot item] (and (#{:air :fire} branch)
                                    (not-any? portal? (tile-seq level))
                                    (have game real-amulet?))]
            (if-not (:in-use item)
              (with-reason "using amulet to search for portal"
                (wield game slot)))))
        (use-light game level)
        (remove-levi game tile-path))))

(defn reequip-weapon [game]
  (if (and (not= :apply (typekw (:last-action game)))
           (not (amulet? (wielded-item game)))
           (not (dug? (at-player game))))
    (wield-weapon game)))

(defn- bait-wizard [game level monster]
  (if (and (= :magenta (:color monster)) (= \@ (:glyph monster))
           (or (not (typename monster)) (rodney? monster))
           (not= :water (branch-key game))
           ((some-fn pool? lava? unknown?) (at level monster)))
    (with-reason "baiting possible wizard away from water/lava"
      ; don't let the book fall into water/lava
      (or (:step (navigate game #(every? (not-any-fn? lava? pool?)
                                         (neighbors level %)))
                 #{:explored :no-dig})
          (->Wait)))))

(defn- bait-giant [game level monster]
  (if (and (= \H (:glyph monster)) (= "Home 3" (:dlvl level))
           (not (have-pick game)) (= 12 (:y monster))
           (< 18 (:x monster) 25))
    ; only needed until the bot can use wand of striking to break blocking boulders
    (with-reason "baiting giant away from corridor"
      (or (:step (navigate game #{(position 26 12)
                                  (position 16 12)}))
          (->Wait)))))

(defn- ranged [game monster]
  ; TODO wands
  (with-reason "ranged combat"
    (if-let [[slot _] (have-throwable game)]
      (->Throw slot (towards (:player game) monster)))))

(defn- hit-floating-eye [{:keys [player] :as game} monster]
  (if (and (adjacent? player monster)
           (= "floating eye" (typename monster)))
    (with-reason "killing floating eye"
      (or (wield-weapon game)
          (if (or (blind? player)
                  (reflection? game)
                  (free-action? game))
            (->Attack (towards player monster)))
          (if-let [[slot _] (have game blind-tool #{:noncursed})]
            (->PutOn slot))
          (ranged game monster)))))

(defn corrodeproof-weapon? [item]
  (and (weapon? item)
       (or (artifact? item)
           (:proof item))))

(defn hit-corrosive [game monster]
  (with-reason "hitting corrosive monster" monster
    (if (corrosive? monster)
      (or (if-let [[slot item] (have game corrodeproof-weapon?
                                     #{:can-use :noncursed})]
            (make-use game slot)
            (if-let [[_ w] (wielding game)]
              (if (not (cursed? w))
                (->Wield \-))))
          (->Move (towards (:player game) monster))))))

(defn mobile? [game monster]
  (and (not (mimic? monster))
       (not (sessile? monster))
       (or (:awake monster)
           (> 6 (- (:turn game) (:first-known monster))))))

(defn kite [{:keys [player] :as game} monster]
  (if (and (adjacent? player monster)
           (#{"black pudding" "brown pudding" "dwarf" "mumak"}
                     (typename monster))
           (mobile? game monster)
           (< -7 (:ac player))
           (pos? (rand-int 20))
           (not (:just-moved monster)))
    (with-reason "kite"
      (:step (navigate game #(= 2 (distance monster %))
                       {:max-steps 1 :no-traps true
                        :no-fight true :walking true})))))

(defn hit-surtur [{:keys [player] :as game} monster]
  (if-let [[slot item] (and (= "Lord Surtur" (typename monster))
                            (have game "wand of cold" #{:can-use}))]
    (->ZapWandAt slot (towards player monster))))

(defn hit-wizard [{:keys [player] :as game} monster]
  (if-let [[slot item] (and (#{"Wizard of Yendor"
                               "Famine" "Pestilence"} (typename monster))
                            (have game "wand of death" #{:can-use}))]
    (->ZapWandAt slot (towards player monster))))

(defn hit-leprechaun [game monster]
  (if-let [qty (and (leprechaun? monster)
                    (some-> (have game "gold piece") val :qty))]
    (->DropSingle \$ qty)))

(defn- hit [{:keys [player] :as game} level monster]
  (with-reason "hitting" monster
    (or (bait-wizard game level monster)
        (bait-giant game level monster)
        (if-let [[slot _] (and (= :air (branch-key game))
                               (not (have-levi-on game))
                               (have-levi game))]
          (with-reason "levitation for :air"
            (make-use game slot)))
        (if (adjacent? player monster)
          (or (hit-wizard game monster)
              (hit-leprechaun game monster)
              (hit-surtur game monster)
              (hit-floating-eye game monster)
              (kite game monster)
              (hit-corrosive game monster)
              (wield-weapon game)
              (if (or (not (monster? (at level monster)))
                      (#{\I \1 \2 \3 \4 \5} (:glyph monster)))
                (->Attack (towards player monster)))
              (->Move (towards player monster)))))))

(defn- kill-engulfer [{:keys [player] :as game}]
  (if (:engulfed player)
    (with-reason "killing engulfer" (or (wield-weapon game)
                                        (->Move :E)))))

(defn- low-hp? [{:keys [hp maxhp] :as player}]
  (or (< hp 10)
      (<= (/ hp maxhp) 9/20)))

(defn can-ignore? [{:keys [player] :as game} monster]
  (or (passive? monster)
      (not (hostile? monster))
      (unicorn? monster)
      (and (pool? (at-curlvl game monster)) (not (flies? monster))
           (not-any? pool? (neighbors (curlvl game) player)))
      (#{"grid bug" "newt" "leprechaun"} (typename monster))
      (and (mimic? monster) (not (adjacent? player monster)))))

(defn pushover? [{:keys [player] :as game} monster]
  (or (can-ignore? game monster)
      (and (>= -14 (:ac player))
           (or (#{\a \b \s \S \B} (:glyph monster))
               (= "gremlin" (:name monster))))))

(defn can-handle? [{:keys [player] :as game} monster]
  (cond
    (and (drowner? monster) (pool? (at-curlvl game monster))) false
    (= "floating eye" (typename monster)) (or (blind? player)
                                              (reflection? game)
                                              (free-action? game)
                                              (have-throwable game)
                                              (have game blind-tool
                                                    #{:noncursed}))
    (#{"spotted jelly"
       "ochre jelly"} (typename monster)) (and (have game corrodeproof-weapon?)
                                               (> (:hp player) 60))
    :else true))

(defn- engrave-slot [game perma?]
  (or (if perma?
        (some-> (have game #{"wand of fire" "wand of lightning"}) key))
      \-))

(defn engrave-e
  ([game] (engrave-e game false nil))
  ([game perma?] (engrave-e game perma? nil))
  ([{:keys [player] :as game} perma? farm?]
   (with-reason "engrave E"
     (let [tile (at-player game)
           append? (or (e? tile) (less-than? 200 (:engraving tile)))]
       (if (and (has-hands? player)
                (engravable? tile))
         (or (remove-levi game)
             (if-not (not (can-engrave? game))
               (->Engrave (engrave-slot game perma?)
                          (str "Elbereth" (if farm? \*))
                          append?))))))))

(defn pray-for-hp [{:keys [player] :as game}]
  (if (and (can-pray? game)
           (or (> 6 (:hp player))
               (>= (quot (:maxhp player) 7) (:hp player))))
    (with-reason "praying for hp" ->Pray)))

(defn exposed? [game level pos]
  (->> (neighbors level pos)
       (filter (some-fn walkable? pool?))
       (more-than? 2)))

(defn- safe-hp? [{:keys [hp maxhp] :as player}]
  (or (>= (/ hp maxhp) 9/10) (< 175 hp)))

(defn- recover
  ([game]
   (recover game false))
  ([{:keys [player] :as game} safe?]
   (if-not (safe-hp? player)
     (or (if-let [[slot _] (and (free-finger? player)
                                (have game "ring of regeneration"
                                      #{:noncursed}))]
           (with-reason "recover - regen"
             (make-use game slot)))
         (if safe?
           (with-reason "recovering - exploring nearby items"
             (if-let [{:keys [step]}
                      (navigate game :new-items
                                {:no-fight true :no-autonav true
                                 :no-traps true :explored true
                                 :no-levitation true :max-steps 10})]
               (or step (remove-levi game)))))
         (with-reason "moving to safer position"
           (:step (navigate game
                            (complement (partial exposed? game (curlvl game)))
                            {:max-steps 8 :no-traps true :explored true
                             :no-fight true})))
         (with-reason "recovering" (->Repeated (->Wait) 10))))))

(defn retreat [{:keys [player] :as game}]
  (with-reason "retreating"
    (if (low-hp? player)
      (let [level (curlvl game)
            tile (at level player)
            threats (threat-map game)
            adjacent (->> (neighbors player)
                          (keep (partial monster-at level))
                          (filter hostile?))]
        (or (pray-for-hp game)
            ; TODO escape items - teleportation, digging, perma-e
            (kill-engulfer game)
            (if (and (some (every-pred (complement :fleeing)
                                       (complement passive?)) adjacent)
                     (not-any? ignores-e? adjacent)
                     (can-engrave? game))
              (if (engravable? tile)
                (with-reason "retreat engrave"
                  (engrave-e game (not-any? ignores-e? (vals threats))))
                (if-let [t (find-first #(and (engravable? %)
                                             (not (monster-at level %))
                                             (less-than?
                                               (count adjacent)
                                               (filter threats (neighbors %))))
                                       (neighbors level tile))]
                  (with-reason "moving to neighbor tile to engrave"
                    (->Move (towards player t))))))
            (if (or (empty? threats)
                    (and (perma-e? tile)
                         (not-any? ignores-e? (vals threats))))
              (recover game))
            (if-let [{:keys [step target]} (navigate game stairs-up?
                                                     #{:no-fight :explored
                                                       :no-autonav :walking})]
              (if (stairs-up? (at level player))
                (if (and (seq threats) (not= 1 (dlvl game)))
                  (with-reason "retreating upstairs" ->Ascend)
                  (with-reason "prepared to retreat upstairs" ->Search))
                (if (or (= (position target) (position (first (:path step))))
                        (some->> (:dir step) (in-direction player)
                                 neighbors (not-any? threats)))
                  step)))
            (if-let [nbr (find-first #(and (not (exposed? game level %))
                                           (passable-walking? game level tile %)
                                           (not (monster-at level %))
                                           (not-any? threats (neighbors %)))
                                     (neighbors level tile))]
              (with-reason "running away"
                (->Move (towards tile nbr))))
            (log/debug "retreat failed"))))))

(defn keep-away? [{:keys [player] :as game} m]
  (or (and (pool? (at-curlvl game m))
           (or (drowner? m) (not (typename m)))
           (not (have game "oilskin cloak" #{:worn})))
      (if-let [montype (typename m)]
        (or (some #(.contains montype %)
                  ["nymph" "rust monster" "disenchanter" "mind flayer"])
            (and (= "homunculus" montype)
                 (not (have-intrinsic? player :sleep)))))))

(defn targettable
  "Returns a list of first hostile monster for each direction (that can be targetted by throw, zap etc.) and there seems to be no risk of hitting non-hostiles or water/lava for non-rays."
  ([game] (targettable game 6 false))
  ([game ray?] (targettable game 5))
  ([{:keys [player] :as game} max-dist ray?]
   (let [level (curlvl game)]
     (for [dir directions
           :let [tiles (->> player
                            (iterate #(in-direction level % dir))
                            (take-while some?)
                            rest
                            (take max-dist))]
           ; TODO bounce rays
           :when (or ray? (not-any? (some-fn pool? lava? sink?) tiles))
           :when (not-any? :room tiles)
           :let [monsters (->> tiles
                               (take-while (some-fn walkable? boulder?))
                               (keep (partial monster-at level)))]
           :when (every? hostile? monsters)
           :let [target (first monsters)]
           :when (and target (not (:remembered target)))]
       target))))

(defn- use-rings [{:keys [player] :as game} threats]
  (or (if-let [[slot _] (and (free-finger? player)
                             (not (:minetown (:tags (curlvl game))))
                             (not-any? :room (neighbors (curlvl game) player))
                             (more-than? 3 threats)
                             (have game "ring of conflict" #{:noncursed}))]
        (with-reason "conflict for combat"
          (make-use game slot)))
      (if-let [[slot _] (and (free-finger? player)
                             (not (endgame? game))
                             (or (and (not-any? sees-invisible? threats)
                                      (more-than? 1 threats))
                                 (some (every-pred (complement sees-invisible?)
                                                   (partial keep-away? game))
                                       threats))
                             (have game "ring of invisibility" #{:noncursed}))]
        (with-reason "invis for combat"
          (make-use game slot)))))

(defn hits-hard? [m]
  (= "winged gargoyle" (typename m)))

(defn castle-fort [game level]
  (if (and (:castle (:tags level))
           (not (:walked (at level 35 15))) (not (:walked (at level 35 14)))
           (not (:walked (at level 40 8))) (not (:walked (at level 40 16)))
           (= (position (at-player game)) (position 35 12))
           (pos? (rand-int 16)))
    (with-reason "stay in fort" ->Search)))

(defn castle-move [game level]
  (if (:castle (:tags level))
    (with-reason "make castle fort"
      (if (and (= (position (at-player game)) (position 12 13))
               (boulder? (at level 11 13)) (boulder? (at level 11 14))
               (not (boulder? (at level 10 13)))
               (not (monster-at level 10 13)))
        (without-levitation game (->Move :W))))))

(defn clear-farm [{:keys [player] :as game}]
  (with-reason "clear farm"
    (if-let [t (and (= "Elbereth*" (:engraving (at-player game)))
                    (farm-done? game)
                    (find-first boulder? (neighbors (curlvl game) player)))]
      (:step (navigate game t)))))

(defn- destroy-drawbridges [{:keys [player] :as game} level]
  (if-let [drawbridge (and (= :quest (branch-key game))
                           (find-first drawbridge? (neighbors level player)))]
    (with-reason "destroy drawbridge"
      (if-let [[slot _] (and (have-levi game)
                             (not (drawbridge? (at level player)))
                             (not-any? wall? (neighbors level player))
                             (have game "wand of striking"))]
        (->ZapWandAt slot (towards player drawbridge))))))

(defn fight [{:keys [player] :as game}]
  (let [level (curlvl game)
        nav-opts {:adjacent true
                  :no-traps true
                  :no-autonav true
                  :walking true
                  :max-steps (hostile-dist-thresh game)}]
    (or (kill-engulfer game)
        (castle-move game level)
        (destroy-drawbridges game level)
        (if-not (have game amulet? #{:worn})
          (wear-amulet game)) ; replace LS
        (let [threats (->> (hostile-threats game)
                           (remove (partial can-ignore? game))
                           set)
              adjacent (->> (neighbors player)
                            (keep (partial monster-at level))
                            (filter hostile?)
                            (remove (partial can-ignore? game)))]
          (or ; TODO if standing on ice without levi move away, also drawbridge
              (if (and (not (e? (at-player game)))
                       (or (more-than? 1 (remove (some-fn :fleeing
                                                          ignores-e?) adjacent))
                           (some (every-pred (partial keep-away? game)
                                             (complement :fleeing))
                                 adjacent)))
                (with-reason "fight engrave"
                  (engrave-e game)))
              (if (and (exposed? game level player)
                       (more-than? 1 (filter (partial mobile? game) adjacent)))
                (with-reason "moving to non-exposed position"
                  (:step (navigate game #(and (not (exposed? game level %))
                                              (not-any?
                                                (partial monster-at level)
                                                (including-origin neighbors %)))
                                   ; TODO if faster than threats increase max-steps
                                   {:max-steps 2 :no-traps true
                                    :no-fight true :explored true}))))
              (if-let [{:keys [step path]}
                       (and (exposed? game level player)
                            (seq (filter (partial mobile? game) adjacent))
                            (or (more-than? 1 (filter ignores-e? threats))
                                (more-than? 2 (filter
                                                #(and (mobile? game %)
                                                      (not (pushover? game %))
                                                      (not (slow? %)))
                                                threats)))
                            (navigate game stairs-up? {:max-steps 40
                                                       :no-autonav true
                                                       :walking true
                                                       :explored true}))]
                (if (and (not-any? #(if-let [monster (monster-at level %)]
                                      (and (not (pushover? game monster))
                                           (< 20 (- (:turn game)
                                                    (:known monster)))))
                                   (for [tile path
                                         nbr (including-origin neighbors tile)]
                                     nbr))
                         (less-than? 8 (take-while #(exposed? game level %)
                                                   path))
                         (pos? (rand-int 20)))
                  (with-reason "moving towards the upstairs" step)))
              (if-let [monster (or (find-first rider? adjacent)
                                   (find-first (some-fn unique?
                                                        titan?) adjacent)
                                   (find-first priest? adjacent)
                                   (find-first werecreature? adjacent)
                                   (find-first ignores-e? adjacent)
                                   (find-first hits-hard? adjacent)
                                   (find-first nasty? adjacent))]
                (hit game level monster))
              (clear-farm game)
              (if-let [m (min-by (partial distance player)
                                 (filter (every-pred (partial keep-away? game)
                                                     (complement :fleeing)
                                                     (complement :remembered)
                                                     (partial mobile? game))
                                         threats))]
                (if (and (> 3 (distance player m)) (pos? (rand-int 15)))
                  (with-reason "trying to keep away from" m
                    (engrave-e game))))
              (if-let [m (find-first #(and (keep-away? game %)
                                           (not (adjacent? player %)))
                                     (targettable game))]
                (with-reason "keep-away monster" m
                  (ranged game m)))
              (when-let [{:keys [step target]} (navigate game threats nav-opts)]
                (let [monster (monster-at level target)]
                  (with-reason "targetting enemy" monster
                    (or (use-rings game threats)
                        (hit game level monster)
                        (castle-fort game level)
                        (if (and (more-than? 2 (filter (partial mobile? game)
                                                       threats))
                                 (not (exposed? game level player))
                                 (some->> (:dir step)
                                          (in-direction player)
                                          (exposed? game level)))
                          (with-reason "staying in more favourable position"
                            (if (pos? (rand-int 13))
                              ->Search)))
                        (if-let [m (find-first #(and (= 2 (distance player %))
                                                     (not (pushover? game %))
                                                     (mobile? game %))
                                               threats)]
                          (if (and (pos? (rand-int (if (slow? m) 30 10)))
                                   (not (passive? m))
                                   (not (:fleeing m))
                                   (not (spellcaster? m)))
                            (with-reason "baiting monsters" ->Search)))
                        step))))))
        (let [leftovers (->> (hostile-threats game)
                             (filter (partial can-ignore? game))
                             (filter (partial can-handle? game))
                             set)]
          (when-let [{:keys [step target]} (navigate game leftovers
                                                     (assoc nav-opts
                                                            :explored true))]
            (let [monster (monster-at level target)]
              (with-reason "targetting leftover enemy" monster
                (or (hit game level monster)
                    step)))))
        (if-let [dir (and (= :sokoban (branch-key game))
                          (= :move (typekw (:last-action game)))
                          (:dir (:last-action game)))]
          (if (and (boulder? (in-direction level player dir))
                   (= (position player) (:last-position game)))
            (if-let [monster (->> (in-direction (in-direction player dir) dir)
                                  (monster-at game))]
              (if (< (+ (:first-known monster) 10) (:turn game))
                (with-reason "ranged attack soko blocker"
                  (ranged game monster))))))
        (castle-fort game level))))

(defn fight-covetous [game]
  (if-let [m (find-first (every-pred covetous? hostile?)
                         (curlvl-monsters game))]
    (with-reason "going to kill" (typename m)
      (:step (navigate game m)))))

(defn- bribe-demon [prompt]
  (->> prompt ; TODO parse amount and pass as arg in the scraper, not in bot logic
       (re-first-group #"demands ([0-9][0-9]*) zorkmids for safe passage")
       parse-int))

(defn- pause-handler [bh]
  (reify FullFrameHandler
    (full-frame [_ _]
      (when (pause-condition? @(:game bh))
        (log/debug "pause condition met")
        (pause bh)))))

(defn- eat-all? [{:keys [player] :as game}]
  (or (hungry? player)
      (> 1000 (nutrition-sum game))
      (and (have-intrinsic? game :fire)
           (have-intrinsic? game :poison))))

(defn- feed [{:keys [player] :as game}]
  (if-not (or (satiated? player) (overloaded? player))
    (let [beneficial? #(every-pred
                         (partial fresh-corpse? game %)
                         (partial want-to-eat? player))
          edible? #(every-pred
                     (partial fresh-corpse? game %)
                     (partial edible? player))]
      (or (if (= :astral (branch-key game))
            (if-let [[slot food] (choose-food game)]
              (with-reason "eating against Famine" food
                (or (unbag game slot food)
                    (->Eat slot)))))
          (if-let [p (navigate game #(and (some (beneficial? %) (:items %))))]
            (with-reason "want to eat corpse at" (:target p)
              (or (:step p)
                  (->> (at-player game) :items
                       (find-first (beneficial? player)) :label
                       ->Eat
                       (without-levitation game)))))
          (if (eat-all? game)
            (if-let [p (navigate game #(and (some (edible? %) (:items %))))]
              (with-reason "going to eat corpse at" (:target p)
                (or (:step p)
                    (->> (at-player game) :items
                         (find-first (edible? player)) :label
                         ->Eat
                         (without-levitation game))))))))))

(defn offer-amulet [game]
  (let [tile (and (= :astral (:branch-id game))
                  (at-player game))]
    (if (and (altar? tile) (= (:alignment (:player game)) (:alignment tile)))
      (some-> (have game real-amulet?) key ->Offer))))

(defn detect-portal [bh]
  (reify ActionHandler
    (choose-action [this {:keys [player] :as game}]
      (if-let [[scroll s] (and (= :water (branch-key game))
                               (not (:polymorphed player))
                               (have game "scroll of gold detection"
                                     #{:safe-buc :bagged}))]
        (with-reason "detecting portal"
          (or (unbag game scroll s)
              (when (confused? player)
                (deregister-handler bh this)
                (->Read scroll))
              (if-let [[potion p] (and (not-any? #(and (> 4 (distance player %))
                                                       (hostile? %))
                                                 (curlvl-monsters game))
                                       (have game #{"potion of confusion"
                                                    "potion of booze"}
                                             #{:nonblessed :bagged}))]
                (with-reason "confusing self"
                  (or (unbag game potion p)
                      (->Quaff potion))))))))))

(defn- seek-fountain [game]
  (with-reason "seeking a fountain to make Excal"
    (let [oracle (get-level game :main :oracle)]
      (or (if (or (nil? oracle)
                  (not-any? :seen (neighbors oracle oracle-position)))
            (or (seek-level game :main :oracle)
                (seek game oracle-position {:adjacent true})))
          (if (some fountain? (tile-seq oracle))
            (seek-level game :main :oracle))
          (if-let [{:keys [step]} (and (not (:minetown (curlvl-tags game)))
                                       (navigate game fountain?))]
            step
            (or (some->> (:dlvl oracle) (iterate prev-dlvl) rest
                         (take-while (partial not= "Dlvl:0"))
                         (find-first (comp (partial some fountain?) tile-seq
                                           (partial get-level game :main)))
                         (seek-level game :main))
                (seek-feature game :fountain)))))))

(defn make-excal
  "When we have appropriate armor and xp, dip for Excalibur"
  [{:keys [player] :as game}]
  (if-let [[slot _] (and (<= 5 (:xplvl player))
                         (or (<= (:ac player) 3)
                             (get-level game :mines :end))
                         (have game "long sword"))]
    (with-reason "getting Excal"
      (or (seek-fountain game)
          (if (fountain? (at-player game))
            (without-levitation game (->Dip slot \.)))))))

(defn excal-handler [bh]
  (reify ActionHandler
    (choose-action [this game]
      (if (have game "Excalibur")
        (do (deregister-handler bh this)
            (log/warn "got excal"))
        (make-excal game)))))

(defn rob? [m]
  (#{"dwarf" "dwarf lord" "dwarf king" "hobbit"} (typename m)))

(defn rob-peacefuls [{:keys [player] :as game}]
  (let [level (curlvl game)]
    (if-let [{:keys [step target]}
             (navigate game #(if-let [monster (monster-at level %)]
                               (and (not (unicorn? monster))
                                    (not (shop? %))
                                    (or (blocked? %) (rob? monster))))
                       #{:adjacent})]
      (with-reason "robbing a poor peaceful dorf"
        (or step (->Attack (towards player target)))))))

(defn wander [game]
  (with-reason "wandering"
    (or (explore game)
        (search-level game 1)
        (:step (navigate game (->> (curlvl game) tile-seq
                                   (filter (every-pred
                                             (complement boulder?)
                                             (some-fn floor? corridor?)
                                             :walked))
                                   (min-by :walked)))))))

(defn- hunt-action [{:keys [player] :as game} robbed-of]
  (if-let [[_ dlvl branch _] (first robbed-of)]
    (let [level (curlvl game)
          stealers (filter steals? (vals (:monsters level)))
          recent (max-by :known stealers)]
      (with-reason "seeking monsters that stole my items:" robbed-of
        (or (seek-level game branch dlvl)
            (if-let [[slot _] (and (or (not recent)
                                       (< 11 (- (:turn game) (:known recent))))
                                   (have-intrinsic? player :telepathy)
                                   (have game blind-tool #{:noncursed}))]
              (make-use game slot))
            (if-let [step (:step (navigate game (nav-targets stealers)))]
              step) ; expect fight to take over when close enough
            (wander game))))))

(defn- found-item? [found [_ _ _ item]]
  (some #(and (= (select-keys item [:specific :proof :name :enchantment])
                 (select-keys % [:specific :proof :name :enchantment])))
        found))

(defn- amulet-escape [game [turn dlvl branch item :as entry]]
  (if (and (real-amulet? item)
           (= dlvl (:dlvl game))
           (= :main branch (branch-key game)))
    (if (= "Dlvl:1" dlvl)
      (do (log/warn "assuming amulet is downstairs")
          [(+ 5000 turn) (get-dlvl game :main :sanctum) :main item])
      (do (log/warn "assuming amulet is upstairs")
          [(+ 500 turn) (prev-dlvl dlvl) branch item]))
    entry))

(defn hunt [{:keys [game] :as bh}]
  (let [robbed-of (atom [])] ; [turn dlvl branch item]
    (reify
      ActionHandler
      (choose-action [_ game]
        (hunt-action game @robbed-of))
      AboutToChooseActionHandler
      (about-to-choose [_ {:keys [player] :as game}]
        (if (not= @robbed-of
                  (swap! robbed-of (partial removev #(< 3000 (- (:turn game)
                                                                (first %))))))
          (log/debug "forgetting about stolen items, now missing" @robbed-of))
        (when (and (blind? player)
                   (have-intrinsic? player :telepathy)
                   (not-any? steals? (curlvl-monsters game)))
          (swap! robbed-of (partial mapv (partial amulet-escape game)))
          (swap! robbed-of (partial removev (fn at-level? [[_ dlvl branch item]]
                                              (and (= dlvl (:dlvl game))
                                                   (= (branch-key game branch)
                                                      (branch-key game))))))))
      FoundItemsHandler
      (found-items [_ items]
        (if (not= @robbed-of
                  (swap! robbed-of (partial removev #(found-item? items %))))
          (log/debug "found stolen items, now missing" @robbed-of)))
      ToplineMessageHandler
      (message [_ msg]
        (when-let [label (re-first-group #" (?:stole|snatches) ([^.!]*)[.!]"
                                         msg)]
          (log/debug "robbed of" label)
          (if-let [[_ item] (inventory-label @game label)]
            (swap! robbed-of conj
                   [(:turn @game) (:dlvl @game) (:branch-id @game) item])
            (log/warn "stolen item" label "not in inventory?")))))))

(defn wish [game]
  (cond
    (and (#{:engrave :zapwand} (typekw (:last-action game)))
         (not (have game "scroll of charging" #{:blessed :bagged}))
         (not (have game "scroll of charging" #{:wished :bagged}))
         (not= "recharged"
               (:specific (inventory-slot game (:slot (:last-action game))))))
    "2 blessed scrolls of charging"
    (and (below-medusa? game) (not (have game "ring of levitation" #{:bagged})))
    "blessed ring of levitation"
    (and (below-medusa? game)
         (not-any? (:genocided game) #{";" "electric eel"}))
    "2 blessed scrolls of genocide"
    (and (not (have-dsm game))
         (not (have game "cloak of magic resistance")))
    "blessed greased +3 gray dragon scale mail"
    (not (have-dsm game))
    "blessed greased +3 silver dragon scale mail"
    (and (have-dsm game) (not (have game #{"amulet of reflection"
                                           "silver dragon scale mail"
                                           "shield of reflection"})))
    "blessed greased fixed +3 shield of reflection"
    (and (not (have game "scroll of remove curse" #{:bagged :safe}))
         (or (have-dsm game {:can-use false})
             (and (have game "ring of levitation" #{:bagged})
                  (not (have-levi game))
                  (below-medusa? game))))
    "2 blessed scrolls of remove curse"
    (not (every? (:genocided game) #{"L" ";"}))
    "2 blessed scrolls of genocide"
    (not (have-candles? game))
    "7 blessed wax candles"
    (not (have game "speed boots"))
    "blessed greased fixed +3 speed boots"
    (not (every? (:genocided game) #{"mind flayer" "master mind flayer"}))
    "2 uncursed scrolls of genocide"
    (and (not (have game "helm of telepathy"))
         (not (:see-invis (:intrinsics (:player game)))))
    "blessed greased fixed +3 helm of telepathy"
    ;(not (have game "gauntlets of power"))
    ;"blessed fixed +3 gauntlets of power"
    (not-any? (:genocided game) #{"R" "disenchanter"})
    "2 blessed scrolls of genocide"
    (< -20 (:ac (:player game)))
    "3 blessed scrolls of enchant armor"
    (not (have game "wand of death" #{:bagged}))
    "blessed wand of death"
    (and (not (have game "amulet of life saving" #{:bagged}))
         (not (have game "amulet of reflection" #{:in-use})))
    "blessed amulet of life saving"
    :else (case (mod (:wishes game)
                     (if (and (<= -25 (:ac (:player game)))
                              (not (endgame? game))) 3 2))
            0 "blessed amulet of life saving"
            1 "blessed wand of death"
            2 "3 blessed scrolls of enchant armor")))

(defn- want-buc? [game item]
  (and (nil? (:buc item))
       ((not-any-fn? food? gem? statue? wand? ammo? dagger?) item)
       (or (know-id? game item)
           (:safe (item-id game item)))))

(defn wow-spot [game]
  (for [y [6 18]
        x [12 66]]
    (at-curlvl game x y)))

(defn- kickable-sink? [tile]
  (and (sink? tile)
       (not (blocked? tile))
       (empty? (:items tile))
       (:walked tile)
       (not (:ring (:tags tile)))))

(defn- sinkid-ring [game]
  (if-let [[slot ring] (have game #(and (not (know-id? game %))
                                        (ring? %)) #{:can-remove})]
    (with-reason "drop ring in sink"
      (if-let [{:keys [step]} (navigate game sink?)]
        (or step (remove-use game slot) (->Drop slot))))))

(defn use-features [{:keys [player] :as game}]
  (or (if-let [wow (and (:castle (curlvl-tags game))
                        (not-any? perma-e? (wow-spot game))
                        (find-first (complement :walked) (wow-spot game)))]
        (with-reason "getting WoW"
          (:step (navigate game wow #{:no-traps :no-levitation}))))
      (sinkid-ring game)
      (if (and (have game "Excalibur" #{:can-use})
               (not (farm-level? game)))
        ; TODO remove items from tile
        (if-let [{:keys [step target]} (navigate game kickable-sink?
                                                 #{:adjacent})]
          (with-reason "kick sink"
            (or step
                (if (monster-at game target)
                  (fidget game (curlvl game) target))
                (kick game target)))))
      (if (altar? (at-player game))
        (if-let [[slot item] (have game {:can-remove true
                                         :bagged true :know-buc false})]
          (with-reason "dropping things on altar"
            (or (unbag game slot item)
                (remove-use game slot)
                (->Drop slot (:qty item))))))
      ; TODO altars not on current level
      (if (have game (partial want-buc? game) #{:can-remove :bagged})
        (with-reason "going to altar" (:step (navigate game altar?))))
      (if-let [{:keys [step]}
               (and (or (not (:castle (curlvl-tags game)))
                        (< (:ac player) -3))
                    (navigate game (every-pred throne? (comp empty? :items))
                              #{:explored}))]
        ; TODO remove items from tile
        (or (with-reason "going to throne" step)
            ; TODO drop gold
            (with-reason "sitting on throne" ->Sit)))
      (if-let [drawbridge (find-first drawbridge? (tile-seq (curlvl game)))]
        (with-reason "destroy drawbridge"
          (if-let [[slot _] (have game "wand of striking")]
            (if-let [{:keys [step]}
                     (navigate game #(and (= 3 (distance drawbridge %))
                                          (in-line drawbridge %)))]
              (or step (->ZapWandAt slot (towards player drawbridge)))))))))

(defn- medusa-spot [level]
  (if (:medusa-1 (:tags level))
    (at level {:x 38 :y 11})
    (if (:medusa-2 (:tags level))
      (at level {:x 70 :y 11}))))

(defn- medusa-action [{:keys [player] :as game} medusa]
  (with-reason "killing medusa"
    (if-let [[slot _] (have game blind-tool #{:noncursed})]
      (if (and (= (:dlvl game) (:dlvl medusa)) (medusa-spot medusa))
        (or (if (> 25 (distance player {:x 38 :y 11}) 2)
              (go-down game medusa))
            (:step (navigate game (medusa-spot medusa) #{:adjacent}))
            (if (adjacent? player (medusa-spot medusa))
              ->Search))
        (if (and (stairs-up? (at-player game))
                 (= (prev-dlvl (:dlvl game)) (:dlvl medusa)))
          (or (if (not (visited-stairs? (at-player game)))
                (make-use game slot))
              ->Ascend))))))

(defn kill-medusa [bh]
  (reify ActionHandler
    (choose-action [this game]
      (if-let [medusa (and (not (reflection? game))
                           (not (:polymorphed (:player game)))
                           (get-level game :main :medusa))]
        (if ((fnil pos? 0) (:searched (medusa-spot medusa)))
          (do (deregister-handler bh this) nil)
          (medusa-action game medusa))))))

(defn safe-zap? [game dir]
  (let [level (curlvl game)]
    (every? #(and ((some-fn corridor? floor? pool? door-open?) %)
                  (not (and (= :sokoban (:branch-id game)) (boulder? %)))
                  (not (monster-at level %)))
            (->> (in-direction level (:player game) dir)
                 (iterate #(in-direction level % dir))
                 (take 7)))))

(defn itemid [{:keys [player] :as game}]
  (or (if (can-engrave? game)
        (if-let [[slot w] (have game #(and (nil? (:engrave (item-id game %)))
                                           (not (tried? game %))
                                           (wand? %)) #{:nonempty})]
          (with-reason "engrave-id wand" w
            (or (:step (navigate game (every-pred engravable?
                                                  (complement perma-e?))))
                (if-not (:engraving (at-player game))
                  (engrave-e game))
                (->Engrave slot "Elbereth" true)))))
      (if-let [[slot wand] (have game #(and (nil? (:target (item-id game %)))
                                            (wand? %)) #{:nonempty})]
        (if-let [dir (find-first (partial safe-zap? game) directions)]
          (with-reason "zap-id wand" wand
            (->ZapWandAt slot dir))))
      (if-let [[slot item] (have game (every-pred (partial should-try? game)
                                                  (complement wand?))
                                 #{:safe-buc :bagged})]
        (with-reason "trying out safe item"
          (or (unbag game slot item)
              (make-use game slot))))
      (if-let [[slot item] (and (:room (at-player game))
                                (shop-inside? (curlvl game) (:player game))
                                (have game #(and (price-id? game %)
                                                 (not (:cost %))
                                                 ((shops-taking %)
                                                  (:room (at-player game))))
                                      #{:bagged}))]
        (with-reason "price id (sell)"
          (or (unbag game slot item)
              (remove-use game slot)
              (->Drop slot))))
      (if-let [shoptype (->> (have-all game #(price-id? game %) #{:bagged})
                             (mapcat (comp (partial shops-taking) val))
                             (some (curlvl-tags game)))]
        (with-reason "visit shop" shoptype "to price id items"
          (:step (navigate game #(and (= shoptype (:room %))
                                      (shop-inside? (curlvl game) %))))))))

(defn- want-name? [item]
  (and (ambiguous-appearance? item) (not (gem? item)) (not (candle? item))))

(defn shop [{:keys [player] :as game}]
  (let [want? #(and (:cost %) (or (want-buy? game %)
                                  (want-name? %)))]
    (or (if-let [item (find-first want? (:items (at-player game)))]
          (with-reason "want to call item"
            (->PickUp (:label item))))
        (with-reason "visit shop for items"
          (:step (navigate game #(some want? (:items %))))))))

(defn- baggable? [item]
  (or (scroll? item)
      (potion? item)))

(defn bag-items [game]
  (with-reason "bag items"
    (if-let [[bag-slot bag] (and (not= :apply (typekw (:last-action game)))
                                 (have game bag?))]
      (if-let [baggables (keys (have-all game baggable?))]
        (put-in bag-slot (zipmap baggables (repeat nil)))))))

(defn- id-priority [game item]
  (let [know? (know-id? game item)
        price (:price (item-id game item))]
    (cond-> 0
      ((some-fn food? rocks? gold? candle?) item) (- 10)
      (not know?) (+ 2)
      (nil? (:buc item)) inc
      (and (or (not know?)
               (and (nil? (:buc item))
                    (wearable? item)))
           (some (desired game) (possible-names game item))) (+ 4)
      (and (#{book candelabrum} (item-name game item))
           (nil? (:buc item))) (+ 5)
      (and (not know?)
           (not (#{100 200} price))
           (scroll? item)) (+ 10)
      (and (not know?) (not (have game #{"silver dragon scale mail"
                                         "shield of reflection"}))
           (could-be? game "amulet of reflection" item)) (+ 10)
      (and (not know?)
           (could-be? game "scroll of remove curse" item)) (+ 10)
      (and (not know?)
           (could-be? game "scroll of genocide" item)) (+ 8)
      (and (not know?)
           (could-be? game "speed boots" item)) (+ 8)
      (and (not know?)
           (= 200 price)
           (or (could-be? game "ring of levitation" item)
               (could-be? game "ring of regeneration" item))) (+ 8)
      (and (not know?)
           (wand? item) (not= 150 price)) (+ 5)
      (and (not know?)
           ((some-fn ring? amulet?) item)) (+ 5))))

(defn- want-id
  ([game] (want-id game false))
  ([game bagged?]
   (->> (inventory game bagged?)
        (remove (every-pred (comp some? :buc) (comp some? :enchantment)))
        (sort-by (comp (partial id-priority game) val))
        reverse)))

#_(def game @(:game bothack.main/a))
#_(log/debug "want identified\n"
              (map (comp #(str % \newline)
                         (juxt (partial id-priority game)
                               :label) val) (want-id game :bagged)))

(defn- recharge [game slot wand]
  (if-not (or (charged? wand)
              (and (= "wand of wishing" (item-name game wand))
                   (recharged? wand)))
    (if-let [[s item] (or (have game "scroll of charging" #{:blessed :bagged})
                          (have game "scroll of charging" #{:wished :bagged})
                          (have game "scroll of charging" #{:bagged}))]
      (with-reason "recharge"
        (or (unbag game s item)
            (with-handler
              (reify ChargeWhatHandler
                (charge-what [_ _] slot))
              (->Read s)))))))

(defn use-items [{:keys [player] :as game}]
  (if-not (shop? (at-player game))
    (let [bagged? (if (less-than? 52 (inventory game)) :bagged)]
      (or (if-let [[excal i] (have game "Excalibur" #{:can-use})]
            (if-let [[scroll item] (and (safe-enchant? i)
                                        (have game "scroll of enchant weapon"
                                              #{bagged? :noncursed}))]
              (or (with-reason "enchant excal"
                    (or (unbag game scroll item)
                        (make-use game excal)
                        (->Read scroll))))))
          (if-let [[slot item] (have game #{"potion of gain level"}
                                     #{(if (farming? game) :safe-buc bagged?)})]
            (with-reason "helpful potion"
              (or (unbag game slot item)
                  (->Quaff slot))))
          (if-let [[slot item] (and (less-than? 50 (inventory game))
                                    (have game "magic lamp"
                                          #{:noncursed bagged?}))]
            (with-reason "rubbing lamp"
              (or (unbag game slot item)
                  (bless game slot)
                  (->Rub slot))))
          (if-let [[slot geno] (have game "scroll of genocide"
                                     #{bagged? :safe-buc})]
            (with-reason "geno"
              (or (unbag game slot geno)
                  (bless game slot)
                  (->Read slot))))
          (if-let [[slot wow] (and (less-than? 50 (inventory game))
                                   (have game "wand of wishing" #{bagged?}))]
            (with-reason "wish"
              (or (unbag game slot wow)
                  (recharge game slot wow)
                  (->ZapWand slot))))
          (if-let [[slot wod] (have game "wand of death" {:can-use false})]
            (with-reason "recharge WoD"
              (recharge game slot wod)))
          (if-let [[slot item] (have game "scroll of identify" #{bagged?})]
            (when-let [want (seq (want-id game bagged?))]
              (with-reason "identify" (first want)
                (if (and (or (not (container? item))
                             (less-than? 50 (inventory game)))
                         (or (< 6 (id-priority game (val (first want))))
                             (and (< 0 (id-priority game (val (first want))))
                                  (or (more-than? 45 (inventory game))
                                      (< 5 (have-sum game "scroll of identify"
                                                     #{:bagged}))
                                      (burdened? player)))))
                  (or (unbag game slot item)
                      (if (less-than? 51 (inventory game))
                        (keep-first (fn [[slot item]]
                                      (if (< 0 (id-priority game item))
                                        (unbag game slot item))) want))
                      (->Read slot))))))
          (if-let [[slot item] (have game #{"potion of extra healing"
                                            "potion of full healing"}
                                     #{bagged?})]
            (if (= (:hp player) (:maxhp player))
              (with-reason "improve maxhp"
                (or (unbag game slot item)
                    (->Quaff slot)))))))))

(defn choose-identify [game options]
  (let [want (want-id game)]
    (find-first options (keys want))))

(defn- respond-geno [bh]
  (let [geno-classes (atom (list ";" "L" "R" "c" "n" "m" "N" "q" "T" "U"))
        geno-types (atom (list "master mind flayer" "mind flayer"
                               "electric eel" "disenchanter" "minotaur"
                               "green slime" "golden naga" "gremlin"))
        throne-geno (atom (list "minotaur" "disenchanter" "green slime"
                                "golden naga" "gremlin"))
        next! (fn [g]
                (when-let [res (peek @g)]
                  (swap! g pop)
                  res))]
    (reify GenocideHandler
      (genocide-class [_ _] (next! geno-classes))
      (genocide-monster [_ _]
        (if (= :sit (typekw (:last-action @(:game bh))))
          (next! throne-geno)
          (next! geno-types))))))

(defn random-unihorn [game]
  (if-let [[slot _] (and (zero? (rand-int 200)) (have-unihorn game))]
    (with-reason "randomly use unihorn" (->Apply slot))))

(defn get-protection [{:keys [player] :as game}]
  (if (and (want-protection? game)
           (> (available-gold game) (* 400 (:xplvl player)))
           (or (not (below-medusa? game)) (in-gehennom? game)))
    (with-reason "get protection"
      (or (if-let [priest (find-first (every-pred priest? :peaceful
                                                  (partial adjacent? player))
                                      (curlvl-monsters game))]
            (->Contribute (towards player priest) (* 400 (:xplvl player))))
          (if (or (:temple (:tags (curlvl game))) (:votd (:tags (curlvl game))))
            (or (some->> (curlvl-monsters game)
                         (find-first (every-pred priest? :peaceful))
                         (navigate game) :step)
                (:step (navigate game (every-pred altar? temple?)))))
          (if-let [target (find-first (comp :temple :tags) (level-seq game))]
            (seek-level game (:branch-id target) (:dlvl target)))))))

#_(defn rub-id [{:keys [game] :as bh}]
  (let [torub (atom 8)]
    (reify ActionHandler
      (choose-action [this game]
        (if (know-appearance? game "magic lamp")
          (deregister-handler bh this))
        (when-let [[slot item] (have game (partial could-be? game "magic lamp")
                                     #{:noncursed})]
          (if (zero? (swap! torub dec))
            (swap! game identify-slot slot "oil lamp"))
          (with-reason "rub-id" (->Rub slot)))))))

(defn handle-drowning [{:keys [player] :as game}]
  (if (and (:grabbed player) (some pool? (neighbors (curlvl game) player)))
    (let [level (curlvl game)
          [drowner & _ :as drowners] (filter #(and (pool? %)
                                                   (monster-at level %))
                                             (neighbors level player))]
      (with-reason "grabbed - avoid drowning"
        (if (and drowner (pos? (rand-int 60)))
          (or (pray game)
              (if-let [[slot ring] (have-levi-on game)]
                (if (and (ring? ring) (walkable? (at-player game)))
                  (remove-use game slot)))
              (engrave-e game :perma)
              (if-let [[slot wand] (and (less-than? 2 drowners)
                                        (or (have game "wand of teleportation")
                                            (have game "wand of cold")))]
                (->ZapWandAt slot (towards player drowner)))
              (engrave-e game)))))))

(defn- maybe-boulder? [level tile]
  (or (boulder? tile)
      (#{\H \X \E} (:glyph (monster-at level tile)))))

(defn farm-spot?
  ([game tile]
   (let [level (curlvl game)]
     (or (farm-spot? (at level tile))
         (->> (neighbors level tile)
              (filter (partial maybe-boulder? level))
              (more-than? 6)))))
  ([tile]
   (= "Elbereth*" (:engraving tile))))

(defn farm-sink [game]
  (or (find-first sink?
                  (including-origin neighbors (curlvl game) (:player game)))
      (find-first sink? (tile-seq (curlvl game)))))

(defn farm-spot [game]
  (find-first farm-spot?
              (including-origin neighbors (curlvl game) (:player game))))

(defn farm-spot* [game]
  (find-first (partial farm-spot? game)
              (including-origin neighbors (curlvl game) (:player game))))

(defn farming? [{:keys [player] :as game}]
  (and (some? (:x (:player game)))
       (or (some? (farm-spot game))
           (let [level (curlvl game)]
             (and (some sink? (including-origin neighbors level player))
                  (->> (neighbors (curlvl game) player)
                       (filter (partial maybe-boulder? level))
                       (more-than? 3)))))))

(defn farm-done? [game]
  (or (get-branch game :wiztower)
      (and (< 4000000 (:score game))
           (or (<= 3 (:wishes game))
               (and (or (have-levi game)
                        (have game "speed boots")
                        (< 65000 (:turn game)))
                    (or (have-mr? game)
                        ((:genocided game) "L")
                        (< 65000 (:turn game)))
                    (reflection? game)
                    (> -10 (:ac (:player game)))))
           (have game bag?)
           (<= 6 (have-sum game "scroll of remove curse" #{:bagged}))
           (have game "scroll of identify" #{:bagged})
           (or (have-candles? game) (not (get-level game :main :castle))))))

(defn init-farm? [game]
  (and (not (farm-done? game))
       (<= 7 (:xplvl (:player game)))
       (know-appearance? game "scroll of identify")
       (have-pick game)
       (have-unihorn game)
       (have game farm-tool)
       (have game #{"wand of lightning" "wand of fire"})
       (< 2001 (nutrition-sum game))
       (have game "scroll of earth" #{:bagged :noncursed})))

(defn farm-rect [game sink]
  (filter #(< 2 (:y %) 21)
          (rectangle (position (- (:x sink) 4)
                               (- (:y sink) 4))
                     (position (+ (:x sink) 4)
                               (+ (:y sink) 4)))))

(defn farm-clear? [tile]
  ((not-any-fn? :walked :undiggable shop? trap? pool?) tile))

(defn farm-level? [game]
  (at-level? game (get-level game :main :sink)))

(defn farm-init [game]
  (with-reason "initiating farm"
    (if-let [sink-level (get-level game :main :sink)]
      (if-not (at-level? game sink-level)
        (seek-level game :main (:dlvl sink-level))
        (if-let [sink (farm-sink game)]
          (or (if (some farm-clear? (farm-rect game sink))
                (with-reason "digging out sink surroundings"
                  (:step (navigate game
                                   (every-pred (comp (set (farm-rect game sink))
                                                     position)
                                               farm-clear?)))))
              (seek game #(and (= (distance-manhattan % sink) 1)
                               (engravable? %)))
              (if-let [[slot item] (have game "scroll of earth" #{:bagged})]
                (or (unbag game slot item)
                    (->Read slot)))))))))

(defn- farm-spot-opposite [game]
  (in-direction (curlvl game) (farm-sink game)
                (towards (farm-spot* game) (farm-sink game))))

(defn farm-init-move [{:keys [player] :as game}]
  (if (not (perma-e? (at-player game)))
    (let [sink (farm-sink game)]
      (or (if (->> (neighbors (curlvl game) player)
                   (filter boulder?)
                   (more-than? 7))
            (->Move (towards player sink)))
          (if-not (or (:tags (:pudding sink))
                      (monster-at game sink))
            (kick game (towards player sink)))))))

(defn- reap-turn? [{:keys [turn player] :as game} splits]
  (or (farm-done? game)
      (and (< 110 splits)
           (-> turn (mod 1000) (quot 100) (mod (if (< 160 splits) 4 8)) zero?)
           (-> turn (mod 100) (< 60)))))

(defn- heal-turn? [{:keys [turn score] :as game} splits kills]
  (and (not (farm-done? game))
       (< 25 splits)
       (-> turn (mod 1000) (quot 100) (mod 3) zero?)
       (or (and (< score 2000000) (< kills 130))
           (-> turn (mod 100) (< 40)))))

(defn farm-wield [{:keys [player] :as game} splits]
  (if-let [[slot item] (if (or (and (hungry? player)
                                    (not (have game food?))
                                    (not (can-pray? game)))
                               (reap-turn? game splits))
                         (have game "Excalibur")
                         (have-key game))]
    (if-not (:wielded item)
      (->Wield slot))))

(defn farm-spot-move [{:keys [player] :as game}
                      {:keys [kills splits] :as state}]
  (or (if-let [m (and (not (unihorn-recoverable? game))
                      (some #(if-let [m (monster-at game %)]
                               (if-not (pudding? m) m))
                            (neighbors player)))]
        (with-reason "killing non-pudding"
          (or (wield-weapon game)
              (hit game (curlvl game) m))))
      (handle-impairment game)
      (farm-init-move game)
      (use-items game)
      (reequip game)
      (examine-containers game)
      (examine-containers-here game)
      (consider-items-here game)
      (bag-items game)
      (if (and (or (and (< (if (have game food?)
                             200
                             75)
                           (- (:turn game)
                              (or (:walked (farm-sink game)) 0)))
                        (or (hungry? player)
                            (= \? (:glyph (farm-sink game)))))
                   (< 1500 (- (:turn game)
                              (or (:walked (farm-sink game)) 0))))
               (not (monster-at game (farm-sink game))))
        (:step (navigate game (farm-sink game))))
      (farm-wield game splits)
      (cond
        (heal-turn? game splits kills) (with-reason "letting puddings heal"
                                         (search 8))
        (> splits 160) (if-not (and (hungry? player) (not (have game food?)))
                         (->FarmAttack (towards player (farm-sink game)) 8)
                         (->Attack (towards player (farm-sink game))))
        (< splits 2) (->Attack (towards player (farm-sink game)))
        (->> (if (< 50 splits) 5 16)
             rand-int zero?) (->Attack (towards player (farm-sink game)))
        (and (<= 2 splits)
             (not (e? (at-player game)))) (engrave-e game :perma :farm)
        :else (with-reason "letting puddings heal" ->Search))))

(defn farm-sink-move [{:keys [player] :as game}]
  (or ;(and (not (e? (at-player game))) (engrave-e game))
      (if-let [m (monster-at game (farm-spot game))]
        (or (wield-weapon game)
            (hit game (curlvl game) m)))
      (if (boulder? (farm-spot-opposite game))
        (seek game (farm-spot-opposite game)))
      (if (or (> 2/3 (/ (:hp player) (:maxhp player)))
              (and (fainting? player) (have game food?)))
        (:step (navigate game (partial farm-spot? game))))
      (consider-items-here game)
      (if (or (know-appearance? game "ring of hunger")
              (< 3000 (nutrition-sum game)))
        (sinkid-ring game))
      (:step (navigate game (partial farm-spot? game)))))

(defn farm-action [game state]
  (with-reason "FARM STATE" "items" (count (inventory game)) "; bagged" (- (count (inventory game :bagged)) (count (inventory game))) "; at sink" (count (:items (farm-sink game))) "; food" (nutrition-sum game) "; prayer" (- (prayer-interval game) 1300) ";" state "; wishes" (:wishes game) "; done?" (farm-done? game)
    (if (farm-spot? game (at-player game))
      (farm-spot-move game state)
      (farm-sink-move game))))

(defn end-farm? [game]
  (and (farm-done? game)
       (not-any? (every-pred pudding? (complement :remembered)
                             #(> 5 (distance % (:player game))))
                 (curlvl-monsters game))))

(defn botched-farm? [game {:keys [last-seen] :as state}]
  (and last-seen
       (not (farm-done? game))
       (< 500 (- (:turn game) last-seen))))

(defn farm [{:keys [game] :as bh}]
  (let [state (atom {:splits 0 :kills 0 :last-seen nil})]
    (reify
      ForceGodHandler
      (force-god [_ _] false)
      ReallyAttackHandler
      (really-attack [_ _] (farming? @game))
      ActionHandler
      (choose-action [this game]
        (when (farming? game)
          (if (some (every-pred pudding? (complement :remembered))
                    (curlvl-monsters game))
            (swap! state assoc :last-seen (:turn game)))
          (if (or (end-farm? game) (botched-farm? game @state))
            (do (if (botched-farm? game @state)
                  (log/warn "farm messed up - abandoning")
                  (log/warn "done farming"))
                (deregister-handler bh this)
                (enhance-all))
            (farm-action game @state))))
      ToplineMessageHandler
      (message [_ msg]
        (if (farming? @game)
          (condp re-seq msg
            #"You kill.*(brown|black) pudding" (swap! state update :kills inc)
            #"divides as you" (swap! state update :splits inc)
            nil))))))

(defn init [{:keys [game] :as bh}]
  (-> bh
      (register-handler priority-bottom (pause-handler bh))
      (register-handler (reify ChooseCharacterHandler
                          (choose-character [this]
                            (deregister-handler bh this)
                            "nvd"))) ; choose a dwarven valk
      (register-handler (reify
                          IdentifyWhatHandler
                          (identify-what [_ options]
                            (choose-identify @game options))
                          OfferHandler
                          (offer-how-much [_ _]
                            (bribe-demon (:last-topline @(:game bh))))))
      (register-handler (reify VaultGuardHandler
                          (who-are-you [_ _]
                            (if (have @(:game bh) #{"pick-axe"
                                                    "scroll of teleportation"
                                                    "wand of teleportation"}
                                      #{:can-use})
                              "Croesus"))))
      (register-handler (respond-geno bh))
      (register-handler (reify MakeWishHandler
                          (make-wish [_ _]
                            (wish @game))))
      (register-handler (reify AboutToChooseActionHandler
                          (about-to-choose [_ game]
                            (if (or (= :inventory (typekw (:last-action* game)))
                                    (nil? @desired*)
                                    (some-> game :last-state at-player shop?)
                                    (shop? (at-player game)))
                              ; expensive (~3 ms)
                              (reset! desired* (currently-desired game))))))
      ; expensive action-decision handlers could easily be aggregated and made to run in parallel as thread-pooled futures, dereferenced in order of their priority and cancelled when a decision is made
      (register-handler -99 (reify ActionHandler
                              (choose-action [_ game]
                                (offer-amulet game))))
      (register-handler -16 (reify ActionHandler
                              (choose-action [_ game]
                                (enhance game))))
      (register-handler -15 (name-first-amulet bh))
      (register-handler -13 (reify ActionHandler
                              (choose-action [_ game]
                                (handle-drowning game))))
      (register-handler -11 (reify ActionHandler
                              (choose-action [_ game]
                                (handle-starvation game))))
      (register-handler -10 (detect-portal bh))
      (register-handler -9 (reify ActionHandler
                             (choose-action [_ game]
                               (handle-illness game))))
      (register-handler -8 (farm bh))
      (register-handler -7 (reify ActionHandler
                             (choose-action [_ game]
                               (retreat game))))
      (register-handler -6 (reify ActionHandler
                             (choose-action [_ game]
                               (fight game))))
      (register-handler -5 (reify ActionHandler
                             (choose-action [_ game]
                               (cursed-levi game))))
      (register-handler -4 (kill-medusa bh))
      (register-handler -3 (reify ActionHandler
                             (choose-action [_ game]
                               (handle-impairment game))))
      (register-handler -2 (reify ActionHandler
                            (choose-action [_ game]
                              (fight-covetous game))))
      (register-handler -1 (reify ActionHandler
                            (choose-action [_ game]
                              (reequip game))))
      (register-handler 0 (reify ActionHandler
                            (choose-action [_ game]
                              (reequip-weapon game))))
      (register-handler 1 (reify ActionHandler
                            (choose-action [_ game]
                              (feed game))))
      (register-handler 2 (reify ActionHandler
                            (choose-action [_ game]
                              (consider-items-here game))))
      (register-handler 3 (reify ActionHandler
                             (choose-action [_ game]
                               (recover game :safe))))
      (register-handler 4 (reify ActionHandler
                            (choose-action [_ game]
                              (examine-containers game))))
      (register-handler 5 (reify ActionHandler
                            (choose-action [_ game]
                              (examine-containers-here game))))
      (register-handler 6 (reify ActionHandler
                            (choose-action [_ game]
                              (consider-items game))))
      (register-handler 7 (reify ActionHandler
                            (choose-action [_ game]
                              (use-items game))))
      (register-handler 8 (reify ActionHandler
                            (choose-action [_ game]
                              (random-unihorn game))))
      (register-handler 9 (hunt bh))
      (register-handler 10 (reify ActionHandler
                            (choose-action [_ game]
                              (itemid game))))
      (register-handler 11 (reify ActionHandler
                            (choose-action [_ game]
                              (use-features game))))
      (register-handler 12 (reify ActionHandler
                            (choose-action [_ game]
                              (shop game))))
      (register-handler 13 (reify ActionHandler
                             (choose-action [_ game]
                               (bag-items game))))
      (register-handler 15 (reify ActionHandler
                            (choose-action [_ game]
                              (get-protection game))))
      (register-handler 16 (excal-handler bh))
      (register-handler 17 (reify ActionHandler
                            (choose-action [this game]
                              (rob-peacefuls game))))
      (register-handler 18 (reify
                             AboutToChooseActionHandler
                             (about-to-choose [this game]
                               (when (farming? game)
                                 (log/warn "farm initiated")
                                 (deregister-handler bh this)))
                             ActionHandler
                             (choose-action [_ game]
                               (if (init-farm? game)
                                 (farm-init game)))))
      (register-handler 19 (reify ActionHandler
                             (choose-action [_ game]
                               (progress game))))))
