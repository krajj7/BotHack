(ns anbf.bots.chobot
  (:require [clojure.tools.logging :as log]
            [flatland.ordered.set :refer [ordered-set]]
            [anbf.anbf :refer :all]
            [anbf.item :refer :all]
            [anbf.itemtype :refer :all]
            [anbf.itemid :refer :all]
            [anbf.handlers :refer :all]
            [anbf.player :refer :all]
            [anbf.pathing :refer :all]
            [anbf.monster :refer :all]
            [anbf.position :refer :all]
            [anbf.game :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.level :refer :all]
            [anbf.tile :refer :all]
            [anbf.delegator :refer :all]
            [anbf.util :refer :all]
            [anbf.behaviors :refer :all]
            [anbf.tracker :refer :all]
            [anbf.actions :refer :all]))

(def hostile-dist-thresh 5)

(defn- hostile-threats [{:keys [player] :as game}]
  (->> (curlvl-monsters game)
       (filter #(and (hostile? %)
                     (or (adjacent? player %)
                         (and (not (and (blind? player) (:remembered %)))
                              (> 10 (- (:turn game) (:known %)))
                              (> hostile-dist-thresh (distance player %))
                              (not (blind? player))
                              (not (hallu? player))
                              (not (digit? %))))))
       set))

(defn- threat-map [game]
  (into {} (for [m (hostile-threats game)] [(position m) m])))

(defn enhance [game]
  (if (:can-enhance (:player game))
    (enhance-all)))

(defn- handle-starvation [{:keys [player] :as game}]
  (or (if (weak? player)
        (if-let [[slot food] (have game (every-pred (partial can-eat? player)
                                                    (complement tin?))
                                   {:bagged true})]
          (with-reason "weak or worse, eating" food
            (or (unbag game slot food)
                (->Eat slot)))))
      (if (and (fainting? (:player game))
               (can-pray? game))
        (with-reason "praying for food" ->Pray))))

(defn- handle-illness [{:keys [player] :as game}]
  (or (if-let [[slot _] (and (unihorn-recoverable? game)
                             ; rest can wait
                             (some (:state player) #{:conf :stun :ill :blind})
                             (have-unihorn game))]
        (with-reason "applying unihorn to recover" (->Apply slot)))
      (if (:ill (:state player))
        (with-reason "fixing illness"
          (or (if-let [[slot _] (have game "eucalyptus leaf" {:noncursed true})]
                (->Eat slot))
              (if-let [[slot item] (or (have game "potion of healing"
                                          {:buc :blessed :bagged true})
                                    (have game #{"potion of extra healing"
                                                 "potion of full healing"}
                                          {:noncursed true} :bagged true))]
                (or (unbag game slot item)
                    (->Quaff slot))))))))

(defn name-first-amulet [anbf]
  (reify ActionHandler
    (choose-action [this game]
      (when-let [[slot _] (have game "Amulet of Yendor")]
        (deregister-handler anbf this)
        (with-reason "naming the real amulet"
          (->Name slot "REAL"))))))

(defn real-amulet? [item]
  (and (= "Amulet of Yendor" (:name item))
       (= "REAL" (:specific item))))

(defn get-amulet [game]
  (if-not (have game real-amulet?)
    (with-reason "searching for the amulet"
      (or (explore game)
          (search-level game 1) ; if Rodney leaves the level with it we're screwed
          (seek game stairs-up?)))))

(defn full-explore [game]
  (if-not (get-level game :main :sanctum)
    (or (explore game :main :oracle)
        (explore game :mines :minetown)
        ;(explore game :sokoban)
        (explore game :main :quest)
        (explore game :mines)
        (explore game :quest)
        (explore game :vlad)
        (explore game :main)
        (explore game :wiztower)
        (invocation game))))

(defn seek-high-altar [game]
  (with-reason "seeking unknown high altar"
    (seek game (every-pred altar? (complement :walked)))))

(defn endgame? [game]
  (get-level game :main :sanctum))

(defn progress [game]
  (if-not (endgame? game)
    (full-explore game)
    (or (get-amulet game)
        (visit game :astral)
        (seek-high-altar game))))

(defn- pause-condition?
  "For debugging - pause the game when something occurs"
  [game]
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
  (ordered-set "Grayswandir" "Excalibur" "Mjollnir" "Stormbringer"
               "katana" "long sword"))

(def desired-suit
  (ordered-set "gray dragon scale mail" "silver dragon scale mail" "dwarwish mithril-coat" "elven mithril-coat" "scale mail"))

