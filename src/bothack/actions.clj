(ns bothack.actions
  (:require [clojure.tools.logging :as log]
            [multiset.core :refer [multiset]]
            [clojure.set :refer [intersection difference]]
            [clojure.string :as string]
            [bothack.handlers :refer :all]
            [bothack.action :refer :all]
            [bothack.player :refer :all]
            [bothack.fov :refer :all]
            [bothack.monster :refer :all]
            [bothack.montype :refer :all]
            [bothack.dungeon :refer :all]
            [bothack.level :refer :all]
            [bothack.tile :refer :all]
            [bothack.item :refer :all]
            [bothack.itemid :refer :all]
            [bothack.position :refer :all]
            [bothack.delegator :refer :all]
            [bothack.util :refer :all]))

(defmacro ^:private defaction [action args & impl]
  `(defrecord ~action ~args
     bothack.util.Type
     (~'typekw [~'_] ~(str->kw action))
     bothack.actions.IAction
     ~@impl))

(def ^:private feature-re #"^(?:You see|There is|You escape)(?: an?| your)?(?: \w+)* (falling rock trap|rolling boulder trap|rust trap|statue trap|magic trap|anti-magic field|polymorph trap|fire trap|arrow trap|dart trap|land mine|teleportation trap|sleeping gas trap|magic portal|level teleporter|bear trap|spiked pit|pit|ladder (?:up|down)|staircase (?:up|down)|spider web|web|ice|opulent throne|pool of water|lowered drawbridge|hole|trap door|fountain|sink|grave|molten lava|doorway|squeaky board|open door|broken door)(?: here| below you)?\.")

(def ^:private trap-disarm-re #"You tear through \w+ web!|You (?:burn|dissolve) \w+ spider web!|You hear a (?:loud|soft) click(?:!|\.)")

(defn- feature-msg-update [tile msg rogue?]
  (or (if-let [align (re-first-group
                       #"There is an altar to [^(]* \(([^)]*)\) here." msg)]
        (assoc tile :feature :altar :alignment (str->kw align)))
      (if (re-seq trap-disarm-re msg)
        (assoc tile :feature :floor))
      (if-let [feature-txt (re-first-group feature-re msg)]
        (assoc tile :feature (case feature-txt
                               "opulent throne" :throne
                               "ice" :ice
                               "doorway" (if rogue? :door-open :floor)
                               "open door" :door-open
                               "broken door" :floor
                               "ladder up" :stairs-up
                               "ladder down" :stairs-down
                               "staircase up" :stairs-up
                               "staircase down" :stairs-down
                               "lowered drawbridge" :drawbridge-lowered
                               "fountain" :fountain
                               "sink" :sink
                               "grave" :grave
                               "molten lava" :lava
                               "pool of water" :pool
                               (trap-names feature-txt))))))

(defn with-reason
  "Updates an action to attach reasoning (purely for debugging purposes)"
  [& reason+action]
  (let [action-idx (dec (count reason+action))
        action (nth reason+action action-idx)
        reason (->> reason+action (take action-idx)
                    (map #(if (string? %) % (pr-str %)))
                    (interpose \space) (apply str))]
    (if-let [a (if (fn? action)
                 (action)
                 action)]
      (update a :reason (fnil conj []) reason))))

(def no-monster-re #"You .*(?:thin air|empty water|empty space)")

(defn recheck-peaceful-status [game monster-selector]
  (->> (curlvl-monsters game)
       (filter monster-selector)
       (remove (comp #{\I \1 \2 \3 \4 \5} :glyph))
       (filter (comp true? :peaceful))
       (reduce #(update-monster %1 %2 assoc :peaceful :update)
               game)))

(defn- direction-trigger [dir]
  (str (or (vi-directions (enum->kw dir))
           (throw (IllegalArgumentException.
                    (str "Invalid direction: " dir))))))

(defaction Attack [dir]
  (trigger [_]
    (str \F (direction-trigger dir)))
  (handler [_ {:keys [game] :as bh}]
    (if (dizzy? (:player @game))
      (swap! game recheck-peaceful-status (partial adjacent? (:player @game))))
    (when-let [target (and (not (dizzy? (:player @game)))
                           (in-direction (:player @game) dir))]
      (swap! game recheck-peaceful-status #(= (position target) (position %)))
      (swap! game update-monster target assoc :awake true)
      (reify ToplineMessageHandler
        (message [_ msg]
          (when (re-seq no-monster-re msg)
            (swap! game update-at target #(if (blank? %)
                                            (assoc % :feature :rock)
                                            (assoc % :pushed true)))
            (swap! game remove-monster target)))))))

(defaction FarmAttack [dir cnt]
  (trigger [_]
    (str (apply str (repeat cnt (str esc esc \F (direction-trigger dir))))
         (apply str (repeat 30 esc))
         "##'"))
  (handler [_ _]))

(defn mark-trap-here [bh]
  (update-at-player-when-known bh update :feature #(if (traps %) % :trap)))

(defn- move-message-handler
  [{:keys [game] :as bh} msg]
  (condp re-seq msg
    #".*: \"Closed for inventory\"" ; TODO possible degradation
    (update-before-action
      bh (fn mark-shop [game]
             (->> (:player game)
                  (neighbors (curlvl game))
                  (find-first door?)
                  (including-origin straight-neighbors (curlvl game))
                  (remove #(= (position (:player game)) (position %)))
                  (reduce #(update-at %1 %2 assoc :room :shop)
                          (add-curlvl-tag game :shop-closed)))))
    #"You crawl to the edge of the pit\.|You disentangle yourself\."
    (swap! game assoc-in [:player :trapped] false)
    #"You fall into \w+ pit!|bear trap closes on your|You stumble into \w+ spider web!|You are stuck to the web\.|You are still in a pit|notice a loose board|You are caught in a bear trap"
    (do (swap! game assoc-in [:player :trapped] true)
        (mark-trap-here bh))
    #"trap door opens|trap door in the .*and a rock falls on you|trigger a rolling boulder|\(little dart|arrow\) shoots out at you|gush of water hits|tower of flame erupts|cloud of gas"
    (mark-trap-here bh)
    #"You feel a strange vibration"
    (do (swap! game add-curlvl-tag :end)
        (update-at-player-when-known bh assoc :vibrating true))
    #"Wait!  That's a .*mimic!"
    (update-before-action
      bh (fn [game]
             (->> (neighbors (curlvl game) (:player game))
                  (filter #(= \m (:glyph %)))
                  (reduce #(update-at %1 %2 assoc :feature nil)
                          game))))
    nil))

(defn update-trapped-status [{:keys [game] :as bh} old-pos]
  (update-on-known-position bh
    #(if (or (= (position (:player %)) old-pos)
             (trap? (at-player @game)))
       %
       (as-> % res
           (assoc-in res [:player :trapped] false)
           (if (-> res :last-state :player :grabbed)
             (assoc-in res [:player :grabbed] false)
             res)))))

(defn- update-narrow [game target]
  (as-> game res
    (assoc-in res [:player :thick] true)
    (if (= :sokoban (branch-key game))
      res
      (reduce #(update-at %1 %2 assoc :feature :rock)
              res
              (intersection (set (straight-neighbors (:player game)))
                            (set (straight-neighbors target)))))))

(def boulder-plug-re #"The boulder triggers and plugs|You no longer feel the boulder|The boulder fills a pit|The boulder falls into and plugs a hole|You hear the boulder fall")

(defaction Move [dir]
  (trigger [_] (direction-trigger dir))
  (handler [_ {:keys [game] :as bh}]
    (let [dir (enum->kw dir)
          got-message (atom false)
          old-game @game
          old-player (:player old-game)
          old-pos (position old-player)
          level (curlvl old-game)
          target (in-direction level old-pos dir)]
      (update-trapped-status bh old-pos)
      (if (and (not (:trapped old-player)) (diagonal dir)
               (or (item? target) (blind? old-player)))
        (update-on-known-position bh
          #(if (and (= (position (:player %)) old-pos)
                    (not (monster-at % target))
                    (not @got-message))
             ; XXX in vanilla (or without the right option) this also happens with walls/rock, but NAO has a message
             (if (and (= :move (typekw (:last-action old-game)))
                      (= dir (:dir (:last-action old-game))))
               (do ;(log/warn "stuck twice on diagonal movement => possibly door at" target)
                   (update-at % target assoc :feature :door-open))
               (do ;(log/warn "retry diagonal move towards" target)
                   %)) ; NH actually seems to ignore a move in some cases
             %)))
      (reify
        ToplineMessageHandler
        (message [_ msg]
          (reset! got-message true)
          (or (move-message-handler bh msg)
              (when-not (dizzy? old-player)
                (condp re-seq msg
                  #"That door is closed"
                  (swap! game update-at target assoc :feature :door-closed)
                  no-monster-re
                  (swap! game remove-monster target)
                  #"You are carrying too much to get through"
                  (swap! game update-narrow target)
                  #"You try to move the boulder, but in vain\."
                  (let [boulder-target (in-direction level target dir)]
                    (if (and (diagonal dir) (item? boulder-target))
                      (swap! game update-at boulder-target
                             assoc :feature :door-open)
                      (swap! game update-at boulder-target
                             assoc :feature :rock)))
                  boulder-plug-re
                  (swap! game update-at
                         (-> old-pos (in-direction dir) (in-direction dir))
                         assoc :feature :floor)
                  #"It's a wall\."
                  (swap! game update-at target
                         #(assoc % :feature (if (blank? %) :rock :wall)))
                  nil))))
        ReallyAttackHandler
        (really-attack [_ _]
          (update-before-action bh update-monster target
                                assoc :peaceful :update)
          nil)))))

(defn adjust-prayer-timeout [game]
  (assoc game :last-prayer (:turn game)))

(defaction Pray []
  (trigger [_] "#pray\n")
  (handler [_ bh]
    (swap! (:game bh) adjust-prayer-timeout)))

(defn- update-searched [{:keys [player turn] :as game} start]
  ; TODO maybe take speed into consideration
  (let [turns (inc (- turn start))]
    (reduce #(update-at %1 %2 update :searched (partial + turns)) game
            (including-origin neighbors player))))

(defaction Search []
  (trigger [_] "s")
  (handler [_ {:keys [game] :as bh}]
    (update-on-known-position bh update-searched (:turn @game))
    nil))

(defaction Wait []
  (trigger [_] ".")
  (handler [_ _]))

(defn- mark-branch-entrance
  "Mark where we ended up on the new level as leading to the branch we came from.  Pets and followers might have displaced us from the stairs which may not be visible, so mark the surroundings too to be sure (but two sets of stairs may be next to each other and this breaks if that happens and there are some followers... too bad)"
  [game tile old-game origin-feature]
  (if (or (= :ludios (branch-key game)) (= "Home 1" (:dlvl game)))
    (update-at game tile assoc :branch-id :main) ; mark portal
    (if (some (some-fn :friendly follower?)
              (mapcat (partial monster-at (curlvl old-game))
                      (neighbors (:player old-game))))
      (->> (including-origin neighbors (curlvl game) tile)
           (remove #(has-feature? % origin-feature))
           (reduce #(update-at %1 %2 assoc :branch-id (branch-key old-game))
                   game))
      (update-at game tile assoc :branch-id (branch-key old-game)))))

(defn stairs-handler [{:keys [game] :as bh}]
  (let [old-game @game
        old-branch (branch-key old-game)
        old-dlvl (:dlvl old-game)
        old-stairs (at-player old-game)
        entered-vlad (atom false)]
    (update-on-known-position bh
      (fn [{new-branch :branch-id new-dlvl :dlvl :as new-game}]
        (log/debug "asc/desc from" old-dlvl "to" new-dlvl "new-branch is" new-branch)
        (if-let [new-stairs (and (not= old-dlvl new-dlvl)
                                 (stairs? old-stairs)
                                 (not (:branch-id old-stairs))
                                 (at-player new-game))]
          (-> new-game
              (assoc-in [:dungeon :levels old-branch old-dlvl :tiles
                         (dec (:y old-stairs)) (:x old-stairs) :branch-id]
                        new-branch)
              (mark-branch-entrance new-stairs old-game
                                    (:feature old-stairs)))
          new-game)))
    (reify
      ToplineMessageHandler
      (message [_ text]
        (condp re-seq text
          #"You can't go down here"
          (swap! game update-at-player assoc :feature nil)
          #"heat and smoke are gone."
          (reset! entered-vlad true)
          #"A mysterious force prevents you from descending"
          (swap! game update-around (-> (curlvl old-game) :blueprint :leader)
                 assoc :walked nil) ; revisit
          nil))
      DlvlChangeHandler
      (dlvl-changed [this old-dlvl new-dlvl]
        (swap! game
               #(let [new-branch (cond
                                   @entered-vlad :vlad
                                   (and (subbranches old-branch)
                                        (= (branch-entry % old-branch)
                                           new-dlvl)) :main
                                   :else (get old-stairs :branch-id
                                              (initial-branch-id % new-dlvl)))]
                  (-> %
                      (update-in [:dungeon :levels old-branch old-dlvl :tags]
                                 conj new-branch)
                      (assoc :branch-id new-branch))))
        (log/debug "choosing branch-id" (:branch-id @game) "for dlvl" new-dlvl)
        (if-let [no (->> (:branch-id @game) name
                         (re-first-group #"unknown-([0-9]+)")
                         parse-int)]
          (swap! game update :last-branch-no #((fnil max 0) % no)))))))

(defaction Ascend []
  (trigger [_] "<")
  (handler [_ bh]
    (stairs-handler bh)))

(defaction Descend []
  (trigger [_] ">")
  (handler [_ bh]
    (stairs-handler bh)))

(defaction Kick [dir]
  (trigger [_] (str (ctrl \d) (direction-trigger dir)))
  (handler [_ {:keys [game] :as bh}]
    (reify ToplineMessageHandler
      (message [_ msg]
        (condp re-seq msg
          no-monster-re
          (swap! game update-from-player dir reset-item)
          #"Your .* is in no shape for kicking."
          (swap! game assoc-in [:player :leg-hurt] true)
          #"You can't move your leg!|There's not enough room to kick down here"
          (swap! game assoc-in [:player :trapped] true)
          #"A black ooze gushes up from the drain!"
          (swap! game update-from-player dir update :tags conj :pudding)
          #"The dish washer returns!"
          (swap! game update-from-player dir update :tags conj :foocubus)
          #"You see a ring shining in its midst"
          (swap! game update-from-player dir update :tags conj :ring)
          #"Thump!"
          (swap! game update-from-player dir assoc :thump true)
          nil)))))

(defaction Close [dir]
  (trigger [this] (str \c (direction-trigger dir)))
  (handler [_ {:keys [game] :as bh}]
    (reify ToplineMessageHandler
      (message [_ text]
        (let [door (in-direction (:player @game) dir)]
          (case text
            "This door is already closed." (swap! game update-at door
                                                  assoc :feature :door-closed)
            "This doorway has no door." (swap! game update-at door
                                               assoc :feature nil)
            "You see no door there." (swap! game update-at door
                                            assoc :feature nil)
            nil))))))

(defn- rogue-item-glyph [game glyph]
  (case glyph
    \" \,
    \$ \*
    \% \:
    \[ \]
    glyph))

(defn- item-glyph [game item]
  (if (:rogue (curlvl-tags game))
    (rogue-item-glyph game (:glyph (item-id game item)))
    (:glyph (item-id game item))))

(def things-re #"^Things that (?:are|you feel) here:|You (?:see|feel)")
(def thing-re #"You (?:see|feel) here ([^.]+).")
(def etype-re #"Something is (written|engraved) here (?:in|on) the (?:.*)\.|Some text has been (burned|melted) into the|There's some (graffiti) on the|You see a message (scrawled) in blood here")
(def etext-re #"You read: \"(.*)\"\.")

(defn unparseable-item?
  "Items with labels that may not fit the terminal size are ignored.
  This is only a problem with thoroughly rusty thoroughly corroded long-named items."
  [item]
  (more-than? 72 (:label item)))

(defaction Look []
  (trigger [this] ":")
  (handler [_ {:keys [game delegator] :as bh}]
    (let [has-item (atom false)
          has-engraving (atom false)
          has-feature (atom false)]
      (swap! game #(if-not (blind? (:player %))
                     (update-at-player % assoc :seen true :new-items false)
                     %))
      (update-on-known-position bh
        (fn after-look [game]
          (as-> game res
            (update-at-player res assoc :examined (:turn game))
            (if (blind? (:player game))
              (if-not (= :permanent (:engraving-type (at-player res)))
                (update-at-player res assoc :engraving nil :engraving-type nil)
                res)
              (if (not @has-engraving)
                (update-at-player res assoc :engraving nil :engraving-type nil)
                res))
            (if (and (not @has-feature)
                     ((not-any-fn? floor? corridor?) (at-player res)))
              (update-at-player res assoc :feature :floor)
              res)
            (if-not @has-item
              (update-at-player res assoc :items [])
              res))))
      ; XXX note: items on tile HAVE to be determined via this command only, topline messages on Move are not reliable due to teletraps
      (reify
        FullFrameHandler
        (full-frame [_ _]
          (send delegator #(if-let [items (seq (:items (at-player @game)))]
                             (found-items % items)
                             %)))
        MultilineMessageHandler
        (message-lines [_ lines]
          (if (re-seq things-re (first lines))
            (let [items (mapv label->item (subvec lines 1))
                  top-item (firstv items)]
              ;(log/debug "Items here:" (log/spy items))
              (reset! has-item true)
              (swap! game #(update-at-player %
                             assoc :items (removev unparseable-item?
                                                   items)
                                   :item-glyph (item-glyph % top-item)
                                   :item-color nil)))))
        ToplineMessageHandler
        (message [_ text]
          (when-not (and (= text "But you can't reach it!")
                         (reset! has-feature true)
                         (reset! has-item true))
            (when-let [item (some->> text
                                     (re-first-group thing-re)
                                     label->item)]
              (log/debug "Single item here:" item)
              (reset! has-item true)
              (swap! game #(update-at-player %
                             assoc :items (if-not (unparseable-item? item)
                                            [item]
                                            [])
                                   :item-glyph (item-glyph % item)
                                   :item-color nil)))
            (when-let [etype (re-any-group etype-re text)]
              (reset! has-engraving true)
              (swap! game update-at-player assoc
                     :engraving-type ({"written" :dust
                                       "scrawled" :dust
                                       "melted" :semi
                                       "graffiti" :semi
                                       "engraved" :semi
                                       "burned" :permanent} etype)))
            (when-let [etext (re-first-group etext-re text)]
              (swap! game update-at-player assoc :engraving etext))
            (swap! game update-at-player
                   #(if-let [new-tile (feature-msg-update
                                        % text (:rogue (curlvl-tags @game)))]
                      (do (reset! has-feature true)
                          new-tile)
                      %))))))))

(def farlook-monster-re #"^(?:.     *)?[^(]*\(([^,)]*)(?:,[^)]*)?\)|a (mimic) or a strange object$")
(def farlook-trap-re #"^\^ * a trap \(([^)]*)\)")

(defaction FarLook [pos]
  (trigger [this]
    (str \; (to-position pos) \.))
  (handler [_ {:keys [game] :as bh}]
    (reify ToplineMessageHandler
      (message [_ text]
        (or (when-let [align (re-first-group #"\(([^ ]*) altar\)$" text)]
              (swap! game update-at pos assoc
                     :alignment (str->kw align)))
            (when-let [trap (re-first-group farlook-trap-re text)]
              (swap! game update-at pos assoc :feature
                     (or (trap-names trap)
                         (throw (IllegalArgumentException. (str "unknown farlook trap: " text " >>> " trap))))))
            (when-let [desc (and (monster-glyph? (firstv text))
                                 (re-any-group farlook-monster-re text))]
              (let [peaceful? (.contains ^String desc "peaceful ")
                    montype (by-description desc)]
                (log/debug "monster description" text "=>" montype)
                (if (= "gremlin" (:name montype))
                  (swap! game assoc :gremlins-peaceful peaceful?))
                (swap! game update-monster pos assoc
                       :peaceful peaceful? :type montype)))
            (log/debug "non-monster farlook result:" text))))))

(defn- handle-door-message [game dir text]
  (let [door (in-direction (:player @game) dir)
        new-feature (condp #(.contains %2 %1) text
                      "The door opens." :door-open
                      "You cannot lock an open door." :door-open
                      "This door is locked." :door-locked
                      "This door is already open." :door-open
                      "This doorway has no door." nil
                      "You see no door there." nil
                      "You succeed in picking the lock." :door-closed
                      "You succeed in unlocking the door." :door-closed
                      "You succeed in locking the door." :door-locked
                      "You can't lock a door with a credit card." :door-closed
                      :nil)]
    (if (not= :nil new-feature)
      (swap! game update-at door assoc :feature new-feature))))

(defaction Open [dir]
  (trigger [_] (str \o (direction-trigger dir)))
  (handler [_ {:keys [game] :as bh}]
    (reify ToplineMessageHandler
      (message [_ text]
        (handle-door-message game dir text)))))

(defn- transfer-item
  "Keep some info about items on inventory update"
  [inventory [old-slot old-item]]
  (if (inventory old-slot)
    (update inventory old-slot
            #(into % (select-keys old-item
                                  (as-> [:items :locked] ks
                                    (if (nil? (:buc %))
                                      (conj ks :buc)
                                      ks)
                                    (if (and (unparseable-item? %)
                                             (:in-use old-item))
                                      (conj ks :in-use :worn)
                                      ks)))))
    inventory)) ; item gone

(defaction Inventory []
  (trigger [_] "i")
  (handler [_ {:keys [game] :as bh}]
    (reify
      ToplineMessageHandler
      (message [_ text]
        (case text
          "Not carrying anything."
          (swap! game assoc-in [:player :inventory] {})
          "Not carrying anything except gold."
          (swap! game update-in [:player :inventory] select-keys [\$])
          nil))
      InventoryHandler
      (inventory-list [_ inventory]
        (swap! game update-in [:player :inventory]
               (partial reduce transfer-item
                        (into {} (for [[c i] inventory] (slot-item c i)))))))))

(defn- examine-tile [{:keys [player] :as game}]
  (if-let [tile (and (not (blind? player)) (not (:engulfed player))
                     (at-player game))]
    (if ((some-fn :new-items unknown? unknown-trap?
                  (every-pred e? (complement perma-e?)
                              (comp (partial not= (:turn game)) :examined))
                  (every-pred altar? (complement :alignment))) tile)
      (with-reason "examining tile" tile ->Look))))

(defn- examine-features [game]
  (if-not (:engulfed (:player game))
    (some->> (curlvl game) tile-seq
             (find-first (every-pred #(or (unknown-trap? %)
                                          (and (not= :astral (branch-key game))
                                               (altar? %) (not (:alignment %))))
                                     (complement blank?)
                                     (complement item?)
                                     (complement monster?)))
             ->FarLook
             (with-reason "examining ambiguous feature"))))

(defn- examine-monsters [{:keys [player] :as game}]
  (if-not (or (:engulfed player) (hallu? player))
    (if-let [m (->> (curlvl-monsters game)
                    (remove (some-fn :remembered
                                     :friendly
                                     (every-pred :type
                                                 (comp some? :peaceful)
                                                 (comp (partial not= :update)
                                                       :peaceful))
                                     (comp #{\I \1 \2 \3 \4 \5} :glyph)))
                    first)]
      (with-reason "examining monster" (->FarLook m)))))

(defn- inventory-handler [bh]
  (reify ActionHandler
    (choose-action [this game]
      (deregister-handler bh this)
      (if (not= :inventory (typekw (:last-action* game)))
        (with-reason "requested inventory update" (->Inventory))))))

(defn update-inventory
  "Re-check inventory on the next action"
  [bh]
  {:pre [(:game bh)]}
  (register-handler bh (dec priority-top) (inventory-handler bh)))

(defn update-tile
  "Re-check current tile for items/engravings/feature on the next action"
  [bh]
  {:pre [(:game bh)]}
  (update-at-player-when-known bh assoc :new-items true))

(defn- discovery-demangle [section appearance]
  (case section
    "Gems" (if (= "gray" appearance)
             (str appearance " stone")
             (str appearance " gem"))
    "Amulets" (str appearance " amulet")
    "Wands" (str appearance " wand")
    "Rings" (str appearance " ring")
    "Potions" (str appearance " potion")
    "Spellbooks" (str appearance " spellbook")
    "Scrolls" (str "scroll labeled " appearance)
    appearance))

(def ^:private discoveries-re #"(Artifacts|Unique Items|Spellbooks|Amulets|Weapons|Wands|Gems|Armor|Food|Tools|Scrolls|Rings|Potions)|(?:\* )?([^\(]*)(?: called [^(]+)? \(([^\)]*)\)$|^(?:[^(]+)$")

(defaction Discoveries []
  (trigger [_] "\\")
  (handler [_ {:keys [game] :as bh}]
    (let [known-names (atom #{})]
      (reify
        AboutToChooseActionHandler
        (about-to-choose [_ _]
          (swap! game forget-names (difference (:used-names game)
                                               @known-names)))
        MultilineMessageHandler
        (message-lines [this lines-all]
          (swap! game add-discoveries
                 (loop [section nil
                        lines lines-all
                        discoveries []]
                   (if-let [line (first lines)]
                     (let [[group id appearance]
                           (->> (string/replace line
                                  #"^(.*) called ([^(]+) \([^)]*\)" "$1 ($2)")
                                (re-first-groups discoveries-re))]
                       (if-let [called (re-first-group #"called ([^(]+)(?: |$)"
                                                       line)]
                         (swap! known-names conj called))
                       (if group
                         (recur group (rest lines) discoveries)
                         (if (= section "Unique Items")
                           (recur section (rest lines) discoveries)
                           (if appearance
                             (recur section (rest lines)
                                    (conj discoveries
                                          [(discovery-demangle
                                             section appearance) id]))
                             (recur section (rest lines) discoveries)))))
                     discoveries))))))))

(defn- discoveries-handler [bh]
  (reify ActionHandler
    (choose-action [this game]
      (deregister-handler bh this)
      (if (not= :discoveries (typekw (:last-action* game)))
        (with-reason "discoveries update"
          (->Discoveries))))))

(defn update-discoveries
  "Re-check discoveries on the next action"
  [bh]
  {:pre [(:game bh)]}
  (register-handler bh (dec priority-top) (discoveries-handler bh)))

(defaction Name [slot name]
  (trigger [_] "#name\n")
  (handler [_ {:keys [game] :as bh}]
    (update-inventory bh)
    (reify
      NameMenuHandler
      (name-menu [_ _] \b)
      NameWhatHandler
      (name-what [_ _] slot)
      WhatNameHandler
      (what-name [_ _] name))))

(defaction Call [slot name]
  (trigger [_] "#call\n")
  (handler [_ {:keys [game] :as bh}]
    (update-inventory bh)
    (update-tile bh)
    (update-discoveries bh)
    (if (names name)
      (swap! game update :used-names conj name))
    (reify
      NameMenuHandler
      (name-menu [_ _] \c)
      NameWhatHandler
      (name-what [_ _] slot)
      WhatNameHandler
      (what-name [_ _] name))))

(defn name-item
  "Name an item on the next action"
  [bh slot name]
  {:pre [(:game bh) (char? slot) (string? name)]}
  (register-handler bh priority-top
                    (reify ActionHandler
                      (choose-action [this game]
                        (deregister-handler bh this)
                        (if-let [item (inventory-slot game slot)]
                          (if (not= name (:specific item))
                            (->Name slot name))
                          (log/warn "naming nonexistent slot" slot))))))

(defn- identify-slot [game slot id]
  {:pre [(:discoveries game) (char? slot) (string? id)]}
  (add-discovery game (slot-appearance game slot) id))

(declare possible-autoid)

(defaction Apply [slot]
  (trigger [_] "a")
  (handler [_ {:keys [game] :as bh}]
    (possible-autoid bh slot)
    (reify
      AttachCandlesHandler
      (attach-candelabrum-candles [_ _]
        (update-inventory bh))
      ToplineMessageHandler
      (message [_ msg]
        (condp re-seq msg
          #"has no oil|has run out of power"
          (do (swap! game identify-slot slot "oil lamp")
              (name-item bh slot "empty"))
          #" lamp is now (on|off)|burns? brightly!|You light your |^You snuff "
          (update-inventory bh)
          #" seems to be locked"
          (swap! game update-slot slot assoc :locked true)
          #" is empty\."
          (swap! game update-slot slot assoc :items [])
          nil))
      ApplyItemHandler
      (apply-what [_ _] slot)
      TakeSomethingOutHandler
      (take-something-out [_ _]
        (swap! game update-slot slot assoc :items [])
        true)
      TakeOutWhatHandler
      (take-out-what [_ options]
        (swap! game update-slot slot update :items into (map label->item
                                                             (vals options)))
        #{}) ; update items but don't take anything out
      PutSomethingInHandler
      (put-something-in [_ _] false))))

(defn with-handler
  ([handler action]
   (with-handler priority-default handler action))
  ([priority handler action]
   (update action :handlers conj [priority handler])))

(defn ->ApplyAt
  "Apply something in the given direction (eg. pickaxe, key...).
  Assumes the thing is already wielded (if it has to be like a pickaxe),
  otherwise resulting direction prompt may be misparsed after auto-wield if
  something happens during the wielding"
  [slot dir]
  (->> (->Apply slot)
       (with-handler priority-top
         (fn [{:keys [game] :as bh}]
           (reify
             DirectionHandler
             (what-direction [_ _] dir)
             ToplineMessageHandler
             (message [_ msg]
               (condp re-seq msg
                 #"The ceiling collapses around you!"
                 (swap! game update-around-player assoc :feature :rock)
                 #"you can't dig while entangled"
                 (swap! game assoc-in [:player :trapped] true)
                 #"This wall (seems|is) too hard to dig into\."
                 (if-not (:orcus (:tags (curlvl @game)))
                   (swap! game update-from-player dir assoc :undiggable true))
                 #"You make an opening"
                 (swap! game update-from-player dir
                        assoc :dug true :feature :floor)
                 #"You succeed in cutting away some rock"
                 (swap! game update-from-player dir
                        assoc :dug true :feature :corridor)
                 #"^You swing your pick"
                 (swap! game update-from-player dir assoc :feature nil)
                 #"here is too hard to"
                 (swap! game add-curlvl-tag :undiggable-floor)
                 #"You dig a pit"
                 (swap! game update-at-player assoc :feature :pit)
                 #"You dig a hole through"
                 (swap! game update-at-player assoc :feature :hole)
                 nil)))))))

(defaction ForceLock []
  (trigger [_] "#force\n")
  (handler [_ {:keys [game] :as bh}]
    (doseq [[idx item] (indexed (:items (at-player @game)))
            :when (:locked item)]
      (swap! game update-item-at-player idx assoc :locked false))
    (reify ForceLockHandler
      (force-lock [_ _] true))))

(defn ->Unlock [slot dir]
  (let [dir (enum->kw dir)]
    (->> (->ApplyAt slot dir)
         (with-handler priority-top
           (fn [{:keys [game] :as bh}]
             (if (= :. dir)
               (doseq [[idx item] (indexed (:items (at-player @game)))
                       :when (:locked item)]
                 (swap! game update-item-at-player idx assoc :locked false)))
             (reify
               ToplineMessageHandler
               (message [_ msg]
                 (if (not= :. dir)
                   (handle-door-message game dir msg)))
               LockHandler
               (lock-it [_ _]
                 (if (dirmap dir)
                   (swap! game update-from-player dir
                          assoc :feature :door-closed))
                 false)
               (unlock-it [_ _]
                 (if (dirmap dir)
                   (swap! game update-from-player dir
                          assoc :feature :door-locked))
                 true)))))))

(defn- possible-autoid
  "Check if the item at slot auto-identified on use.  If no-mark? is true the result is not used for item-id purposes"
  ([bh slot] (possible-autoid bh slot false))
  ([{:keys [game] :as bh} slot no-mark?]
   (if-let [item (inventory-slot @game slot)]
     (when-not (know-id? @game item)
       (update-discoveries bh)
       (if (not no-mark?)
         (register-handler bh
           (reify AboutToChooseActionHandler
             (about-to-choose [this game]
               (when (= :discoveries (typekw (:last-action* game)))
                 (deregister-handler bh this)
                 (if (not (know-id? game item))
                   (swap! (:game bh) add-prop-discovery (appearance-of item)
                          :autoid false)))))))))))

(defn mark-tried [game item]
  (if-not (impaired? (:player game))
    (update game :tried conj (appearance-of item))
    game))

(defn tried?
  "Wands are tried when engraved with, other items when worn/quaffed/read/...  Zapping wands doesn't count as use - you can check (:target (item-id game wand))"
  [game item]
  ((:tried game) (appearance-of item)))

(defn- mark-use [{:keys [game] :as bh} slot]
  (swap! game #(mark-tried % (inventory-slot % slot))))

(defaction Wield [slot]
  (trigger [_] "w")
  (handler [_ {:keys [game] :as bh}]
    (update-inventory bh)
    (possible-autoid bh slot)
    (reify WieldItemHandler
      (wield-what [_ _] slot))))

(defn ->UnWield
  ([] (->Wield \-))
  ([_] (->UnWield)))

(defaction Wear [slot]
  (trigger [_] "W")
  (handler [_ {:keys [game] :as bh}]
    (update-inventory bh)
    (possible-autoid bh slot)
    (reify
      ToplineMessageHandler
      (message [_ msg]
        (condp re-seq msg
          #"You don't have anything else to wear|already wearing that"
          (swap! game update-in [:player :inventory slot]
                 assoc :worn true :in-use true)
          nil))
      WearItemHandler
      (wear-what [_ _]
        (mark-use bh slot)
        slot))))

(defaction PutOn [slot]
  (trigger [_] "P")
  (handler [_ {:keys [game] :as bh}]
    (update-inventory bh)
    (possible-autoid bh slot)
    (reify PutOnItemHandler
      (put-on-what [_ _]
        (mark-use bh slot)
        slot))))

(defaction Remove [slot]
  (trigger [_] "R")
  (handler [_ bh]
    (update-inventory bh)
    (reify RemoveItemHandler
      (remove-what [_ _]
        (mark-use bh slot)
        slot))))

(defaction TakeOff [slot]
  (trigger [_] "T")
  (handler [_ {:keys [game] :as bh}]
    (update-inventory bh)
    (reify
      ToplineMessageHandler
      (message [_ msg]
        (condp re-seq msg
          #"Not wearing any armor|not wearing that"
          (swap! game update-in [:player :inventory slot]
                 assoc :worn false :in-use false)
          nil))
      TakeOffItemHandler
      (take-off-what [_ _]
        (mark-use bh slot)
        slot))))

(defn- ring-msg [msg]
  (condp re-seq msg
    #"got lost in the sink, but there it is!" "ring of searching"
    #"The ring is regurgitated!" "ring of slow digestion"
    #"The sink quivers upward for a moment" "ring of levitation"
    #"You smell rotten fruit" "ring of poison resistance"
    #"Static electricity surrounds the sink" "ring of shock resistance"
    #"You hear loud noises coming from the drain" "ring of conflict"
    #"The water flow seems fixed" "ring of sustain ability"
    #"The water flow seems (stronger|weaker) now" "ring of gain strength"
    #"The water flow seems (greater|lesser) now" "ring of gain constitution"
    #"The water flow (hits|misses) the drain" "ring of increase accuracy"
    #"The water's force seems (greater|smaller) now" "ring of increase damage"
    #"Several flies buzz angrily around the sink" "ring of aggravate monster"
    #"Suddenly, .*from the sink!" "ring of hunger"
    #"The faucets flash brightly for a moment" "ring of adornment"
    #"The sink looks as good as new" "ring of regeneration"
    #"You don't see anything happen to the sink" "ring of invisibility"
    #"You see the ring slide right down the drain!" "ring of free action"
    #"You see some air in the sink" "ring of see invisible"
    #"The sink seems to blend into the floor for a moment" "ring of stealth"
    #"The hot water faucet flashes brightly" "ring of fire resistance"
    #"The cold water faucet flashes brightly" "ring of cold resistance"
    #"The sink looks nothing like" "ring of protection from shape changers"
    #"The sink glows (silver|black) for a moment" "ring of protection"
    #"The sink glows white for a moment" "ring of warning"
    #"The sink momentarily vanishes" "ring of teleportation"
    #"The sink looks like it is being beamed" "ring of teleport control"
    #"The sink momentarily looks like a fountain" "ring of polymorph"
    #"The sink momentarily looks like a regularly" "ring of polymorph control"
    nil))

(defaction DropSingle [slot qty]
  (trigger [_] "d")
  (handler [_ {:keys [game] :as bh}]
    (update-inventory bh)
    (update-tile bh)
    (swap! game update :player dissoc :thick)
    (reify
      ToplineMessageHandler
      (message [_ msg]
        (if-let [id (ring-msg msg)]
          (swap! game identify-slot slot id)
          (if (= msg "You cannot drop something you are wearing.")
            (swap! game update-in [:player :inventory slot]
                   assoc :worn true :in-use true))))
      SellItHandler
      (sell-it [_ bid _]
        (let [item (inventory-slot @game slot)]
          (when (price-id? @game item)
            (swap! game add-observed-cost (appearance-of item) bid :sell)
            nil)))
      DropSingleHandler
      (drop-single [_ _] (str (if (pos? qty)
                                qty)
                              slot)))))

(defaction Quiver [slot]
  (trigger [_] "Q")
  (handler [_ bh]
    (update-inventory bh)
    (reify QuiverHandler
      (ready-what [_ _] slot))))

(defn ->Drop
  ([slot qty]
   (->DropSingle slot qty))
  ([slot-or-map]
   (if (char? slot-or-map)
     (->DropSingle slot-or-map 1)
     (throw (UnsupportedOperationException. "multidrop not yet implemented")))))

(defaction PickUp [label-or-list]
  (trigger [_] ",")
  (handler [_ bh]
    (update-inventory bh)
    (update-tile bh)
    (let [labels (if (string? label-or-list)
                   (multiset label-or-list)
                   (into (multiset) label-or-list))
          remaining (atom labels)]
      (reify PickupHandler
        (pick-up-what [_ options]
          ;(log/debug options)
          ;(log/debug "want" remaining)
          (loop [opts options
                 res #{}]
            (if-let [[slot lbl] (first opts)]
              (if (contains? @remaining lbl)
                (do (swap! remaining disj lbl)
                    (recur (rest opts) (conj res slot)))
                (recur (rest opts) res))
              res)))))))

(defaction Autotravel [pos]
  (trigger [_] "_")
  (handler [this {:keys [game] :as bh}]
    (let [pos (position pos)
          path (set (rest (pop (:path this))))]
      (swap! game assoc :last-autonav pos :autonav-stuck false)
      (reify
        KnowPositionHandler
        (know-position [this {:keys [cursor]}]
          (when (and (seq path) (not= pos cursor)
                     (or (some boulder? (neighbors (curlvl @game) cursor))
                         (not-any? path (neighbors cursor))))
     ; when autonav diverges from the intended path, this should prevent a cycle
            (log/debug "autonav stuck")
            (swap! game assoc :autonav-stuck true)))
        ToplineMessageHandler
        (message [_ msg]
          (move-message-handler bh msg))
        AutotravelHandler
        (travel-where [_] pos)))))

(defaction Enhance []
  (trigger [_] "#enhance\n")
  (handler [_ {:keys [game] :as bh}]
    (reify CurrentSkillsHandler
      (current-skills [_ _] ; nothing to enhance
        (swap! game assoc-in [:player :can-enhance] nil)
        #{}))))

(defn enhance-all
  "Enhance action with a handler that enhances any skills available"
  []
  (with-handler
    (reify EnhanceWhatHandler ; enhance anything
      (enhance-what [_ _] \a))
    (->Enhance)))

(defn- -withHandler
  ([action handler]
   (-withHandler action priority-default handler))
  ([action priority handler]
   (with-handler action priority handler)))

(defaction Read [slot]
  (trigger [_] "r")
  (handler [_ {:keys [game] :as bh}]
    (update-inventory bh)
    (possible-autoid bh slot)
    (reify
      ReadWhatHandler
      (read-what [_ _]
        (mark-use bh slot)
        slot)
      ToplineMessageHandler
      (message [_ msg]
        (condp re-seq msg
          #"Your [a-z]* (glow|begin to glow|tingle|begin to tingle)|A faint (buzz|glow) surrounds your [a-z]*\.|You feel confused\."
          (swap! game identify-slot slot "scroll of confuse monster")
          #"You feel like someone is helping you\.|You feel in touch with the Universal Oneness\.|You feel like you need some help\.|You feel the power of the Force against you"
          (swap! game identify-slot slot "scroll of remove curse")
          #"You hear maniacal laughter|You hear sad wailing"
          (swap! game identify-slot slot "scroll of scare monster")
          nil)))))

(defaction Sit []
  (trigger [_] "#sit\n")
  (handler [_ {:keys [game] :as bh}]
    (reify
      ToplineMessageHandler
      (message [_ msg]
        (condp re-seq msg
          #"not very comfortable\.\.\."
          (swap! game update-at-player assoc :feature nil)
          #"Having fun sitting on the (floor|air)\?"
          (swap! game update-at-player assoc :feature :floor)
          (move-message-handler bh msg))))))

(defaction Eat [slot-or-label]
  (trigger [_] "e")
  (handler [_ bh]
    (reify
      ToplineMessageHandler
      (message [_ msg]
        (when (= msg "You don't have anything to eat.")
          (update-inventory bh)
          (update-tile bh)))
      EatItHandler
      (eat-it [_ what]
        (if (and (string? slot-or-label)
                 (= what slot-or-label))
          (update-tile bh)
          false))
      EatWhatHandler
      (eat-what [_ _]
        (if (string? slot-or-label)
          (update-tile bh))
        (when (char? slot-or-label)
          (update-inventory bh)
          slot-or-label)))))

(def fountain-re #"The flow reduces to a trickle|, stop using that fountain!")

(defaction Quaff [slot]
  (trigger [_] "q")
  (handler [_ {:keys [game] :as bh}]
    (possible-autoid bh slot)
    (reify
      ToplineMessageHandler
      (message [_ msg]
        (if (re-seq fountain-re msg)
          (swap! game update-at-player update :tags conj :trickle)))
      DrinkHereHandler
      (drink-here [_ _]
        (if (= slot \.)
          (update-tile bh)
          false))
      DrinkWhatHandler
      (drink-what [_ _]
        (when (not= slot \.)
          (mark-use bh slot)
          (update-inventory bh)
          slot)))))

(defn use-action [item]
  (case (item-type item)
    :scroll ->Read
    :spellbook ->Read
    :potion ->Quaff
    :ring ->PutOn
    :amulet ->PutOn
    :armor ->Wear
    :weapon ->Wield
    :tool (if (= :accessory (item-subtype item))
            ->PutOn
            ->Apply)))

(defn remove-action [item]
  (if (:wielded item)
    ->UnWield
    (case (item-type item)
      :ring ->Remove
      :amulet ->Remove
      :tool ->Remove
      :weapon ->UnWield
      :armor ->TakeOff
      ->TakeOff)))

(defn remove-blockers [game slot]
  (with-reason "removing blockers of" slot
    (let [item (inventory-slot game slot)]
      (if-let [[[blocker-slot blocker] & _ :as blockers] (blockers game item)]
        (if (and blocker
                 (not-any? cursed? (vals blockers))
                 (or (not (:wielded blocker)) (shield? item)))
          ((remove-action blocker) blocker-slot))))))

(declare untrap-move)

(defn make-use [game slot]
  (with-reason "making use of" slot
    (let [item (inventory-slot game slot)]
      (if-not (or (:in-use item) (cursed-blockers game slot)
                  (not (has-hands? (:player game))))
        (or (if (:trapped (:player game))
              (untrap-move game))
            (remove-blockers game slot)
            ((use-action item) slot))))))

(defn remove-use [game slot]
  (with-reason "removing use of" slot
    (let [item (inventory-slot game slot)]
      (if (:in-use item)
        (or (if (:trapped (:player game))
              (untrap-move game))
            (remove-blockers game slot)
            (if (can-remove? game slot)
              ((remove-action item) slot)))))))

(defn without-levitation [game action]
  ; XXX doesn't work for intrinsic levitation
  (if-let [a (if (fn? action)
               (action)
               action)]
    (if-let [[slot _] (and (not= :air (branch-key game))
                           (have-levi-on game))]
      (with-reason "action" (typekw a) "forbids levitation"
        (remove-use game slot))
      a)))

(defaction Repeated [action n]
  (trigger [_] (str n (trigger action)))
  (handler [_ bh] (handler action bh)))

(defn search
  "Search once or n times"
  ([] (search 1))
  ([n] (->Repeated (->Search) n)))

(defn arbitrary-move
  ([game level] (arbitrary-move game level false))
  ([{:keys [player] :as game} level diagonal?]
   (some->> (if diagonal?
              (diagonal-neighbors level player)
              (neighbors level player))
            (remove (some-fn trap? (partial monster-at level)))
            (filterv #(or (passable-walking? game level (at level player) %)
                          (unexplored? %)))
            random-nth
            (towards player)
            ->Move
            (with-reason "arbitrary direction"))))

(defn untrap-move
  ([game] (untrap-move game (curlvl game)))
  ([{:keys [player] :as game} level]
   (with-reason "untrap move"
     (or (if-let [wall (find-first (some-fn wall? rock?)
                                   (diagonal-neighbors level player))]
           (->Move (towards player wall)))
         (arbitrary-move game level :diagonal)
         (arbitrary-move game level)))))

(defn kick [{:keys [player] :as game} target-or-dir]
  (let [dir (if (keyword? target-or-dir)
              target-or-dir
              (towards player target-or-dir))]
    (if-not (or (:thump (in-direction (curlvl game) player dir))
                (stressed? player))
      (with-reason "kick"
        (cond
          (:leg-hurt player) (with-reason "wait out leg hurt" (search 10))
          (:trapped player) (untrap-move game)
          :else (without-levitation game
                  (->Kick dir)))))))

(defn dig [[slot item] dir]
  (if (:in-use item)
    (->ApplyAt slot dir)
    (->Wield slot)))

(defn descend [game]
  (without-levitation game ->Descend))

(defaction Offer [slot-or-label]
  (trigger [_] "#offer\n")
  (handler [_ {:keys [game] :as bh}]
    (reify
      ToplineMessageHandler
      (message [_ msg]
        (condp re-seq msg
          #"You are not standing on an altar"
          (log/warn "#offer on non-altar")
          #"You have a feeling of reconciliation\.|You glimpse a four-leaf clover at your feet|You think something brushed your foot|You see crabgrass at your feet"
          (swap! game assoc-in [:player :last-prayer] -1000)
          nil))
      SacrificeItHandler
      (sacrifice-it [_ what]
        (if (and (string? slot-or-label)
                 (= what slot-or-label))
          (update-tile bh)
          false))
      SacrificeWhatHandler
      (sacrifice-what [_ _]
        (if (string? slot-or-label)
          (update-tile bh))
        (when (char? slot-or-label)
          (update-inventory bh)
          slot-or-label)))))

(defn- nth-container-index [game n]
  (loop [items (:items (at-player game))
         containers 0
         idx 0]
    (if-let [item (first items)]
      (if (container? item)
        (if (= containers n)
          idx
          (recur (rest items)
                 (inc containers)
                 (inc idx)))
        (recur (rest items)
               containers
               (inc idx))))))

(defaction Loot []
  (trigger [_] "#loot\n")
  (handler [_ {:keys [game] :as bh}]
    (let [n (atom -1)]
      (reify
        LootWhatHandler
        (loot-what [_ _] #{\,})
        LootItHandler
        (loot-it [_ _] true)
        TakeSomethingOutHandler
        (take-something-out [_ _]
          (swap! game #(update-item-at-player % (nth-container-index % @n)
                                              assoc :items []))
          true)
        TakeOutWhatHandler
        (take-out-what [_ options]
          (swap! game #(update-item-at-player % (nth-container-index % @n)
                                              update :items into
                                              (map label->item (vals options))))
          #{}) ; update items but don't take anything out
        ToplineMessageHandler
        (message [_ msg]
          (if (.startsWith msg "You carefully open")
            (swap! n inc)) ; may occur on same line with "... is empty"
          (condp re-seq msg
            #"You don't find anything here to loot"
            (update-tile bh)
            #"It develops a huge set of teeth and bites you!"
            (swap! game #(update-item-at-player % (nth-container-index % @n)
                                                assoc :name "bag of tricks"))
            #" seems to be locked\."
            (let [idx (swap! n inc)]
              (swap! game #(update-item-at-player % (nth-container-index % idx)
                                                  assoc :locked true)))
            #" is empty\."
            (swap! game #(update-item-at-player % (nth-container-index % @n)
                                                assoc :items []))
            nil))
        PutSomethingInHandler
        (put-something-in [_ _] false)))))

(defn- update-container [bh slot]
  (register-handler bh priority-top
                    (reify ActionHandler
                      (choose-action [this game]
                        (when (has-hands? (:player game))
                          (deregister-handler bh this)
                          (with-reason "updating content of container at" slot
                            (if (= \. slot)
                              (->Loot)
                              (if (inventory-slot game slot)
                                (->Apply slot)
                                (log/warn "container at" slot
                                          "disappeared - exploded BoH?")))))))))

(defn put-in
  ([bag-slot slot amt] (put-in bag-slot {slot amt}))
  ([bag-slot slot-or-amt-map]
   (let [amt-map (if (map? slot-or-amt-map)
                   slot-or-amt-map
                   {slot-or-amt-map nil})
         handler (fn [bh]
                   (update-inventory bh)
                   (update-container bh bag-slot)
                   (reify
                     TakeSomethingOutHandler
                     (take-something-out [_ _] false)
                     PutSomethingInHandler
                     (put-something-in [_ _] true)
                     PutInWhatHandler
                     (put-in-what [_ _]
                       (set (map #(str (val %) (key %)) amt-map)))))]
     (with-reason "putting" slot-or-amt-map "into bag at" bag-slot
       (with-handler (dec priority-top) handler
         (if (= \. bag-slot)
           (->Loot)
           (->Apply bag-slot)))))))

(defn take-out
  ([bag-slot label amt] (take-out bag-slot {label amt}))
  ([bag-slot label-or-amt-map]
   (let [amt-map (if (map? label-or-amt-map)
                   label-or-amt-map
                   {label-or-amt-map nil})
         handler (fn [bh]
                   (update-inventory bh)
                   (update-container bh bag-slot)
                   (reify ; FIXME may select same item on multiple pages or containers, should be handled like PickUp
                     TakeSomethingOutHandler
                     (take-something-out [_ _] true)
                     TakeOutWhatHandler
                     (take-out-what [_ options]
                       (set (keep (fn select-item [[slot label]]
                                    (if-let [[_ amt] (find amt-map label)]
                                      (str amt slot)))
                                  options)))
                     PutSomethingInHandler
                     (put-something-in [_ _] false)))]
     (with-reason "taking" label-or-amt-map "out of container at" bag-slot
       (with-handler (dec priority-top) handler
         (if (= \. bag-slot)
           (->Loot)
           (->Apply bag-slot)))))))

(defn unbag
  "Return action to take out 1 or qty of the item out of a bag"
  ([game maybe-bag-slot item] (unbag game maybe-bag-slot item 1))
  ([game maybe-bag-slot item qty]
   (if (not= item (inventory-slot game maybe-bag-slot))
     (with-reason "preparing item -" (:name item)
       (if (more-than? 51 (inventory game))
         (log/warn "tried unbagging with full inventory - doing nothing")
         (take-out maybe-bag-slot (:label item) qty))))))

(defaction Dip [item-slot potion-slot]
  (trigger [_] "#dip\n")
  (handler [_ {:keys [game] :as bh}]
    (update-inventory bh)
    (reify
      ToplineMessageHandler
      (message [_ msg]
        (if (and (= msg "Interesting...")
                 (holy-water? (inventory-slot @game potion-slot)))
          (swap! game assoc-in [:player :inventory item-slot :buc] :blessed)))
      DipHandler
      (dip-what [_ _] item-slot)
      (dip-into-what [_ _]
        (if (not= \. potion-slot)
          potion-slot))
      DipHereHandler
      (dip-here [_ _]
        (if (= potion-slot \.)
          (update-tile bh)
          false)))))

(defn examine-handler [bh]
  (reify ActionHandler
    (choose-action [_ game]
      (or (examine-tile game)
          (examine-monsters game)
          (examine-features game)))))

(defaction Throw [slot dir]
  (trigger [_] "t")
  (handler [_ {:keys [game] :as bh}]
    (update-inventory bh)
    (let [level (curlvl @game)
          player (:player @game)
          to-update (loop [prev-tile (at level player)
                           tile (in-direction level player dir)]
                      (if-not ((some-fn pool? lava?) tile)
                        (if (or (not tile)
                                (monster-at level tile)
                                (not (walkable? tile)))
                          prev-tile
                          (recur tile
                                 (in-direction level tile dir)))))]
      (if (and to-update (not (visible? game level to-update)))
        (swap! game update-at to-update assoc :new-items true)))
    (reify
      ThrowWhatHandler
      (throw-what [_ _] slot)
      DirectionHandler
      (what-direction [_ _] dir))))

(defn- engrave-effect [msg]
  (condp re-seq msg
    #"The engraving .*vanishes!"
    :vanish
    #"A few ice cubes drop"
    :ice
    #"The.* is riddled by bullet holes"
    :bullet
    #"The bugs on the.* stop moving!"
    :stop
    #"The bugs on the.* slow down"
    :slow
    #"The bugs on the.* speed up"
    :speed
    #"The engraving now reads"
    :change
    #"fights your attempt to write"
    :fights
    nil))

(def ^:private empty-wand-re
  #"You write in the dust with .*wand of (?:lightning|fire|digging)")

(defaction Engrave [slot what append?]
  (trigger [_] "E")
  (handler [_ {:keys [game] :as bh}]
    (update-tile bh)
    (when (not= \- slot)
      (possible-autoid bh slot :no-mark)
      (update-inventory bh)
      (mark-use bh slot))
    (reify
      ToplineMessageHandler
      (message [_ msg]
        (if (re-seq empty-wand-re msg)
          (name-item bh slot "empty"))
        (if-let [effect (engrave-effect msg)]
          (swap! game add-prop-discovery (slot-appearance @game slot)
                 :engrave effect)))
      EngraveAppendHandler
      (append-engraving [_ _] (boolean append?))
      EngraveWithWhatHandler
      (write-with-what [_ _] slot)
      EngraveWhatHandler
      (write-what [_ _] what))))

(defn call-id-handler
  "Automatically disambiguate items like lamp, stone, harp etc. by calling them"
  [bh]
  (reify ActionHandler
    (choose-action [_ game]
      (if-let [[slot item] (and (not (blind? (:player game)))
                                (have game ambiguous-appearance?))]
        (if-let [n (name-for game item)]
          (with-reason "call item to disambiguate"
            (->Call slot n)))))))

(defn wish-id-handler
  "ID wished-for item"
  [{:keys [game] :as bh}]
  (let [wish (atom nil)
        slot (atom nil)]
    (reify
      PromptResponseHandler
      (response-chosen [_ method res]
        (when (and (= make-wish method) (not= "nothing" res))
          (update-inventory bh)
          (reset! wish res)))
      AboutToChooseActionHandler
      (about-to-choose [_ _]
        (when-let [item (and @wish
                             (= :inventory (typekw (:last-action* @game)))
                             (label->item @wish))]
          (swap! game identify-slot @slot (:name item))
          (swap! game update-slot @slot assoc :buc (:buc item))
          (if ((some-fn potion? scroll?) item)
            (name-item bh @slot "wish"))
          (reset! slot nil)
          (reset! wish nil)))
      ToplineMessageHandler
      (message [_ msg]
        (if (and @wish (nil? @slot))
          (when-let [s (and (some? @wish) (nil? @slot)
                            (first (re-first-group #"^([a-zA-Z]) - " msg)))]
            (reset! slot s)))))))

(defn mark-recharge-handler [{:keys [game] :as bh}]
  (reify PromptResponseHandler
    (response-chosen [_ method res]
      (when (= charge-what method)
        (name-item bh (if (string? res) (first res) res) "recharged")))))

(defaction Wipe []
  (trigger [_] "#wipe\n")
  (handler [_ {:keys [game] :as bh}]
    (reify ToplineMessageHandler
      (message [_ msg]
        (if (re-seq #"Your .* is already clean|You've got the glop off"
                    msg)
          (swap! game update-in [:player :state] disj :ext-blind))))))

(defaction ZapWand [slot]
  (trigger [_] "z")
  (handler [_ {:keys [game] :as bh}]
    (let [target (atom false)
          charged (atom true)]
      (reify
        ZapWhatHandler
        (zap-what [_ _] slot)
        AboutToChooseActionHandler
        (about-to-choose [_ _]
          (when @charged
            (possible-autoid bh slot)
            (swap! game add-prop-discovery (slot-appearance @game slot)
                   :target @target)))
        DirectionHandler
        (what-direction [_ _]
          (reset! target true)
          nil)
        ToplineMessageHandler
        (message [_ msg]
          (when (re-seq #"Nothing happens" msg)
            (reset! charged false)
            (let [item (inventory-slot @game slot)]
              (if (or (not= "recharged" (:specific item))
                      (not= "wand of wishing" (item-name @game item)))
                (name-item bh slot "empty")))))))))

(defn ->ZapWandAt [slot dir]
  (with-handler (inc priority-bottom)
    (reify DirectionHandler
      (what-direction [_ _] dir))
    (->ZapWand slot)))

(defaction Rub [slot]
  (trigger [_] "#rub\n")
  (handler [_ {:keys [game] :as bh}]
    (possible-autoid bh slot)
    (reify
      RubWhatHandler
      (rub-what [_ _] slot)
      ToplineMessageHandler
      (message [_ msg]
        (when (re-seq #"puff of smoke" msg)
          (swap! game identify-slot slot "magic lamp"))))))

(defaction Chat [dir]
  (trigger [_] (str "#chat\n" (direction-trigger dir)))
  (handler [_ {:keys [game] :as bh}]
    (swap! game recheck-peaceful-status (some-fn priest? shopkeeper?))
    (reify ToplineMessageHandler
      (message [_ msg]
        (if (.contains msg "Thy devotion has been rewarded")
          (swap! game update-in [:player :protection] inc))))))

(defn ->Contribute [dir amt]
  (with-handler
    (reify OfferHandler
      (offer-how-much [_ _] amt))
    (->Chat dir)))

(defaction Pay [shk]
  (trigger [_] "p")
  (handler [_ {:keys [game] :as bh}]
    (reify PayWhomHandler
      (pay-whom [_] shk))))

(defn wield [game slot]
  (if-let [item (and (has-hands? (:player game))
                     (inventory-slot game slot))]
    (if-not (or (:in-use item) (cursed? (wielded-item game)))
      (if-let [[shield _] (and (two-handed? item) (have game shield? #{:worn}))]
        (remove-use game shield)
        (->Wield slot)))))
