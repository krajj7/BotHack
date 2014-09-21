(ns anbf.actions
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [multiset.core :refer [multiset]]
            [anbf.handlers :refer :all]
            [anbf.action :refer :all]
            [anbf.player :refer :all]
            [anbf.montype :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.level :refer :all]
            [anbf.tile :refer :all]
            [anbf.item :refer :all]
            [anbf.itemid :refer :all]
            [anbf.position :refer :all]
            [anbf.delegator :refer :all]
            [anbf.util :refer :all]))

(defmacro ^:private defaction [action args & impl]
  `(do (defrecord ~action ~args
         anbf.util.Type
         (~'typekw [~'_] ~(keyword (string/lower-case action)))
         anbf.bot.IAction
         ~@impl)
       (defn ~(symbol (str \- action)) ~args
         (~(symbol (str action \.)) ~@args))))

(def ^:private feature-re #"^(?:You see|There is|You escape)(?: an?| your)?(?: \w+)* (falling rock trap|rolling boulder trap|rust trap|magic trap|anti-magic field|polymorph trap|fire trap|arrow trap|dart trap|land mine|teleportation trap|sleeping gas trap|magic portal|level teleporter|bear trap|spiked pit|pit|staircase (?:up|down)|spider web|web|ice|opulent throne|hole|trap door|fountain|sink|grave|doorway|squeaky board|open door|broken door)(?: here| below you)?\.")

(defn- feature-here [msg rogue?]
  (condp re-seq msg
    #"You tear through \w+ web!|You (?:burn|dissolve) \w+ spider web!|You hear a (?:loud|soft) click(?:!|\.)" :floor
    #"There is an altar" :altar
    (if-let [feature (re-first-group feature-re msg)]
      (case feature
        "opulent throne" :throne
        "ice" :ice
        "doorway" (if rogue? :door-open :floor)
        "open door" :door-open
        "broken door" :floor
        "staircase up" :stairs-up
        "staircase down" :stairs-down
        "fountain" :fountain
        "sink" :sink
        "grave" :grave
        (trap-names feature)))))

(defn update-before-action
  "Before the next action is chosen call (apply swap! game f args).  This happens when game state is updated and player position is known."
  [anbf f & args]
  (register-handler anbf priority-top
    (reify AboutToChooseActionHandler
      (about-to-choose [this _]
        (apply swap! (:game anbf) f args)
        (deregister-handler anbf this)))))

(defn update-on-known-position
  "When player position on map is known call (apply swap! game f args).  Game state may not be fully updated yet for the turn."
  [anbf f & args]
  (register-handler anbf priority-top
    (reify
      AboutToChooseActionHandler ; handler might have been registered too late to receive know-position this turn
      (about-to-choose [this _]
        (apply swap! (:game anbf) f args)
        (deregister-handler anbf this))
      KnowPositionHandler
      (know-position [this _]
        (apply swap! (:game anbf) f args)
        (deregister-handler anbf this)))))

(defn update-at-player-when-known
  "Update the tile at player's next known position by applying update-fn to its current value and args"
  [anbf update-fn & args]
  (update-on-known-position anbf #(apply update-at-player % update-fn args)))

(defn with-reason
  "Updates an action to attach reasoning (purely for debugging purposes)"
  [& reason+action]
  (let [action-idx (dec (count reason+action))
        action (nth reason+action action-idx)
        reason (->> reason+action (take action-idx)
                    (interpose \space) (apply str))]
    (if-let [a (and (some? action)
                    (if (fn? action)
                      (action)
                      action))]
      (update-in a [:reason] (fnil conj []) reason))))

(def no-monster-re #"You .* (thin air|empty water)" )

(defaction Attack [dir]
  (handler [_ {:keys [game] :as anbf}]
    (let [old-player (:player @game)]
      (reify ToplineMessageHandler
        (message [_ msg]
          (when-not (dizzy? old-player)
            (condp re-seq msg
              no-monster-re
              (swap! game remove-curlvl-monster (in-direction old-player dir))
              nil))))))
  (trigger [_]
    (str \F (or (vi-directions (enum->kw dir))
                (throw (IllegalArgumentException.
                         (str "Invalid direction: " dir)))))))

(defn- move-message-handler
  [{:keys [game] :as anbf} level portal-atom msg]
  (condp re-seq msg
        #".*: \"Closed for inventory\"" ; TODO possible degradation
        (update-before-action
          anbf (fn mark-shop [game]
                 (reduce #(if ((some-fn door? wall?) %2)
                            (update-curlvl-at %1 %2 assoc :room :shop)
                            %1)
                         (add-curlvl-tag game :shop-closed)
                         (neighbors level (:player game)))))
        #"You crawl to the edge of the pit\.|You disentangle yourself\."
        (swap! game assoc-in [:player :trapped] false)
        #"You fall into \w+ pit!|bear trap closes on your|You stumble into \w+ spider web!|You are stuck to the web\.|You are still in a pit"
        (do (swap! game assoc-in [:player :trapped] true)
            (update-at-player-when-known anbf update-in [:feature]
                                         #(if (traps %) % :trap)))
        #"trap door opens|trap door in the .*and a rock falls on you|trigger a rolling boulder|\(little dart|arrow\) shoots out at you|gush of water hits|tower of flame erupts"
        (update-at-player-when-known anbf assoc :feature :trap)
        #"You are carrying too much to get through"
        (swap! game assoc-in [:player :thick] true)
        #"activated a magic portal!"
        (do (reset! portal-atom true)
            (update-at-player-when-known anbf assoc :feature :portal))
        #"You feel a strange vibration"
        (do (swap! game add-curlvl-tag :end)
            (update-at-player-when-known anbf assoc :vibrating true))
        #"Wait!  That's a .*mimic!"
        (update-before-action
          anbf (fn [game]
                 (reduce #(if (= \m (:glyph (at-curlvl %1 %2)))
                            (update-curlvl-at %1 %2 assoc :feature nil)
                            %1)
                         game
                         (neighbors (:player game)))))
        nil))

(defn- portal-handler [{:keys [game] :as anbf} level new-dlvl]
  (or ; TODO planes
      (when (subbranches (branch-key @game level))
        (log/debug "leaving subbranch via portal")
        (swap! game assoc :branch-id :main))
      (when (= "Home" (subs new-dlvl 0 4))
        (log/debug "entering quest portal")
        (swap! game assoc :branch-id :quest))
      (when (> (dlvl-number new-dlvl) 40)
        (log/debug "entering wiztower portal")
        (swap! game assoc :branch-id :wiztower))
      (log/error "entered unknown portal!")))

(defn update-trapped-status [{:keys [game] :as anbf} old-pos]
  (update-on-known-position anbf
    #(if (or (= (position (:player %)) old-pos)
             (trap? (at-player @game)))
       %
       (do (if (:trapped (:player %))
             (log/debug "player moved => not :trapped anymore"))
           (assoc-in % [:player :trapped] false)))))

(defn- direction-trigger [dir]
  (str (or (vi-directions (enum->kw dir))
           (throw (IllegalArgumentException.
                    (str "Invalid direction: " dir))))))

(defaction Move [dir]
  (handler [_ {:keys [game] :as anbf}]
    (let [got-message (atom false)
          portal (atom false)
          old-player (:player @game)
          old-pos (position old-player)
          level (curlvl @game)
          target (in-direction level old-pos dir)]
      (update-trapped-status anbf old-pos)
      (if (and (not (:trapped old-player)) (diagonal dir) (item? target))
        (update-on-known-position anbf
          #(if (and (= (position (:player %)) old-pos)
                    (not (curlvl-monster-at % target))
                    (not @got-message))
             ; XXX in vanilla (or without the right option) this also happens with walls/rock, but NAO has a message
             (if (:feature target)
               (do (log/error "seemingly non diagonally walkable feature at"
                              target)
                   %)
               (do (log/debug "stuck on diagonal movement => possibly door at"
                              target)
                   (update-curlvl-at % (in-direction old-pos dir)
                                     assoc :feature :door-open)))
             %)))
      (reify
        ToplineMessageHandler
        (message [_ msg]
          (reset! got-message true)
          (or (move-message-handler anbf level portal msg)
              (when-not (dizzy? old-player)
                (condp re-seq msg
                  no-monster-re
                  (swap! game remove-curlvl-monster target)
                  #"You try to move the boulder, but in vain\."
                  (let [boulder-target (in-direction level target dir)]
                    (if (item? boulder-target)
                      (swap! game update-curlvl-at boulder-target
                             assoc :feature :door-open)
                      (swap! game update-curlvl-at boulder-target
                             assoc :feature :rock)))
                  #"It's a wall\."
                  (swap! game update-curlvl-at target assoc :feature :wall)
                  nil))))
        DlvlChangeHandler
        (dlvl-changed [_ old-dlvl new-dlvl]
          (if @portal
            (portal-handler anbf level new-dlvl)))
        ReallyAttackHandler
        (really-attack [_ _]
          (swap! game update-curlvl-monster (in-direction old-pos dir)
                 assoc :peaceful true)
          nil))))
  (trigger [_] (direction-trigger dir)))

(defaction Pray []
  ; TODO mark for timeout est.
  (handler [_ _])
  (trigger [_] "#pray\n"))

(defn- update-searched [{:keys [player] :as game}]
  (reduce #(update-curlvl-at %1 %2 update-in [:searched] inc) game
          (conj (neighbors player) player)))

(defaction Search []
  (handler [_ {:keys [game] :as anbf}]
    (swap! game update-searched) nil)
  (trigger [_] "s"))

(defaction Wait []
  (handler [_ _])
  (trigger [_] "."))

(defn- mark-branch-entrance [game tile branch origin-feature]
  "Mark where we ended up on the new level as leading to the branch we came from.  Pets and followers might have displaced us from the stairs which may not be visible, so just mark the surroundings too, it only matters for the stairs.  (Actually two sets of stairs may be next to each other and this breaks if that happens and the non-origin stairs are obscured)"
  (if (or (= :ludios (branch-key game)) (= "Home 1" (:dlvl game)))
    (update-curlvl-at game tile assoc :branch-id :main) ; mark portal
    (->> (conj (neighbors tile) tile)
         (remove (partial has-feature? origin-feature))
         (reduce #(update-curlvl-at %1 %2 assoc :branch-id branch) game))))

(defn stairs-handler [anbf]
  (let [old-game (-> anbf :game deref)
        old-branch (branch-key old-game)
        old-dlvl (:dlvl old-game)
        old-stairs (at-player old-game)
        entered-vlad (atom false)]
    (update-on-known-position anbf
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
              (mark-branch-entrance new-stairs old-branch
                                    (:feature old-stairs)))
          new-game)))
    (reify
      ToplineMessageHandler
      (message [_ text]
        (if (.contains text "heat and smoke are gone.")
          (reset! entered-vlad true)))
      DlvlChangeHandler
      (dlvl-changed [this old-dlvl new-dlvl]
        (swap! (:game anbf)
               #(let [new-branch (if @entered-vlad
                                   :vlad
                                   (get old-stairs :branch-id
                                        (initial-branch-id % new-dlvl)))]
                  (-> %
                      (update-in [:dungeon :levels old-branch old-dlvl :tags]
                                 conj new-branch)
                      (assoc :branch-id new-branch))))
        (log/debug "choosing branch-id" (:branch-id @(:game anbf))
                   "for dlvl" new-dlvl)))))

(defaction Ascend []
  (handler [_ anbf]
    (stairs-handler anbf))
  (trigger [_] "<"))

(defaction Descend []
  (handler [_ anbf]
    (stairs-handler anbf))
  (trigger [_] ">"))

(defaction Kick [dir]
  (handler [_ {:keys [game] :as anbf}]
    (reify ToplineMessageHandler
      (message [_ msg]
        (condp re-seq msg
          #"Your .* is in no shape for kicking."
          (swap! game assoc-in [:player :leg-hurt] true)
          #"You can't move your leg!|There's not enough room to kick down here."
          (swap! game assoc-in [:player :trapped] true)
          nil))))
  (trigger [_] (str (ctrl \d) (vi-directions (enum->kw dir)))))

(defaction Close [dir]
  (handler [_ {:keys [game] :as anbf}]
    (reify ToplineMessageHandler
      (message [_ text]
        (let [door (in-direction (:player @game) dir)]
          (case text
            "This door is already closed." (swap! game update-curlvl-at door
                                                  assoc :feature :door-closed)
            "This doorway has no door." (swap! game update-curlvl-at door
                                               assoc :feature nil)
            "You see no door there." (swap! game update-curlvl-at door
                                            assoc :feature nil)
            nil)))))
  (trigger [this] (str \c (vi-directions (enum->kw dir)))))

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

(defaction Look []
  (handler [_ {:keys [game delegator] :as anbf}]
    (let [has-item (atom false)]
      (swap! game #(if-not (blind? (:player %))
                     (update-at-player % assoc :seen true :new-items false)
                     %))
      (update-on-known-position anbf
        (fn after-look [game]
          (if @has-item
            (send delegator found-items (:items (at-player game))))
          (as-> game res
            (update-at-player res assoc :examined (:turn game))
            (if (unknown? (at-player res))
              (update-at-player res assoc :feature :floor)
              res) ; got no topline message suggesting a special feature
            (if-not @has-item
              (update-at-player res assoc :items [])
              res))))
      ; XXX note: items on tile HAVE to be determined via this command only, topline messages on Move are not reliable due to teletraps
      (reify
        MultilineMessageHandler
        (message-lines [_ lines]
          (condp re-seq (nth lines 0)
            #"^(?:Things that (?:are|you feel) here:|You (?:see|feel))"
            (let [items (mapv label->item (subvec lines 1))
                  top-item (nth items 0)]
              (log/debug "Items here:" (log/spy items))
              (reset! has-item true)
              (swap! game #(update-at-player %
                             assoc :items items
                                   :item-glyph (item-glyph % top-item)
                                   :item-color nil)))
            (log/error "Unrecognized message list " (str lines))))
        ToplineMessageHandler
        (message [_ text]
          (when-not (and (= text "But you can't reach it!")
                         (reset! has-item true))
            (when-let [item (some->> text
                                     (re-first-group
                                       #"You (?:see|feel) here ([^.]+).")
                                     label->item)]
              (log/debug "Single item here:" item)
              (reset! has-item true)
              (swap! game #(update-at-player %
                             assoc :items [item]
                                   :item-glyph (item-glyph % item)
                                   :item-color nil)))
            (if-let [feature (feature-here text (:rogue (curlvl-tags @game)))]
              (swap! game update-at-player assoc :feature feature)
              (if (trap? (at-player @game))
                (swap! game update-at-player assoc :feature :floor))))))))
  (trigger [this] ":"))

(def farlook-monster-re #"^.     *[^(]*\(([^,)]*)(?:,[^)]*)?\)|a (mimic) or a strange object$")
(def farlook-trap-re #"^\^ * a trap \(([^)]*)\)")

(defaction FarLook [pos]
  (handler [_ {:keys [game] :as anbf}]
    (reify ToplineMessageHandler
      (message [_ text]
        (or (when-let [trap (re-first-group farlook-trap-re text)]
              (swap! game update-curlvl-at pos assoc :feature
                     (or (trap-names trap)
                         (throw (IllegalArgumentException.  (str "unknown farlook trap: " text " >>> " trap))))))
            (when-let [desc (and (monster-glyph? (nth text 0))
                                 (re-any-group farlook-monster-re text))]
              (let [peaceful? (.startsWith ^String desc "peaceful")
                    type (by-description desc)]
                (log/debug "monster description" text "=>" type)
                (swap! game update-curlvl-monster pos assoc
                       :peaceful peaceful?
                       :type type)))
            (log/debug "non-monster farlook result:" text)))))
  (trigger [this]
    (str \; (to-position pos) \.)))

(defn- handle-door-message [game dir text]
  (let [door (in-direction (:player @game) dir)
        new-feature (case text
                      "You cannot lock an open door." :door-open
                      "This door is locked." :door-locked
                      "This door is already open." :door-open
                      "The door opens." :door-open
                      "This doorway has no door." nil
                      "You see no door there." nil
                      "You succeed in picking the lock." :door-closed
                      "You succeed in unlocking the door." :door-closed
                      "You succeed in locking the door." :door-locked
                      :nil)]
    (if (not= :nil new-feature)
      (swap! game update-curlvl-at door assoc :feature new-feature))))

(defaction Open [dir]
  (handler [_ {:keys [game] :as anbf}]
    (reify ToplineMessageHandler
      (message [_ text]
        (handle-door-message game dir text))))
  (trigger [_] (str \o (vi-directions (enum->kw dir)))))

(defaction Inventory []
  (handler [_ {:keys [game] :as anbf}]
    (reify InventoryHandler
      (inventory-list [_ inventory]
        (swap! game assoc-in [:player :inventory]
               (into {} (for [[c i] inventory] (slot-item c i)))))))
  (trigger [_] "i"))

(defn- examine-tile [{:keys [player] :as game}]
  (let [tile (at-player game)]
    (when (and (not (blind? player))
               (not (have-levi-on game))
               (or ((some-fn unknown? unknown-trap? :new-items) tile)
                   (and (seq (:items tile))
                        (not= (:last-position game) (position player))
                        (not= :search (some-> game :last-action typekw))
                        (not= (:turn game) (:examined tile)))))
      (with-reason "examining tile" (pr-str tile) ->Look))))

(defn- examine-traps [game]
  (some->> (curlvl game) tile-seq
           (find-first (every-pred unknown-trap?
                                   (complement item?)
                                   (complement monster?)))
           ->FarLook
           (with-reason "examining unknown trap")))

(defn- examine-monsters [{:keys [player] :as game}]
  (when-not (:hallu (:state player))
    (when-let [m (->> (curlvl-monsters game) vals
                      (remove (some-fn :remembered
                                       :fiendly
                                       (comp some? :peaceful)
                                       (comp #{\I \1 \2 \3 \4 \5} :glyph)))
                      first)]
      (with-reason "examining monster" (->FarLook m)))))

(defn examine-handler [anbf]
  (reify ActionHandler
    (choose-action [_ game]
      (or (examine-tile game)
          (examine-monsters game)
          (examine-traps game)))))

(defn- inventory-handler [anbf]
  (reify ActionHandler
    (choose-action [this game]
      (deregister-handler anbf this)
      (with-reason "requested inventory update" (->Inventory)))))

(def ^:private inventory-handler (memoize inventory-handler))

(defn update-inventory
  "Re-check inventory on the next action"
  [anbf]
  (register-handler anbf priority-top (inventory-handler anbf)))

(defn update-items
  "Re-check current tile for items on the next action"
  [anbf]
  (update-at-player-when-known anbf assoc :new-items true))

(def ^:private discoveries-re #"(Artifacts|Unique Items|Spellbooks|Amulets|Weapons|Wands|Gems|Armor|Food|Tools|Scrolls|Rings|Potions)|(?:\* )?([^\(]*) \(([^\)]*)\)$|^([^(]+)$")

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

(defaction Discoveries []
  (handler [_ {:keys [game] :as anbf}]
    (reify MultilineMessageHandler
      (message-lines [this lines-all]
        (swap! game add-discoveries
               (loop [section nil
                      lines lines-all
                      discoveries []]
                 (if-let [line (first lines)]
                   (let [[group id appearance _] (re-first-groups
                                                   discoveries-re line)]
                     (if group
                       (recur group (rest lines) discoveries)
                       (if (= section "Unique Items")
                         (recur section (rest lines) discoveries)
                         (recur section (rest lines)
                                (conj discoveries
                                      [(discovery-demangle
                                         section appearance) id])))))
                   discoveries))))))
  (trigger [_] "\\"))

(defn- discoveries-handler [anbf]
  (reify ActionHandler
    (choose-action [this game]
      (deregister-handler anbf this)
      (->Discoveries))))

(def ^:private discoveries-handler (memoize discoveries-handler))

(defn update-discoveries
  "Re-check discoveries on the next action"
  [anbf]
  (register-handler anbf priority-top (discoveries-handler anbf)))

(defaction Apply [slot]
  (handler [_ {:keys [game] :as anbf}]
    (reify ApplyItemHandler
      (apply-what [_ _] slot)))
  (trigger [_] "a"))

(defn with-handler
  ([handler action]
   (with-handler action priority-default handler))
  ([priority handler action]
   (update-in action [:handlers] conj [priority handler])))

(defn ->ApplyAt
  "Apply something in the given direction (eg. pickaxe, key...).
  Assumes the thing is already wielded (if it has to be like a pickaxe), otherwise resulting direction prompt may be misparsed after auto-wield if something happens during the wielding"
  [slot dir]
  (->> (->Apply slot)
       (with-handler priority-top
         (fn [{:keys [game] :as anbf}]
           (reify
             DirectionHandler
             (what-direction [_ _] dir)
             ToplineMessageHandler
             (message [_ msg]
               (condp re-seq msg
                 #"you can't dig while entangled"
                 (swap! game assoc-in [:player :trapped] true)
                 #"This wall (seems|is) too hard to dig into\."
                 (swap! game #(update-curlvl-at % (in-direction (:player %) dir)
                                               assoc :undiggable true))
                 #"You make an opening"
                 (swap! game #(update-curlvl-at % (in-direction (:player %) dir)
                                               assoc :dug true :feature :floor))
                 #"You succeed in cutting away some rock"
                 (swap! game #(update-curlvl-at % (in-direction (:player %) dir)
                                            assoc :dug true :feature :corridor))
                 #"^You swing your pick"
                 (swap! game #(update-curlvl-at % (in-direction (:player %) dir)
                                                assoc :feature nil))
                 #"here is too hard to"
                 (swap! game add-curlvl-tag :undiggable-floor)
                 #"You dig a pit"
                 (swap! game update-at-player assoc :feature :pit)
                 #"You dig a hole through"
                 (swap! game update-at-player assoc :feature :hole)
                 nil)))))))

(defaction ->ForceLock []
  (handler [_ {:keys [game] :as anbf}]
    (reify ForceLockHandler
      (force-lock [_ _] true)))
  (trigger [_] "#force\n"))

(defn ->Unlock [slot dir]
  (->> (->ApplyAt slot dir)
       (with-handler priority-top
         (fn [{:keys [game] :as anbf}]
           (reify
             ToplineMessageHandler
             (message [_ msg]
               (handle-door-message game dir msg))
             LockHandler
             (lock-it [_ _]
               (if (dirmap dir)
                 (swap! game #(update-curlvl-at % (in-direction (:player %) dir)
                                                assoc :feature :door-closed)))
               false)
             (unlock-it [_ _]
               (if (dirmap dir)
                 (swap! game #(update-curlvl-at % (in-direction (:player %) dir)
                                                assoc :feature :door-locked)))
               true))))))

(defn- possible-autoid
  "Check if the item at slot may have auto-identified on use"
  [{:keys [game] :as anbf} slot]
  (if-not (know-id? @game (inventory-slot @game slot))
    (update-discoveries anbf)))

(defaction Wield [slot]
  (handler [_ {:keys [game] :as anbf}]
    (update-inventory anbf)
    (possible-autoid anbf slot)
    (reify WieldItemHandler
      (wield-what [_ _] slot)))
  (trigger [_] "w"))

(defaction Wear [slot]
  (handler [_ {:keys [game] :as anbf}]
    (update-inventory anbf)
    (possible-autoid anbf slot)
    (reify WearItemHandler
      (wear-what [_ _] slot)))
  (trigger [_] "W"))

(defaction PutOn [slot]
  (handler [_ {:keys [game] :as anbf}]
    (update-inventory anbf)
    (possible-autoid anbf slot)
    (reify PutOnItemHandler
      (put-on-what [_ _] slot)))
  (trigger [_] "P"))

(defaction Remove [slot]
  (handler [_ anbf]
    (update-inventory anbf)
    (reify RemoveItemHandler
      (remove-what [_ _] slot)))
  (trigger [_] "R"))

(defaction TakeOff [slot]
  (handler [_ anbf]
    (update-inventory anbf)
    (reify TakeOffItemHandler
      (take-off-what [_ _] slot)))
  (trigger [_] "T"))

(defaction DropSingle [slot qty]
  (handler [_ anbf]
    (update-inventory anbf)
    (update-items anbf)
    (swap! (:game anbf) update-in [:player] dissoc :thick)
    (reify DropSingleHandler
      (drop-single [_ _] (str (if (> qty 1) qty) slot))))
  (trigger [_] "d"))

(defaction Quiver [slot]
  (handler [_ anbf]
    (update-inventory anbf)
    (reify QuiverHandler
      (ready-what [_ _] slot)))
  (trigger [_] "Q"))

(defn ->Drop
  ([slot-or-list qty]
   (if (char? slot-or-list)
     (->DropSingle slot-or-list qty)
     (throw (UnsupportedOperationException. "multidrop not yet implemented"))))
  ([slot-or-list]
   (->DropSingle slot-or-list 1)))

(defaction PickUp [label-or-list]
  (handler [_ anbf]
    (update-inventory anbf)
    (update-items anbf)
    (let [labels (if (string? label-or-list)
              (multiset label-or-list)
              (into (multiset) label-or-list))
          remaining (atom labels)]
      (reify PickupHandler
        (pick-up-what [_ options]
          (log/debug options)
          (log/debug "want" remaining)
          (loop [opts options
                 res #{}]
            (if-let [[slot lbl] (first opts)]
              (if (contains? @remaining lbl)
                (do (swap! remaining disj lbl)
                    (recur (rest opts) (conj res slot)))
                (recur (rest opts) res))
              res))))))
  (trigger [_] ","))

(defaction Autotravel [pos]
  (handler [this {:keys [game] :as anbf}]
    (let [level (curlvl @game)
          portal (atom nil)]
      (reify
        ToplineMessageHandler
        (message [_ msg]
          (move-message-handler anbf level portal msg))
        DlvlChangeHandler
        (dlvl-changed [_ old-dlvl new-dlvl]
          (if @portal
            (portal-handler anbf level new-dlvl)))
        AutotravelHandler
        (travel-where [_] pos))))
  (trigger [_] "_"))

(defn- -withHandler
  ([action handler]
   (-withHandler action priority-default handler))
  ([action priority handler]
   (with-handler action priority handler)))

(defn dig [[slot item] dir]
  (if (:in-use item)
    (->ApplyAt slot dir)
    (->Wield slot)))

(defn- use-action [item]
  (case (typekw item)
    :ring ->PutOn
    :amulet ->PutOn
    :tool ->PutOn
    :armor ->Wear))

(defn make-use [game slot]
  (if-let [itemtype (some->> slot (inventory-slot game) (item-id game))]
    ; TODO if already occupied
    ((use-action itemtype) slot)))

(defn- remove-action [item]
  (case (typekw item)
    :ring ->Remove
    :amulet ->Remove
    :tool ->Remove
    :armor ->TakeOff))

(defn remove-use [game slot]
  (let [item (inventory-slot game slot)
        itemtype (item-id game item)]
    (if (and itemtype (not= :cursed (:buc item)))
      ((remove-action itemtype) slot))))

; factory functions for Java bots ; TODO the rest
(gen-class
  :name anbf.bot.Actions
  :methods [^:static [Move [anbf.bot.Direction] anbf.bot.IAction]
            ^:static [Pray [] anbf.bot.IAction]
            ^:static [withHandler [anbf.bot.IAction Object] anbf.bot.IAction]
            ^:static [withHandler [anbf.bot.IAction int Object] anbf.bot.IAction]])