(def desired-boots
  (ordered-set "speed boots" "iron shoes"))

(def desired-shield
  (ordered-set "shield of reflection" "small shield"))

(def desired-cloak
  (ordered-set "cloak of magic resistance" "cloak of displacement" "oilskin cloak" "cloak of protection" "cloak of invisibility"))

(def desired-helmet
  (ordered-set "helm of telepathy" "helm of brilliance" "dwarvish iron helm" "orcish helm"))

(def desired-gloves
  (ordered-set "gauntlets of power" "gauntlets of dexterity" "leather gloves"))

(def blind-tool (ordered-set "blindfold" "towel"))

(def always-desired #{"magic lamp" "magic marker" "wand of wishing" "wand of death"})

(def desired-items
  [(ordered-set "pick-axe" #_"dwarvish mattock") ; currenty-desired presumes this is the first category
   (ordered-set "skeleton key" "lock pick" "credit card")
   (ordered-set "ring of levitation" "boots of levitation")
   #{"ring of slow digestion"}
   #{"ring of conflict"}
   #{"ring of regeneration"}
   #{"ring of invisibility"}
   #{"Orb of Fate"}
   blind-tool
   #{"brass lantern" "oil lamp"}
   #{"unicorn horn"}
   #{"Candelabrum of Invocation"}
   #{"Bell of Opening"}
   #{"Book of the Dead"}
   #{"lizard corpse"}
   desired-cloak
   desired-suit
   desired-shield
   desired-boots
   desired-helmet
   #{"amulet of reflection"}
   #{"amulet of life saving"}
   #{"wand of fire"}
   #{"wand of lightning"}
   #{"wand of teleportation"}
   #{"wand of striking"}
   #{"wand of digging"}
   desired-weapons])

(defn entering-shop? [game]
  (some->> (nth (:last-path game) 0) (at-curlvl game) shop?))

(defn desired-food [game]
  (let [min-nw (if (< 2000 (nutrition-sum game))
                 (nw-ratio-avg game)
                 24)]
    (for [food (:food item-kinds)
          :when (and (not (egg? food))
                     (not (corpse? food))
                     (> (nw-ratio food) min-nw))]
      (:name food))))

(defn desired-throwables [game]
  (let [amt-daggers (have-sum game dagger? {:noncursed true})
        amt-ammo (have-sum game (some-fn dart? ammo?) {:noncursed true})
        amt-rocks (have-sum game rocks? {:noncursed true})]
    (cond
      (< 5 amt-daggers) []
      (< 6 amt-ammo) daggers
      (< 6 amt-rocks) (concat daggers ammo)
      :else (concat daggers ammo ["rock"]))))

(defn currently-desired
  "Returns the set of item names that the bot currently wants.
  Assumes the bot has at most 1 item of each category."
  [game]
  (loop [cs (if (or (entering-shop? game) (shop? (at-player game)))
              (rest desired-items) ; don't pick that pickaxe back up
              desired-items)
         res always-desired]
    (if-let [c (first cs)]
      (if-let [[slot i] (have game c)]
        (recur (rest cs)
               (into (if (cursed? i)
                       (conj res (:name i))
                       res)
                     (take-while (partial not= (item-name game i)) c)))
        (recur (rest cs) (into res c)))
      (as-> res res
        (into res (desired-food game))
        (into res (desired-throwables game))
        (if-let [sanctum (get-level game :main :sanctum)]
          (if (and (not (have game real-amulet?))
                   (:seen (at sanctum 20 11)))
            (conj res "Amulet of Yendor"))
          res)))))

(defn- handle-impairment [{:keys [player] :as game}]
  (or (if (:lycantrophy player)
        (if-not (in-gehennom? game)
          (with-reason "curing lycantrophy" ->Pray)))
      (if-let [[slot _] (and (unihorn-recoverable? game)
                             (have-unihorn game))]
        (with-reason "applying unihorn to recover" (->Apply slot)))
      (if-let [[slot _] (have game blind-tool {:in-use true :noncursed true})]
        (with-reason "unblinding self"
          (->Remove slot)))
      (if (or (impaired? player) (:polymorphed player))
        (with-reason "waiting out impairment" (->Repeated (->Wait) 10)))))

(defn- take-cursed? [game item]
  (or (#{"levitation boots" "speed boots" "water walking boots" "cloak of displacement" "cloak of invisibility" "cloak of magic resistance" "cloak of protection" "gauntlets of dexterity" "gauntlets of power" "helm of brilliance" "helm of opposite alignment" "helm of telepathy" "shield of reflection"  "bag of holding" "unicorn horn"} (item-name game item))
      ((some-fn ring? amulet? scroll? potion? tool? artifact?) item)))

(defn- worthwhile? [game item]
  ; TODO negative enchantment
  (and (not= "empty" (:specific item))
       (or (not= :cursed (:buc item))
           (take-cursed? game item))))

(defn consider-items [game]
  (let [desired (currently-desired game)
        to-take? #(or (real-amulet? %)
                      (and (desired (item-name game %))
                           (not= "empty" (:specific %))
                           (can-take? %)
                           (worthwhile? game %)))]
    (or (if-let [to-get (seq (for [item (lootable-items (at-player game))
                                   :when (to-take? item)]
                               (:label item)))]
          (with-reason "looting desirable items"
            (without-levitation game
              (take-out \. (reduce #(assoc %1 %2 nil) {} to-get))))
          (log/debug "no desired lootable items"))
        (if-let [to-get (seq (for [item (:items (at-player game))
                                   :when (to-take? item)]
                               (:label item)))]
          (with-reason "getting desirable items"
            (without-levitation game
              (->PickUp (->> to-get set vec))))
          (log/debug "no desired items here"))
        (when-let [{:keys [step target]}
                   (navigate game #(some to-take? (concat (:items %)
                                                          (lootable-items %))))]
          (with-reason "want item at" target step))
        (log/debug "no desirable items anywhere"))))

(defn uncurse-weapon [game]
  (if-let [[_ weapon] (wielding game)]
    (if-let [[slot scroll] (and (cursed? weapon)
                                (have game "scroll of remove curse"
                                      {:noncursed true :bagged true}))]
      (with-reason "uncursing weapon" (:label weapon)
        (or (unbag game slot scroll)
            (->Read slot))))))

(defn- wield-weapon [{:keys [player] :as game}]
  (if-let [[slot weapon] (some (partial have game) desired-weapons)]
    (if-not (or (:wielded weapon) (not (can-use? player weapon)))
      (or (uncurse-weapon game)
          (with-reason "wielding better weapon -" (:label weapon)
            (->Wield slot))))))

(defn- wear-armor [{:keys [player] :as game}]
  (first (for [category [desired-shield desired-boots
                         desired-suit desired-cloak
                         desired-helmet desired-gloves]
               :let [[slot armor] (some (partial have game) category)]
               :when (and armor (not (:in-use armor)) (can-use? player armor))]
           (with-reason "wearing better armor"
             (make-use game slot)))))

(defn light? [game item]
  (let [id (item-id game item)]
    (and (not= "empty" (:specific item))
         (= :light (:subtype id))
         (= :copper (:material id)))))

(defn bless-gear [game]
  (or (if-let [[slot item] (have game #{"Orb of Fate" "unicorn horn"
                                        "luckstone" "bag of holding"}
                                 {:nonblessed true :know-buc true})]
        (if-let [[water-slot water] (have game holy-water? {:bagged true})]
          (or (unbag game water-slot water)
              (with-reason "blessing" item
                (->Dip slot water-slot)))))
      (if-let [[_ item] (have game (every-pred cursed? :in-use))]
        (if-let [[slot scroll] (have game "scroll of remove curse"
                                     {:noncursed true :bagged true})]
          (with-reason "uncursing" (:label item)
            (or (unbag game slot scroll)
                (->Read slot)))))))

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
  (if-let [[slot item] (have game (every-pred :lit (partial light? game)))]
    (if (and (not= "magic lamp" (item-name game item))
             (not (want-light? game level)))
      (with-reason "saving energy" (->Apply slot)))
    (if (want-light? game level)
      (or (if-let [[slot lamp] (have game "magic lamp")]
            (with-reason "using magic lamp" (->Apply slot)))
          (if-let [[slot lamp] (have game (partial light? game))]
            (with-reason "using any light source" (->Apply slot)))))))

(defn remove-rings [{:keys [player] :as game}]
  (or (if-let [[slot _] (have game "ring of invisibility" {:in-use true})]
        (with-reason "don't need invis"
          (remove-use game slot)))
      (if-let [[slot _] (and (= (:hp player) (:maxhp player))
                            (have game "ring of regeneration" {:in-use true}))]
        (with-reason "don't need regen"
          (remove-use game slot)))))

(defn reequip [game]
  (let [level (curlvl game)
        tile-path (mapv (partial at level) (:last-path game))
        step (first tile-path)
        branch (branch-key game)]
    (or (bless-gear game)
        (wear-armor game)
        (remove-rings game)
        (if (and (not= :wield (some-> game :last-action typekw))
                 step (not (:dug step))
                 (every? walkable? tile-path))
          (if-let [[slot item] (and (#{:air :fire :earth} branch)
                                    (not-any? portal? (tile-seq level))
                                    (have game real-amulet?))]
            (if-not (:in-use item)
              (with-reason "using amulet to search for portal"
                (->Wield slot)))
            (with-reason "reequip - weapon"
              (wield-weapon game))))
        ; TODO multidrop
        (if-not (shop? (at-player game))
          (if-let [[slot _] (have game #(not (worthwhile? game %)))]
            (with-reason "dropping junk" (->Drop slot))))
        (use-light game level)
        (if-let [[slot _] (and (not (needs-levi? (at-player game)))
                               (not (#{:water :air} branch))
                               (not-any? needs-levi? tile-path)
                               (have-levi-on game))]
          (with-reason "reequip - don't need levi"
            (remove-use game slot))))))

(defn- bait-wizard [game level monster]
  (if (and (= :magenta (:color monster)) (= \@ (:glyph monster))
           (not= :water (branch-key game))
           ((some-fn pool? lava?) (at level monster)))
    (with-reason "baiting possible wizard away from water/lava"
      ; don't let the book fall into water/lava
      (or (:step (navigate game #(every? (not-any-fn? lava? pool?)
                                         (neighbors level %))))
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

(defn have-throwable [game]
  (or (have game (some-fn dagger? short-sword?))
      (have game (some-fn dart? ammo?))
      (have game rocks?)))

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
          (if-let [[slot _] (have game blind-tool {:noncursed true})]
            (->PutOn slot))
          (ranged game monster)))))

(defn corrodeproof-weapon [game]
  (have game (every-pred weapon? (some-fn artifact? :proof)) {:noncursed true}))

(defn hit-corrosive [game monster]
  (with-reason "hitting corrosive monster" monster
    (if (corrosive? monster)
      (or (if-let [[slot item] (have game corrodeproof-weapon)]
            (if-not (:in-use item)
              (->Wield slot))
            (if (wielding game)
              (->Wield \-)))
          (->Move (towards (:player game) monster))))))

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
          (or (hit-corrosive game monster)
              (hit-floating-eye game monster)
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
      (<= (/ hp maxhp) 1/3)))

(defn can-ignore? [game monster]
  (or (passive? monster)
      (unicorn? monster)
      (#{"grid bug" "newt" "leprechaun"} (typename monster))))

(defn can-handle? [{:keys [player] :as game} monster]
  (cond
    (= "floating eye" (typename monster)) (or (blind? player)
                                              (reflection? game)
                                              (free-action? game)
                                              (have-throwable game)
                                              ; TODO used throwables blocked
                                              (have game blind-tool
                                                    {:noncursed true}))
    (#{"spotted jelly"
       "ochre jelly"} (typename monster)) (have game corrodeproof-weapon)
    :else true))

(defn engrave-e [{:keys [player] :as game}]
  (let [tile (at-player game)
        append? (e? tile)]
    (if-not (or (not (has-hands? player)) (perma-e? tile) (impaired? player))
      (->Engrave \- "Elbereth" append?))))

(defn retreat [{:keys [player] :as game}]
  (with-reason "retreating"
    (if (low-hp? player)
      (let [level (curlvl game)
            threats (threat-map game)
            adjacent (->> (neighbors player)
                          (keep (partial monster-at level))
                          (filter hostile?))]
        (or (kill-engulfer game)
            (if (and (not (e? (at-player game)))
                     (some (every-pred (complement :fleeing)
                                       (complement ignores-e?)) adjacent)
                     (not-any? ignores-e? adjacent))
              (with-reason "retreat engrave" (engrave-e game)))
            (if (and (empty? threats) (stairs-down? (at-player game)))
              (with-reason "retreated on downstairs"
                ->Search))
            (if-let [{:keys [step target]} (navigate game stairs-up?
                                                     {:walking true
                                                      :explored true
                                                      :no-autonav true})]
              (if (stairs-up? (at-player game))
                (if (and (seq threats) (not= 1 (dlvl game)))
                  (with-reason "retreating upstairs" ->Ascend)
                  (with-reason "prepared to retreat upstairs" ->Search))
                step))
            ; stairs unreachable
            )))))

(defn- safe-hp? [{:keys [hp maxhp] :as player}]
  (or (>= (/ hp maxhp) 9/10)))

(defn- recover [{:keys [player] :as game}]
  (if-not (safe-hp? player)
    (or (if-let [[slot _] (and (free-finger? player)
                               (have game "ring of regeneration"
                                     {:noncursed true}))]
          (with-reason "recover - regen"
            (make-use game slot)))
        (with-reason "recovering - exploring nearby items"
          (:step (navigate game :new-items {:walking true :explored true
                                            :max-steps 10 :no-autonav true})))
        (with-reason "recovering" (->Repeated (->Wait) 10)))))

(defn keep-away? [player m]
  (if-let [montype (typename m)]
    (or (some #(.contains montype %) ["nymph" "rust monster" "disenchanter"])
        (and (= "homunculus" montype) (not (have-intrinsic? player :sleep))))))

(defn targettable
  "Returns a list of first hostile monster for each direction (that can be targetted by throw, zap etc.) and there is no risk of hitting non-hostiles or water/lava for non-rays."
  ([game] (targettable game 6 false))
  ([game ray?] (targettable game 6))
  ([{:keys [player] :as game} max-dist ray?]
   (let [level (curlvl game)]
     (for [dir directions
           :let [tiles (->> player
                            (iterate #(in-direction level % dir))
                            (take-while some?)
                            rest
                            (take max-dist))]
           ; TODO bounce rays
           :when (or ray? (not-any? (some-fn pool? lava?) tiles))
           :let [monsters (->> tiles
                               (take-while (some-fn walkable? boulder?))
                               (keep (partial monster-at level)))]
           :when (and (seq monsters) (every? hostile? monsters))]
       (first monsters)))))

(defn fight [{:keys [player] :as game}]
  (let [level (curlvl game)
        nav-opts {:adjacent true
                  :no-traps true
                  :no-autonav true
                  :walking true
                  :max-steps (if (planes (branch-key game))
                               1
                               hostile-dist-thresh)}]
    (or (kill-engulfer game)
        ; TODO if faster than threats move into a more favourable position
        ; TODO special handling of uniques
        (let [adjacent (->> (neighbors player)
                            (keep (partial monster-at level))
                            (filter hostile?)
                            (remove (partial can-ignore? game)))]
          (or (if (and (not (e? (at-player game)))
                       (or (more-than? 2 (remove ignores-e? adjacent))
                           (some (every-pred (partial keep-away? player)
                                             (complement :fleeing))
                                 adjacent)))
                (with-reason "fight engrave"
                  (engrave-e game)))
              (if-let [monster (or (if (some pool? (neighbors level player))
                                     (find-first drowner? adjacent))
                                   (find-first rider? adjacent)
                                   (find-first unique? adjacent)
                                   (find-first priest? adjacent)
                                   (find-first werecreature? adjacent)
                                   (find-first nasty? adjacent))]
                (hit game level monster))))
        (let [threats (->> (hostile-threats game)
                           (remove (partial can-ignore? game))
                           set)]
          (or (if-let [[slot _] (and (free-finger? player)
                                     (not-any? sees-invisible? threats)
                                     (have game "ring of invisibility"
                                           {:noncursed true}))]
                (with-reason "invis for combat"
                  (make-use game slot)))
              (if-let [m (min-by (partial distance player)
                                 (filter (every-pred (partial keep-away? player)
                                                     (complement :fleeing)
                                                     (complement :remembered)
                                                     :awake)
                                         threats))]
                (if (> 3 (distance player m))
                  (with-reason "trying to keep away from" m
                    (engrave-e game))))
              (if-let [m (find-first #(and (keep-away? player %)
                                           (not (adjacent? player %)))
                                     (targettable game))]
                (with-reason "keep-away monster" m
                  (ranged game m)))
              (when-let [{:keys [step target]} (navigate game threats nav-opts)]
                (let [monster (monster-at level target)]
                  (with-reason "targetting enemy" monster
                    (or (hit game level monster)
                        step))))))
        (let [leftovers (->> (hostile-threats game)
                             (filter (partial can-ignore? game))
                             (filter (partial can-handle? game))
                             set)]
          (when-let [{:keys [step target]} (navigate game leftovers nav-opts)]
            (let [monster (monster-at level target)]
              (with-reason "targetting leftover enemy" monster
                (or (hit game level monster)
                    step))))))))

(defn- bribe-demon [prompt]
  (->> prompt ; TODO parse amount and pass as arg in the scraper, not in bot logic
       (re-first-group #"demands ([0-9][0-9]*) zorkmids for safe passage")
       parse-int))

(defn- pause-handler [anbf]
  (reify FullFrameHandler
    (full-frame [_ _]
      (when (pause-condition? @(:game anbf))
        (log/debug "pause condition met")
        (pause anbf)))))

(defn- feed [{:keys [player] :as game}]
  (if-not (satiated? player)
    (let [beneficial? #(every-pred
                         (partial fresh-corpse? game %)
                         (partial want-to-eat? player))
          edible? #(every-pred
                     (partial fresh-corpse? game %)
                     (partial can-eat? player))]
      (or (if-let [p (navigate game #(and (some (beneficial? %) (:items %))))]
            (with-reason "want to eat corpse at" (:target p)
              (or (:step p)
                  (->> (at-player game) :items
                       (find-first (beneficial? player)) :label
                       ->Eat
                       (without-levitation game)))))
          (if true #_(hungry? player) ; TODO eat tins
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
      (->Offer (key (have game real-amulet?))))))

(defn detect-portal [anbf]
  (reify ActionHandler
    (choose-action [this {:keys [player] :as game}]
      (if-let [[scroll s] (and (= :water (branch-key game))
                               (have game "scroll of gold detection"
                                     {:safe true :bagged true}))]
        (with-reason "detecting portal"
          (or (unbag game scroll s)
              (when (confused? player)
                (deregister-handler anbf this)
                (->Read scroll))
              (if-let [[potion p] (and (not-any? #(and (> 4 (distance player %))
                                                       (hostile? %))
                                                 (curlvl-monsters game))
                                       (have game #{"potion of confusion"
                                                    "potion of booze"}
                                             {:nonblessed true :bagged true}))]
                (with-reason "confusing self"
                  (or (unbag game potion p)
                      (->Quaff potion))))))))))

(defn init [anbf]
  (-> anbf
      (register-handler priority-bottom (pause-handler anbf))
      (register-handler (reify ChooseCharacterHandler
                          (choose-character [this]
                            (deregister-handler anbf this)
                            "nvd"))) ; choose a dwarven valk
      (register-handler (reify
                          OfferHandler
                          (offer-how-much [_ _]
                            (bribe-demon (:last-topline @(:game anbf))))
                          ReallyAttackHandler
                          (really-attack [_ _] false)))
      ; expensive action-decision handlers could easily be aggregated and made to run in parallel as thread-pooled futures, dereferenced in order of their priority and cancelled when a decision is made
      (register-handler -99 (reify ActionHandler
                              (choose-action [_ game]
                                (offer-amulet game))))
      (register-handler -15 (reify ActionHandler
                              (choose-action [_ game]
                                (enhance game))))
      (register-handler -14 (name-first-amulet anbf))
      (register-handler -10 (reify ActionHandler
                              (choose-action [_ game]
                                (handle-starvation game))))
      (register-handler -8 (detect-portal anbf))
      (register-handler -7 (reify ActionHandler
                             (choose-action [_ game]
                               (handle-illness game))))
      (register-handler -6 (reify ActionHandler
                             (choose-action [_ game]
                               (retreat game))))
      (register-handler -4 (reify ActionHandler
                             (choose-action [_ game]
                               (fight game))))
      (register-handler -2 (reify ActionHandler
                             (choose-action [_ game]
                               (handle-impairment game))))
      (register-handler 0 (reify ActionHandler
                            (choose-action [_ game]
                              (reequip game))))
      (register-handler 1 (reify ActionHandler
                            (choose-action [_ game]
                              (feed game))))
      (register-handler 2 (reify ActionHandler
                             (choose-action [_ game]
                               (recover game))))
      (register-handler 4 (reify ActionHandler
                            (choose-action [_ game]
                              (consider-items game))))
      (register-handler 5 (reify ActionHandler
                            (choose-action [_ game]
                              (examine-containers game))))
      (register-handler 6 (reify ActionHandler
                            (choose-action [_ game]
                              (examine-containers-here game))))
      (register-handler 7 (reify ActionHandler
                            (choose-action [_ game]
                              (progress game))))))
