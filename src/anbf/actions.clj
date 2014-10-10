(ns anbf.actions
  (:require [clojure.tools.logging :as log]
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
         (~'typekw [~'_] ~(str->kw action))
         anbf.bot.IAction
         ~@impl)
       (defn ~(symbol (str \- action)) ~args
         (~(symbol (str action \.)) ~@args))))

(def ^:private feature-re #"^(?:You see|There is|You escape)(?: an?| your)?(?: \w+)* (falling rock trap|rolling boulder trap|rust trap|statue trap|magic trap|anti-magic field|polymorph trap|fire trap|arrow trap|dart trap|land mine|teleportation trap|sleeping gas trap|magic portal|level teleporter|bear trap|spiked pit|pit|staircase (?:up|down)|spider web|web|ice|opulent throne|hole|trap door|fountain|sink|grave|doorway|squeaky board|open door|broken door)(?: here| below you)?\.")

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
                               "staircase up" :stairs-up
                               "staircase down" :stairs-down
                               "fountain" :fountain
                               "sink" :sink
                               "grave" :grave
                               (trap-names feature-txt))))
      (if (trap? tile) ; no feature msg => no trap
        (assoc tile :feature :floor))
      tile))

(defn with-reason
  "Updates an action to attach reasoning (purely for debugging purposes)"
  [& reason+action]
  (let [action-idx (dec (count reason+action))
        action (nth reason+action action-idx)
        reason (->> reason+action (take action-idx)
                    (interpose \space) (apply str))]
    (if-let [a (if (fn? action)
                 (action)
                 action)]
      (update a :reason (fnil conj []) reason))))

