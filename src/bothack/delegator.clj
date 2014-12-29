(ns bothack.delegator
  "The delegator delegates event and command invocations to all registered handlers which implement the protocol for the given event type, or the first handler that implements the command protocol.  For commands it writes responses back to the terminal.  Handlers are invoked in order of their priority, for handlers of the same priority order of invocation is not specified. "
  (:require [clojure.data.priority-map :refer [priority-map]]
            [clojure.pprint :refer [pprint]]
            [bothack.action :refer :all]
            [bothack.util :refer :all]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defprotocol NetHackWriter
  (write [this cmd] "Write a string to the NetHack terminal as if typed."))

(defrecord Delegator [writer handlers inhibited]
  NetHackWriter
  (write [this cmd] "Write a string to the NetHack terminal as if typed."
    (when-not (:inhibited this)
      (log/debug "writing to terminal:" (with-out-str (pprint cmd)))
      ((:writer this) cmd))
    this))

(defn new-delegator [writer]
  (Delegator. writer (priority-map) false))

(defn set-inhibition
  "When inhibited the delegator keeps delegating events but doesn't delegate any commands or writes."
  [delegator state]
  (assoc delegator :inhibited state))

(defn register
  "Register an event/command handler."
  ([delegator handler]
   (register delegator priority-default handler))
  ([delegator priority handler]
   (update delegator :handlers assoc handler priority)))

(defn deregister [delegator handler]
  "Deregister a handler from the delegator."
  (update delegator :handlers dissoc handler))

(defn switch [delegator handler-old handler-new]
  "Replace a command handler with another, keep the priority."
  (if-let [priority (get (:handlers delegator) handler-old)]
    (-> delegator
        (deregister handler-old)
        (register priority handler-new))
    (throw (IllegalArgumentException. "Handler to switch not present"))))

(defn set-writer [delegator writer]
  (assoc delegator :writer writer))

(defn- invoke-handler [protocol method handler & args]
  ;(log/debug "testing handler" handler " for " protocol)
  (if (satisfies? protocol handler)
    (try
      ;(log/debug "invoking handler" handler)
      (apply method handler args)
      (catch Exception e
        (log/error e "Delegator caught handler exception")))))

(defn- invoke-event
  [protocol method delegator & args]
  (doseq [[h _] (:handlers delegator)]
    (apply invoke-handler protocol method h args)))

(defn- invoke-command
  [protocol method delegator & args]
  (loop [[handler & more-handlers] (keys (:handlers delegator))]
    ;(log/debug "invoking next command handler" handler)
    (if-some [res (apply invoke-handler protocol method handler args)]
      res
      (if (seq more-handlers)
        (recur more-handlers)
        (throw (IllegalStateException.
                 (str "No handler responded to command of "
                      (:on-interface protocol))))))))

(defn- position-map [s]
  (if (instance? bothack.bot.IPosition s)
    {:x (.x ^bothack.bot.IPosition s) :y (.y ^bothack.bot.IPosition s)}
    s))

(defn- enter-position [s] (str (to-position (position-map s)) \.))

(defn- newline-terminate [s]
  (let [t (str s)]
    (if (.endsWith t "\n") t (str t \newline))))

(declare response-chosen)

(defn- respond-escapable [res-transform protocol method delegator & args]
  (if-not (:inhibited delegator)
    (let [res (apply invoke-command protocol method delegator args)]
      (log/debug "command response:" res)
      (if-not (and (string? res) (empty? res)) ; can return "\n" to send empty response
        (do (response-chosen delegator method res)
            (write delegator (res-transform res)))
        (do (log/info "Escaping prompt")
            (write delegator esc))))))

(defn- yesno [s] (if s "y" "n"))

(defn- direction [s] (get vi-directions (enum->kw s) s))

(defn- respond-menu [options]
  (if (coll? options)
    (string/join options)
    (str options)))

(defn- delegation-impl [invoke-fn protocol [method [delegator & args]]]
  `(~method [~delegator ~@args]
            (~invoke-fn ~protocol ~method ~delegator ~@args)
            ~delegator))

(defn- declojurify
  "my-great-method => myGreatMethod"
  [sym]
  (as-> (string/split (str sym) #"-") res
    (->> (rest res)
         (map #(->> % first Character/toUpperCase (conj (rest %)) string/join))
         (apply str (first res)))
    (symbol res)
    (with-meta res (meta sym))))

(defn- interface-sig [return [method [_ & args]]]
  `(~(with-meta (symbol (declojurify method))
                {:tag (or return 'void)}) [~@args]))

(defn- interface-call [[method [this & args]]]
  `(~method [~this ~@args] (. ~this ~(declojurify method) ~@args)))

; rebinding *ns* for definterface didn't seem to work...
(defmacro ^:private defnsinterface
  [iname ins & sigs]
  (let [tag (fn [x] (or (:tag (meta x)) Object))
        psig (fn [[iname [& args]]]
               (vector iname (vec (map tag args)) (tag iname) (map meta args)))
        cname (with-meta (symbol (str ins "." iname)) (meta iname))]
    `(let []
       (gen-interface :name ~cname :methods ~(vec (map psig sigs)))
       (import ~cname))))

(defmacro ^:private defprotocol-delegated
  [return invoke-fn protocol & proto-methods]
  `(do (defprotocol ~protocol ~@proto-methods)
       (defnsinterface ~(symbol (str \I protocol)) "bothack.delegator"
         ~@(map (partial interface-sig return) proto-methods))
       (extend-type ~(symbol (str "bothack.delegator.I" protocol))
         ~protocol ~@(map interface-call proto-methods))
       (extend-type Delegator ~protocol
         ~@(map (partial delegation-impl invoke-fn protocol) proto-methods))))

(defmacro ^:private defeventhandler [protocol & proto-methods]
  `(defprotocol-delegated nil invoke-event ~protocol ~@proto-methods))

; event protocols:

(defeventhandler ConnectionStatusHandler
  (online [handler])
  (offline [handler]))

(defeventhandler RedrawHandler
  (redraw [handler ^bothack.bot.IFrame frame]))

; called when the frame on screen is complete - the cursor is on the player, the map and status lines are completely drawn and NetHack is waiting for input.
(defeventhandler FullFrameHandler
  (full-frame [handler ^bothack.bot.IFrame frame]))

; called when the cursor is on the player – besides full frames this also occurs on location prompts
(defeventhandler KnowPositionHandler
  (know-position [handler ^bothack.bot.IFrame frame]))

(defeventhandler GameStateHandler
  (started [handler])
  (ended [handler]))

(defeventhandler ToplineMessageHandler
  (message [handler ^String text]))

(defeventhandler BOTLHandler
  (botl [handler ^clojure.lang.IPersistentMap status]))

(defeventhandler DlvlChangeHandler
  (dlvl-changed [handler ^String old-dlvl ^String new-dlvl]))

(defeventhandler AboutToChooseActionHandler
  (about-to-choose [handler ^bothack.bot.IGame gamestate]))

(defeventhandler ActionChosenHandler
  (action-chosen [handler ^bothack.bot.IAction action]))

(defeventhandler CommandResponseHandler
  (response-chosen [handler method response]))

(defeventhandler InventoryHandler
  (inventory-list [handler ^clojure.lang.IPersistentMap inventory]))

(defeventhandler MultilineMessageHandler
  (message-lines [handler ^clojure.lang.IPersistentList items]))

(defeventhandler FoundItemsHandler
  (found-items [handler ^clojure.lang.IPersistentList items]))

; command protocols:

(defmacro ^:private defchoicehandler [protocol & proto-methods]
  `(defprotocol-delegated String (partial respond-escapable str)
     ~protocol ~@proto-methods))

(defmacro ^:private defyesnohandler [protocol & proto-methods]
  `(defprotocol-delegated Boolean (partial respond-escapable yesno)
     ~protocol ~@proto-methods))

(defmacro ^:private deflocationhandler [protocol & proto-methods]
  `(defprotocol-delegated bothack.bot.IPosition (partial respond-escapable
                                                      enter-position)
     ~protocol ~@proto-methods))

(defmacro ^:private defdirhandler [protocol & proto-methods]
  `(defprotocol-delegated String (partial respond-escapable direction)
     ~protocol ~@proto-methods))

(deflocationhandler TeleportWhereHandler
  (teleport-where [handler]))

(deflocationhandler AutotravelHandler
  (travel-where [handler]))

(deflocationhandler PayWhomHandler
  (pay-whom [handler]))

(defchoicehandler ChooseCharacterHandler
  (choose-character [handler]))

(defdirhandler DirectionHandler
  (what-direction [handler prompt]))

(defyesnohandler ReallyAttackHandler
  (really-attack [handler ^String what]))

(defyesnohandler SeducedEquipRemoveHandler
  (seduced-remove [handler ^String text]))

(defyesnohandler SeducedPutOnHandler
  (seduced-puton [handler ^String text]))

(defchoicehandler ApplyItemHandler
  (apply-what [handler ^String prompt]))

(defchoicehandler WieldItemHandler
  (wield-what [handler ^String text]))

(defchoicehandler WearItemHandler
  (wear-what [handler ^String text]))

(defchoicehandler TakeOffItemHandler
  (take-off-what [handler ^String text]))

(defchoicehandler PutOnItemHandler
  (put-on-what [handler ^String text]))

(defchoicehandler RemoveItemHandler
  (remove-what [handler ^String text]))

(defchoicehandler DropSingleHandler
  (drop-single [handler ^String text]))

(defchoicehandler QuiverHandler
  (ready-what [handler ^String text]))

(defyesnohandler EnterGehennomHandler
  (enter-gehennom [handler ^String text]))

(defyesnohandler ForceGodHandler ; wizmode
  (force-god [handler ^String text]))

(defyesnohandler DieHandler ; wizmode
  (die [handler ^String text]))

(defchoicehandler DumpCoreHandler ; wizmode
  (dump-core [handler ^String text]))

(defchoicehandler CreateWhatMonsterHandler ; wizmode
  (create-what-monster [handler ^String text]))

(defyesnohandler KeepSaveHandler ; wizmode
  (keep-save [handler ^String text]))

(defyesnohandler DryFountainHandler ; wizmode
  (dry-fountain [handler ^String text]))

(defyesnohandler LockHandler
  (lock-it [handler ^String text])
  (unlock-it [handler ^String text]))

(defyesnohandler ForceLockHandler
  (force-lock [handler ^String text]))

(defyesnohandler PayDamageHandler
  (pay-damage [handler ^String text]))

(defn- respond-action [protocol method delegator & args]
  (if-not (:inhibited delegator)
    (let [action (apply invoke-command protocol method delegator args)]
      (action-chosen delegator action)
      (->> action trigger (write delegator)))))

(defmacro ^:private defactionhandler [protocol & proto-methods]
  `(defprotocol-delegated bothack.bot.IAction respond-action
     ~protocol ~@proto-methods))

(defactionhandler ActionHandler
  (choose-action [handler ^bothack.bot.IGame gamestate]))

(defmacro ^:private defprompthandler [protocol & proto-methods]
  `(defprotocol-delegated String (partial respond-escapable newline-terminate)
     ~protocol ~@proto-methods))

(defmacro ^:private defmenuhandler [protocol & proto-methods]
  `(defprotocol-delegated clojure.lang.IPersistentSet
     (partial respond-escapable respond-menu)
     ~protocol ~@proto-methods))

(defmenuhandler PickupHandler
  (pick-up-what [handler ^clojure.lang.IPersistentMap options]))

(defmenuhandler NameMenuHandler
  (name-menu [handler ^clojure.lang.IPersistentMap options]))

(defchoicehandler NameWhatHandler
  (name-what [handler ^String prompt]))

(defprompthandler WhatNameHandler
  (what-name [handler ^String prompt]))

(defchoicehandler CallWhatHandler
  (call-what [handler ^String prompt]))

(defprompthandler CallWhatNameHandler
  (call-what-name [handler ^String prompt]))

(defprompthandler OfferHandler
  (offer-how-much [handler ^String prompt]))

(defprompthandler LevelTeleportHandler
  (leveltele [handler ^String prompt]))

(defchoicehandler WhichRingFingerHandler
  (which-finger [handler ^String prompt]))

(defchoicehandler ReadWhatHandler
  (read-what [handler ^String prompt]))

(defchoicehandler DrinkWhatHandler
  (drink-what [handler ^String prompt]))

(defyesnohandler DrinkHereHandler
  (drink-here [handler ^String prompt]))

(defchoicehandler ZapWhatHandler
  (zap-what [handler ^String prompt]))

(defyesnohandler EatItHandler
  (eat-it [handler ^String what]))

(defyesnohandler SacrificeItHandler
  (sacrifice-it [handler ^String what]))

(defyesnohandler AttachCandlesHandler
  (attach-candelabrum-candles [handler ^String prompt]))

(defyesnohandler StillClimbHandler
  (still-climb [handler ^String prompt]))

(defchoicehandler EatWhatHandler
  (eat-what [handler ^String prompt]))

(defchoicehandler SacrificeWhatHandler
  (sacrifice-what [handler ^String prompt]))

(defchoicehandler DipHandler
  (dip-what [handler ^String prompt])
  (dip-into-what [handler ^String prompt]))

(defyesnohandler DipHereHandler
  (dip-here [handler ^String prompt]))

(defyesnohandler LiftBurdenHandler
  (lift-burden [handler ^clojure.lang.Keyword burden ^String item-label]))

(defyesnohandler LootItHandler
  (loot-it [handler ^String what]))

(defyesnohandler PutSomethingInHandler
  (put-something-in [handler ^String prompt]))

(defyesnohandler TakeSomethingOutHandler
  (take-something-out [handler ^String prompt]))

(defyesnohandler StopEatingHandler
  (stop-eating [handler ^String prompt]))

(defmenuhandler TakeOutWhatHandler
  (take-out-what [handler ^clojure.lang.IPersistentMap options]))

(defmenuhandler PutInWhatHandler
  (put-in-what [handler ^clojure.lang.IPersistentMap options]))

(defmenuhandler LootWhatHandler
  (loot-what [handler ^clojure.lang.IPersistentMap options]))

(defchoicehandler ThrowWhatHandler
  (throw-what [handler ^String prompt]))

(defprompthandler EngraveWhatHandler
  (write-what [handler ^String prompt]))

(defchoicehandler EngraveWithWhatHandler
  (write-with-what [handler ^String prompt]))

(defyesnohandler EngraveAppendHandler
  (append-engraving [handler ^String prompt]))

(defyesnohandler SellItHandler
  (sell-it [handler ^Integer offer ^String what]))

(defyesnohandler WizmodeEnhanceHandler
  (enhance-without-practice [handler ^String prompt]))

(defyesnohandler DoTeleportHandler
  (do-teleport [handler ^String prompt]))

(defmenuhandler EnhanceWhatHandler
  (enhance-what [handler ^clojure.lang.IPersistentMap options]))

(defmenuhandler CurrentSkillsHandler
  (current-skills [handler ^clojure.lang.IPersistentMap options]))

(defprompthandler MakeWishHandler
  (make-wish [handler ^String prompt]))

(defprompthandler GenocideHandler
  (genocide-class [handler ^String prompt])
  (genocide-monster [handler ^String prompt]))

(defprompthandler VaultGuardHandler
  (who-are-you [handler ^String prompt]))

(defmenuhandler IdentifyWhatHandler
  (identify-what [handler ^clojure.lang.IPersistentMap options]))

(defchoicehandler RubWhatHandler
  (rub-what [handler ^String prompt]))

(defchoicehandler ChargeWhatHandler
  (charge-what [handler ^String prompt]))
