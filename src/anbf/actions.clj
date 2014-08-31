(ns anbf.actions
  (:require [clojure.tools.logging :as log]
            [anbf.handlers :refer :all]
            [anbf.action :refer :all]
            [anbf.player :refer :all]
            [anbf.montype :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.tile :refer :all]
            [anbf.item :refer :all]
            [anbf.itemid :refer :all]
            [anbf.position :refer :all]
            [anbf.delegator :refer :all]
            [anbf.util :refer :all]))

(defmacro ^:private defaction [action args & impl]
  `(do (defrecord ~action ~args anbf.bot.IAction ~@impl)
       (defn ~(symbol (str \- action)) ~args
         (~(symbol (str action \.)) ~@args))))

(def vi-directions
  {:NW \y :N \k :NE \u
   :W  \h        :E \l
   :SW \b :S \j :SE \n})

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

(defn update-on-known-position
  "When player position on map is known call (apply swap! game f args)"
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

(defaction Move [dir]
  (handler [_ {:keys [game] :as anbf}]
    (let [got-message (atom false)
          portal (atom false)
          old-player (:player @game)
          old-pos (position old-player)
          level (curlvl @game)
          target (in-direction level old-pos dir)]
      (update-on-known-position anbf
        #(if (or (= (position (:player %)) old-pos)
                 (trap? (at-player @game)))
           %
           (assoc-in % [:player :trapped] false)))
      (if (and (diagonal dir) (item? target))
        (update-on-known-position anbf
          #(if (and (= (position (:player %)) old-pos)
                    (not (curlvl-monster-at % target))
                    (not @got-message))
             ; XXX in vanilla (or without the right option) this also happens with walls/rock, but NAO has a message
             (do (log/debug "stuck on diagonal movement => door at" target)
                 (update-curlvl-at % (in-direction old-pos dir)
                                   assoc :feature :door-open))
             %)))
      (reify
        ToplineMessageHandler
        (message [_ msg]
          (reset! got-message true)
          (or (condp re-seq msg
                #".*: \"Closed for inventory\"" ; TODO possible degradation
                (update-on-known-position
                  anbf (fn mark-shop [game]
                         (reduce #(if (or (door? %2) (= :wall (:feature %2)))
                                    (update-curlvl-at %1 %2 assoc :room :shop)
                                    %1)
                                 (add-curlvl-tag game :shop-closed)
                                 (neighbors level (:player game)))))
                #"You crawl to the edge of the pit\.|You disentangle yourself\."
                (swap! game assoc-in [:player :trapped] false)
                #"You fall into \w+ pit!|bear trap closes on your|You stumble into \w+ spider web!|You are stuck to the web\."
                (do (swap! game assoc-in [:player :trapped] true)
                    (update-on-known-position anbf
                      #(update-curlvl-at % (:player %) assoc :feature :trap)))
                #"trap door in the .*and a rock falls on you|trigger a rolling boulder" ; TODO dart/arrow/..
                (update-on-known-position anbf
                  #(update-curlvl-at % (:player %) assoc :feature :trap))
                #"You are carrying too much to get through"
                (swap! game assoc-in [:player :thick] true)
                #"activated a magic portal!"
                (do (reset! portal true)
                    (update-on-known-position anbf
                      #(update-curlvl-at % (:player %) assoc :feature :portal)))
                #"You feel a strange vibration"
                (update-on-known-position anbf
                      #(update-curlvl-at % (:player %) assoc :vibrating true))
                nil)
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
                  #"Wait!  That's a .*mimic!"
                  (swap! game update-curlvl-at target assoc :feature nil)
                  nil))))
        DlvlChangeHandler
        (dlvl-changed [_ old-dlvl new-dlvl]
          (if @portal
            (or ; TODO planes
                (if (subbranches (branch-key @game level))
                  (swap! game assoc :branch-id :main))
                (if (= "Home" (subs new-dlvl 0 4))
                  (swap! game assoc :branch-id :quest))
                (log/error "entered unknown portal!"))))
        ReallyAttackHandler
        (really-attack [_ _]
          (swap! game update-curlvl-monster (in-direction old-pos dir)
                 assoc :peaceful true)
          nil))))
  (trigger [_]
    (str (or (vi-directions (enum->kw dir))
             (throw (IllegalArgumentException.
                      (str "Invalid direction: " dir)))))))

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
         (remove #(= origin-feature (:feature %)))
         (reduce #(update-curlvl-at %1 %2 assoc :branch-id branch) game))))

(defn stairs-handler [anbf]
  (let [old-game (-> anbf :game deref)
        old-branch (branch-key old-game)
        old-dlvl (:dlvl old-game)
        old-stairs (at-player old-game)]
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
    (reify DlvlChangeHandler
      (dlvl-changed [this old-dlvl new-dlvl]
        (swap! (:game anbf)
               #(let [new-branch (get old-stairs :branch-id
                                      (initial-branch-id % new-dlvl))]
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

(defaction Look []
  (handler [_ {:keys [game] :as anbf}]
    (let [has-item (atom false)]
      (swap! game #(if-not (blind? (:player %))
                     (update-curlvl-at % (:player %)
                       assoc :seen true :new-items false)
                     %))
      (update-on-known-position anbf
        (fn after-look [game]
          (as-> game res
            (if (->> (:player res) (at-curlvl res) :feature nil?)
              (update-curlvl-at res (:player res) assoc :feature :floor)
              res) ; got no topline message suggesting a special feature
            (if-not @has-item
              (update-curlvl-at res (:player res) assoc :items [])
              res))))
      ; XXX note: items on tile HAVE to be determined via this command only, topline messages on Move are not reliable due to teletraps
      (reify
        MultilineMessageHandler
        (message-lines [_ lines]
          (condp re-seq (nth lines 0)
            #"^(?:Things that (?:are|you feel) here:|You (?:see|feel))"
            (let [items (mapv item (subvec lines 1))
                  top-item (nth items 0)]
              (log/debug "Items here:" (log/spy items))
              (reset! has-item true)
              (swap! game #(update-curlvl-at % (:player %)
                             assoc :items items
                                   :item-glyph (:glyph (item-id % top-item))
                                   :item-color nil)))
            (log/error "Unrecognized message list " (str lines))))
        ToplineMessageHandler
        (message [_ text]
          (when-not (and (= text "But you can't reach it!")
                         (reset! has-item true))
            (when-let [item (some->> text
                                     (re-first-group
                                       #"You (?:see|feel) here ([^.]+).")
                                     item)]
              (log/debug "Single item here:" item)
              (reset! has-item true)
              (swap! game #(update-curlvl-at % (:player %)
                             assoc :items [item]
                                   :item-glyph (:glyph (item-id % item))
                                   :item-color nil)))
            (if-let [feature (feature-here text (:rogue (curlvl-tags @game)))]
              (swap! game #(update-curlvl-at % (:player %)
                                             assoc :feature feature))
              (if (= :trap (:feature (at-player @game)))
                (swap! game #(update-curlvl-at % (:player %)
                                               assoc :feature :floor)))))))))
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
            (when-let [desc (and (monster? (nth text 0))
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

(defaction Open [dir]
  (handler [_ {:keys [game] :as anbf}]
    (reify ToplineMessageHandler
      (message [_ text]
        (let [door (in-direction (:player @game) dir)]
          (case text
            "This door is locked." (swap! game update-curlvl-at door
                                          assoc :feature :door-locked)
            "This door is already open." (swap! game update-curlvl-at door
                                                assoc :feature :door-open)
            "The door opens." (swap! game update-curlvl-at door
                                     assoc :feature :door-open)
            "This doorway has no door." (swap! game update-curlvl-at door
                                               assoc :feature nil)
            "You see no door there." (swap! game update-curlvl-at door
                                            assoc :feature nil)
            nil)))))
  (trigger [_] (str \o (vi-directions (enum->kw dir)))))

(defaction Inventory []
  (handler [_ {:keys [game] :as anbf}]
    (swap! game update-in [:player] dissoc :thick)) ; TODO only on changes
  (trigger [_] "i"))

(defn- examine-tile [{:keys [player] :as game}]
  (let [tile (at-player game)]
    (when (and (not (blind? player))
               (or (not (:feature tile))
                   (= :trap (:feature tile))
                   (:new-items tile)))
      (log/debug "examining tile")
      (->Look))))

(defn- examine-traps [game]
  (some->> (curlvl game) :tiles (apply concat)
           (find-first #(and (= :trap (:feature %))
                             (not (item? %))
                             (not (monster? (:glyph %) (:color %)))))
           (->FarLook)))

(defn- examine-monsters [{:keys [player] :as game}]
  (when-not (:hallu (:state player))
    (when-let [m (->> (curlvl-monsters game) vals
                      (remove #(or (:remembered %)
                                   (:friendly %)
                                   (some? (:peaceful %))
                                   (#{\I \1 \2 \3 \4 \5} (:glyph %))))
                      first)]
      (log/debug "examining monster" m)
      (->FarLook m))))

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
      (->Inventory))))

(def ^:private inventory-handler (memoize inventory-handler))

(defn update-inventory
  "Re-check inventory as soon as possible."
  [anbf]
  (register-handler anbf priority-top (inventory-handler anbf)))

(defn with-handler
  ([handler action]
   (with-handler action priority-default handler))
  ([priority handler action]
   (update-in action [:handlers] conj [priority handler])))

(defn- -withHandler
  ([action handler]
   (-withHandler action priority-default handler))
  ([action priority handler]
   (with-handler action priority handler)))

; factory functions for Java bots ; TODO the rest
(gen-class
  :name anbf.bot.Actions
  :methods [^:static [Move [anbf.bot.Direction] anbf.bot.IAction]
            ^:static [Pray [] anbf.bot.IAction]
            ^:static [withHandler [anbf.bot.IAction Object] anbf.bot.IAction]
            ^:static [withHandler [anbf.bot.IAction int Object] anbf.bot.IAction]])