(def no-monster-re #"You .* (thin air|empty water)" )

(defaction Attack [dir]
  (handler [_ {:keys [game] :as anbf}]
    (let [old-player (:player @game)]
      (reify ToplineMessageHandler
        (message [_ msg]
          (when-let [target (and (re-seq no-monster-re msg)
                                 (not (dizzy? old-player))
                                 (in-direction old-player dir))]
            (swap! game update-curlvl-at target
                   #(if (blank? %) (assoc % :feature :rock) %))
            (swap! game remove-curlvl-monster target))))))
  (trigger [_]
    (str \F (or (vi-directions (enum->kw dir))
                (throw (IllegalArgumentException.
                         (str "Invalid direction: " dir)))))))

(defn mark-trap-here [anbf]
  (update-at-player-when-known anbf update :feature #(if (traps %) % :trap)))

(defn- move-message-handler
  [{:keys [game] :as anbf} portal-atom msg]
  (condp re-seq msg
    #".*: \"Closed for inventory\"" ; TODO possible degradation
    (update-before-action
      anbf (fn mark-shop [game]
             (->> (:player game)
                  (neighbors (curlvl game))
                  (find-first door?)
                  (including-origin straight-neighbors (curlvl game))
                  (remove #(= (position (:player game)) (position %)))
                  (reduce #(update-curlvl-at %1 %2 assoc :room :shop)
                          (add-curlvl-tag game :shop-closed)))))
    #"You crawl to the edge of the pit\.|You disentangle yourself\."
    (swap! game assoc-in [:player :trapped] false)
    #"You fall into \w+ pit!|bear trap closes on your|You stumble into \w+ spider web!|You are stuck to the web\.|You are still in a pit|notice a loose board"
    (do (swap! game assoc-in [:player :trapped] true)
        (mark-trap-here anbf))
    #"trap door opens|trap door in the .*and a rock falls on you|trigger a rolling boulder|\(little dart|arrow\) shoots out at you|gush of water hits|tower of flame erupts|cloud of gas"
    (mark-trap-here anbf)
    #"You are carrying too much to get through"
    (swap! game assoc-in [:player :thick] true)
    #"activated a magic portal!"
    (reset! portal-atom true)
    #"You feel a strange vibration"
    (do (swap! game add-curlvl-tag :end)
        (update-at-player-when-known anbf assoc :vibrating true))
    #"Wait!  That's a .*mimic!"
    (update-before-action
      anbf (fn [game]
             (->> (neighbors (curlvl game) (:player game))
                  (filter #(= \m (:glyph %)))
                  (reduce #(update-curlvl-at %1 %2 assoc :feature nil)
                          game))))
    nil))

(defn- portal-handler [{:keys [game] :as anbf} level new-dlvl]
  (or (when (= "Astral Plane" new-dlvl)
        (log/debug "entering astral")
        (swap! game assoc :branch-id :astral))
      (when (subbranches (branch-key @game level))
        (log/debug "leaving subbranch via portal")
        (swap! game assoc :branch-id :main))
      (when (= "Home" (subs new-dlvl 0 4))
        (log/debug "entering quest portal")
        (swap! game assoc :branch-id :quest))
      (when (> (dlvl-number new-dlvl) 35)
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
               % ; NH actually seems to ignore a move in some cases when stepping on newly-discovered trap
               (do (log/debug "stuck on diagonal movement => possibly door at"
                              target)
                   (update-curlvl-at % (in-direction old-pos dir)
                                     assoc :feature :door-open)))
             %)))
      (reify
        ToplineMessageHandler
        (message [_ msg]
          (reset! got-message true)
          (or (move-message-handler anbf portal msg)
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
                  (swap! game update-curlvl-at target
                         #(assoc % :feature (if (blank? %) :rock :wall)))
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
  (handler [_ anbf]
    (swap! (:game anbf) #(assoc % :last-prayer (:turn %))))
  (trigger [_] "#pray\n"))

(defn- update-searched [{:keys [player turn] :as game} start]
  ; TODO maybe take speed into consideration
  (let [turns (inc (- turn start))]
    (reduce #(update-curlvl-at %1 %2 update :searched (partial + turns)) game
            (including-origin neighbors player))))

(defaction Search []
  (handler [_ {:keys [game] :as anbf}]
    (update-on-known-position anbf update-searched (:turn @game))
    nil)
  (trigger [_] "s"))

(defaction Wait []
  (handler [_ _])
  (trigger [_] "."))

(defn- mark-branch-entrance [game tile old-game origin-feature]
  "Mark where we ended up on the new level as leading to the branch we came from.  Pets and followers might have displaced us from the stairs which may not be visible, so mark the surroundings too to be sure (but two sets of stairs may be next to each other and this breaks if that happens and there are some followers... too bad)"
  (if (or (= :ludios (branch-key game)) (= "Home 1" (:dlvl game)))
    (update-curlvl-at game tile assoc :branch-id :main) ; mark portal
    (if (some (some-fn :friendly (comp :follows :type))
              (mapcat (partial monster-at (curlvl old-game))
                      (neighbors (:player old-game))))
      (->> (including-origin neighbors (curlvl game) tile)
           (remove #(has-feature? % origin-feature))
           (reduce #(update-curlvl-at %1 %2 assoc
                                      :branch-id (branch-key old-game)) game))
      (update-curlvl-at game tile assoc :branch-id (branch-key old-game)))))

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
              (mark-branch-entrance new-stairs old-game
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

(def things-re #"^Things that (?:are|you feel) here:|You (?:see|feel)")
(def thing-re #"You (?:see|feel) here ([^.]+).")

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
            (if ((some-fn unknown? unknown-trap?) (at-player res))
              (update-at-player res assoc :feature :floor)
              res) ; got no topline message suggesting a special feature
            (if-not @has-item
              (update-at-player res assoc :items [])
              res))))
      ; XXX note: items on tile HAVE to be determined via this command only, topline messages on Move are not reliable due to teletraps
      (reify
        MultilineMessageHandler
        (message-lines [_ lines]
          (if (re-seq things-re (first lines))
            (let [items (mapv label->item (subvec lines 1))
                  top-item (nth items 0)]
              (log/debug "Items here:" (log/spy items))
              (reset! has-item true)
              (swap! game #(update-at-player %
                             assoc :items items
                                   :item-glyph (item-glyph % top-item)
                                   :item-color nil)))))
        ToplineMessageHandler
        (message [_ text]
          (when-not (and (= text "But you can't reach it!")
                         (reset! has-item true))
            (when-let [item (some->> text
                                     (re-first-group thing-re)
                                     label->item)]
              (log/debug "Single item here:" item)
              (reset! has-item true)
              (swap! game #(update-at-player %
                             assoc :items [item]
                                   :item-glyph (item-glyph % item)
                                   :item-color nil)))
            (swap! game update-at-player feature-msg-update
                   text (:rogue (curlvl-tags game))))))))
  (trigger [this] ":"))

(def farlook-monster-re #"^.     *[^(]*\(([^,)]*)(?:,[^)]*)?\)|a (mimic) or a strange object$")
(def farlook-trap-re #"^\^ * a trap \(([^)]*)\)")

(defaction FarLook [pos]
  (handler [_ {:keys [game] :as anbf}]
    (reify ToplineMessageHandler
      (message [_ text]
        (or (when-let [align (re-first-group #"\(([^ ]*) altar\)$" text)]
              (swap! game update-curlvl-at pos assoc
                     :alignment (str->kw align)))
            (when-let [trap (re-first-group farlook-trap-re text)]
              (swap! game update-curlvl-at pos assoc :feature
                     (or (trap-names trap)
                         (throw (IllegalArgumentException. (str "unknown farlook trap: " text " >>> " trap))))))
            (when-let [desc (and (monster-glyph? (nth text 0))
                                 (re-any-group farlook-monster-re text))]
              (let [peaceful? (.startsWith ^String desc "peaceful")
                    typename (by-description desc)]
                (log/debug "monster description" text "=>" typename)
                (swap! game update-curlvl-monster pos assoc
                       :peaceful peaceful?
                       :type (name->monster typename))))
            (log/debug "non-monster farlook result:" text)))))
  (trigger [this]
    (str \; (to-position pos) \.)))

(defn- handle-door-message [game dir text]
  (let [door (in-direction (:player @game) dir)
        new-feature (condp #(.contains %2 %1) text
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
  (if-let [tile (and (not (blind? player))
                     (at-player game))]
    (if ((some-fn :new-items unknown? unknown-trap?
                  (every-pred altar? (complement :alignment))) tile)
      (with-reason "examining tile" (pr-str tile) ->Look))))

(defn- examine-features [game]
  (some->> (curlvl game) tile-seq
           (find-first (every-pred #(or (unknown-trap? %)
                                        (and (altar? %) (not (:alignment %))
                                             (not= :astral (branch-key game))))
                                   (complement item?)
                                   (complement monster?)))
           ->FarLook
           (with-reason "examining ambiguous feature")))

(defn- examine-monsters [{:keys [player] :as game}]
  (when-not (hallu? player)
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
          (examine-features game)))))

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
  (register-handler anbf (dec priority-top) (discoveries-handler anbf)))

(defaction Name [slot name]
  (handler [_ {:keys [game] :as anbf}]
    (update-inventory anbf)
    (reify
      NameMenuHandler
      (name-menu [_ _] \b)
      NameWhatHandler
      (name-what [_ _] slot)
      NameItemHandler
      (name-item [_ _] name)))
  (trigger [_] "#name\n"))

(defn update-name [anbf slot name]
  "Name an item on the next action"
  (register-handler anbf priority-top
                    (reify ActionHandler
                      (choose-action [this game]
                        (deregister-handler anbf this)
                        (->Name slot name)))))

(defaction Apply [slot]
  (handler [_ {:keys [game] :as anbf}]
    (reify
      AttachCandlesHandler
      (attach-candelabrum-candles [_ _]
        (update-inventory anbf))
      ToplineMessageHandler
      (message [_ msg]
        (condp re-seq msg
          #"has no oil|has run out of power"
          (update-name anbf slot "empty")
          #" lamp is now (on|off)|burns? brightly!|You light your |^You snuff "
          (update-inventory anbf)
          nil))
      ApplyItemHandler
      (apply-what [_ _] slot)))
  (trigger [_] "a"))

(defn with-handler
  ([handler action]
   (with-handler action priority-default handler))
  ([priority handler action]
   (update action :handlers conj [priority handler])))

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
                 (if-not (:orcus (:tags (curlvl @game)))
                   (swap! game #(update-curlvl-at %
                                                  (in-direction (:player %) dir)
                                                  assoc :undiggable true)))
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

(defaction ForceLock []
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
  "Check if the item at slot auto-identified on use"
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
    (swap! (:game anbf) update :player dissoc :thick)
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
    (let [pos (position pos)
          level (curlvl @game)
          portal (atom nil)
          path (set (rest (:path this)))]
      (swap! game assoc :last-autonav pos :autonav-stuck false)
      (reify
        KnowPositionHandler
        (know-position [this {:keys [cursor]}]
          (when (and (seq path) (not= pos cursor)
                     (not-any? path (neighbors cursor)))
            ; when autonav diverges from the intended path, this should prevent a cycle
            (log/debug "autonav stuck")
            (swap! game assoc :autonav-stuck true)))
        ToplineMessageHandler
        (message [_ msg]
          (move-message-handler anbf portal msg))
        DlvlChangeHandler
        (dlvl-changed [_ old-dlvl new-dlvl]
          (if @portal
            (portal-handler anbf level new-dlvl)))
        AutotravelHandler
        (travel-where [_] pos))))
  (trigger [_] "_"))

(defaction Enhance []
  (handler [_ {:keys [game] :as anbf}]
    (log/error "TODO")
    #_(swap! game (assoc-in [:player :can-enhance] nil)))
  (trigger [_] "#enhance\n"))

(defn- -withHandler
  ([action handler]
   (-withHandler action priority-default handler))
  ([action priority handler]
   (with-handler action priority handler)))

(defn- identify-slot [game slot id]
  (add-discovery game (:name (inventory-slot game slot)) id))

(defaction Read [slot]
  (handler [_ {:keys [game] :as anbf}]
    (update-inventory anbf)
    (reify
      ReadWhatHandler
      (read-what [_ _] slot)
      ToplineMessageHandler
      (message [_ msg]
        (condp re-seq msg
          #"Your [a-z]* (glow|begin to glow|tingle|begin to tingle)|A faint (buzz|glow) surrounds your [a-z]*\.|You feel confused\."
          (swap! game identify-slot slot "scroll of confuse monster")
          #"You feel like someone is helping you\.|You feel in touch with the Universal Oneness\.|You feel like you need some help\.|You feel the power of the Force against you"
          (swap! game identify-slot slot "scroll of remove curse")
          #"You hear maniacal laughter|You hear sad wailing"
          (swap! game identify-slot slot "scroll of scare monster")
          nil))))
  (trigger [_] "r"))

(defaction Sit []
  (handler [_ {:keys [game] :as anbf}]
    (let [portal (atom nil)
          level (curlvl @game)]
      (reify
        ToplineMessageHandler
        (message [_ msg]
          (condp re-seq
            #"Having fun sitting on the (floor|air)\?"
            (swap! game update-at-player assoc :feature :floor)
            (move-message-handler anbf portal msg)))
        DlvlChangeHandler
        (dlvl-changed [_ old-dlvl new-dlvl]
          (if @portal
            (portal-handler anbf level new-dlvl))))))
  (trigger [_] "#sit\n"))

(defaction Eat [slot-or-label]
  (handler [_ anbf]
    (reify
      ToplineMessageHandler
      (message [_ msg]
        (when (= msg "You don't have anything to eat.")
          (update-inventory anbf)
          (update-items anbf)))
      EatItHandler
      (eat-it [_ what]
        (if (and (string? slot-or-label)
                 (= what slot-or-label))
          (update-items anbf)
          false))
      EatWhatHandler
      (eat-what [_ _]
        (when (char? slot-or-label)
          (update-inventory anbf)
          slot-or-label))))
  (trigger [_] "e"))

(defaction Quaff [slot]
  (handler [_ {:keys [game] :as anbf}]
    (update-inventory anbf)
    (reify DrinkWhatHandler
      (drink-what [_ _] slot)))
  (trigger [_] "q"))

(defn use-action [item]
  (case (item-type item)
    :ring ->PutOn
    :amulet ->PutOn
    :tool ->PutOn
    :armor ->Wear))

(defn make-use [game slot]
  ; TODO if slot already occupied
  ((use-action (inventory-slot game slot)) slot))

(defn remove-action [item]
  (case (item-type item)
    :ring ->Remove
    :amulet ->Remove
    :tool ->Remove
    :armor ->TakeOff))

(defn remove-use [game slot]
  (let [item (inventory-slot game slot)]
    (if (noncursed? item)
      ((remove-action item) slot)
      (log/warn "tried to remove cursed item"))))

(defn without-levitation [game action]
  ; XXX doesn't work for intrinsic levitation
  (if-let [[slot _] (and (not= :air (branch-key game))
                         (have-levi-on game))]
    (with-reason "action" (typekw action) "forbids levitation"
      (remove-use game slot))
    action))

(defn dig [[slot item] dir]
  (if (:in-use item)
    (->ApplyAt slot dir)
    (->Wield slot)))

(defn descend [game]
  (without-levitation game (->Descend)))

(defaction Repeated [action n]
  (handler [_ anbf] (handler action anbf))
  (trigger [_] (str n (trigger action))))

(defn search
  "Search once or n times"
  ([] (search 1))
  ([n] (->Repeated (->Search) n)))

; factory functions for Java bots ; TODO the rest
(gen-class
  :name anbf.bot.Actions
  :methods [^:static [Move [anbf.bot.Direction] anbf.bot.IAction]
            ^:static [Pray [] anbf.bot.IAction]
            ^:static [withHandler [anbf.bot.IAction Object] anbf.bot.IAction]
            ^:static [withHandler [anbf.bot.IAction int Object] anbf.bot.IAction]])
