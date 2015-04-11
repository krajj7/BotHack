(ns bothack.delegator
  "The delegator delegates event and prompt invocations to all registered
  handlers which implement the protocol for the given event type, or the first
  handler that implements the prompt protocol.  For prompts it writes responses
  back to the terminal.  Handlers are invoked in order of their priority, for
  handlers of the same priority order of invocation is not specified. "
  (:require [clojure.data.priority-map :refer [priority-map]]
            [clojure.pprint :refer [pprint]]
            [bothack.action :refer :all]
            [bothack.position :refer :all]
            [bothack.util :refer :all]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defprotocol NetHackWriter
  (write [this cmd] "Write a string to the NetHack terminal as if typed."))

(defrecord Delegator [writer handlers inhibited]
  NetHackWriter
  (write [this cmd]
    (when-not (:inhibited this)
      (log/debug "writing to terminal:" (with-out-str (pprint ^String cmd)))
      ((:writer this) cmd))
    this))

(defn new-delegator [writer]
  (Delegator. writer (priority-map) false))

(defn set-inhibition
  "When inhibited the delegator keeps delegating events but doesn't delegate
  any prompts or writes."
  [delegator state]
  (assoc delegator :inhibited state))

(defn register
  "Register an event/prompt handler."
  ([delegator handler]
   (register delegator priority-default handler))
  ([delegator priority handler]
   (update delegator :handlers assoc handler priority)))

(defn deregister
  "Deregister a handler from the delegator."
  [delegator handler]
  (update delegator :handlers dissoc handler))

(defn switch
  "Replace a prompt handler with another, keep the priority."
  [delegator handler-old handler-new]
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

(defn- invoke-prompt
  [protocol method delegator & args]
  (loop [[handler & more-handlers] (keys (:handlers delegator))]
    ;(log/debug "invoking next prompt handler" handler)
    (if-some [res (apply invoke-handler protocol method handler args)]
      res
      (if (seq more-handlers)
        (recur more-handlers)
        (throw (IllegalStateException.
                 (str "No handler responded to prompt of "
                      (:on-interface protocol))))))))

(defn- enter-position [s] (str (to-position s) \.))

(defn- newline-terminate [s]
  (let [t (str s)]
    (if (.endsWith t "\n") t (str t \newline))))

(declare response-chosen)

(defn- respond-escapable [res-transform protocol method delegator & args]
  (if-not (:inhibited delegator)
    (let [res (apply invoke-prompt protocol method delegator args)]
      (log/debug "prompt response:" res)
      (if-not (and (string? res) (empty? res)) ; can return "\n" to send empty response
        (do (response-chosen delegator method res)
            (write delegator (res-transform res)))
        (do (log/info "Escaping prompt")
            (write delegator esc))))))

(defn- yesno [s] (if s "y" "n"))

(defn- direction [s] (get vi-directions (enum->kw s) s))

(defn- respond-menu [options]
  (if (or (coll? options) (instance? java.util.Collection options))
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
  [kind return invoke-fn protocol & proto-methods]
  `(do (defprotocol ~protocol ~@proto-methods)
       ~(if (= :internal kind)
          ; :internal = interface will be auto-generated from clojure (no javadoc, no parametrized types)
          ; anything other than :internal needs to have an interface pre-defined in the java sources (bothack.events or bothack.prompts package)
          `(defnsinterface ~(symbol (str \I protocol)) "bothack.internal"
             ~@(map (partial interface-sig return) proto-methods)))
       (extend-type ~(symbol (str "bothack."
                                  (cond (= :internal kind) "internal"
                                        (some? return) "prompts"
                                        :else "events")
                                  ".I" protocol))
         ~protocol ~@(map interface-call proto-methods))
       (extend-type Delegator ~protocol
         ~@(map (partial delegation-impl invoke-fn protocol) proto-methods))))

(defmacro ^:private defeventhandler [kind protocol & proto-methods]
  `(defprotocol-delegated ~kind nil invoke-event ~protocol ~@proto-methods))

; event protocols:

(defeventhandler :internal ConnectionStatusHandler
  (online [handler])
  (offline [handler]))

(defeventhandler :public RedrawHandler
  (redraw [handler ^bothack.bot.IFrame frame]))

; called when the frame on screen is complete - the cursor is on the player, the map and status lines are completely drawn and NetHack is waiting for input.
(defeventhandler :public FullFrameHandler
  (full-frame [handler ^bothack.bot.IFrame frame]))

; called when the cursor is on the player – besides full frames this also occurs on location prompts
(defeventhandler :public KnowPositionHandler
  (know-position [handler ^bothack.bot.IFrame frame]))

(defeventhandler :public GameStateHandler
  (started [handler])
  (ended [handler]))

(defeventhandler :public ToplineMessageHandler
  (message [handler ^String text]))

(defeventhandler :internal BOTLHandler
  (botl [handler ^clojure.lang.IPersistentMap status]))

(defeventhandler :public DlvlChangeHandler
  (dlvl-changed [handler ^String old-dlvl ^String new-dlvl]))

(defeventhandler :public AboutToChooseActionHandler
  (about-to-choose [handler ^bothack.bot.IGame gamestate]))

(defeventhandler :public ActionChosenHandler
  (action-chosen [handler ^bothack.actions.IAction action]))

(defeventhandler :internal PromptResponseHandler
  (response-chosen [handler method response]))

(defeventhandler :internal InventoryHandler
  (inventory-list [handler ^clojure.lang.IPersistentMap inventory]))

(defeventhandler :public MultilineMessageHandler
  (message-lines [handler ^clojure.lang.IPersistentList lines]))

(defeventhandler :public FoundItemsHandler
  (found-items [handler ^clojure.lang.IPersistentList items]))

; prompt protocols:

(defmacro ^:private defchoicehandler [kind protocol & proto-methods]
  `(defprotocol-delegated ~kind String (partial respond-escapable str)
     ~protocol ~@proto-methods))

(defmacro ^:private defyesnohandler [kind protocol & proto-methods]
  `(defprotocol-delegated ~kind Boolean (partial respond-escapable yesno)
     ~protocol ~@proto-methods))

(defmacro ^:private deflocationhandler [kind protocol & proto-methods]
  `(defprotocol-delegated ~kind bothack.bot.IPosition (partial respond-escapable
                                                      enter-position)
     ~protocol ~@proto-methods))

(defmacro ^:private defdirhandler [kind protocol & proto-methods]
  `(defprotocol-delegated ~kind String (partial respond-escapable direction)
     ~protocol ~@proto-methods))

(deflocationhandler :public TeleportWhereHandler
  (teleport-where [handler]))

(deflocationhandler :internal AutotravelHandler
  (travel-where [handler]))

(deflocationhandler :internal PayWhomHandler
  (pay-whom [handler]))

; only valks supported
(defchoicehandler :internal ChooseCharacterHandler
  (choose-character [handler]))

(defdirhandler :public DirectionHandler
  (what-direction [handler prompt]))

(defyesnohandler :public ReallyAttackHandler
  (really-attack [handler ^String what]))

(defyesnohandler :public SeducedHandler
  (seduced-puton [handler ^String text])
  (seduced-remove [handler ^String text]))

(defchoicehandler :internal ApplyItemHandler
  (apply-what [handler ^String prompt]))

(defchoicehandler :internal WieldItemHandler
  (wield-what [handler ^String text]))

(defchoicehandler :internal WearItemHandler
  (wear-what [handler ^String text]))

(defchoicehandler :internal TakeOffItemHandler
  (take-off-what [handler ^String text]))

(defchoicehandler :internal PutOnItemHandler
  (put-on-what [handler ^String text]))

(defchoicehandler :internal RemoveItemHandler
  (remove-what [handler ^String text]))

(defchoicehandler :internal DropSingleHandler
  (drop-single [handler ^String text]))

(defchoicehandler :internal QuiverHandler
  (ready-what [handler ^String text]))

(defyesnohandler :internal EnterGehennomHandler
  (enter-gehennom [handler ^String text]))

(defyesnohandler :internal ForceGodHandler ; wizmode
  (force-god [handler ^String text]))

(defyesnohandler :internal DieHandler ; wizmode
  (die [handler ^String text]))

(defchoicehandler :internal DumpCoreHandler ; wizmode
  (dump-core [handler ^String text]))

(defyesnohandler :internal KeepSaveHandler ; wizmode
  (keep-save [handler ^String text]))

(defyesnohandler :internal DryFountainHandler ; wizmode
  (dry-fountain [handler ^String text]))

(defyesnohandler :internal LockHandler
  (lock-it [handler ^String text])
  (unlock-it [handler ^String text]))

(defyesnohandler :internal ForceLockHandler
  (force-lock [handler ^String text]))

(defyesnohandler :public PayDamageHandler
  (pay-damage [handler ^String text]))

(defn- respond-action [protocol method delegator & args]
  (if-not (:inhibited delegator)
    (let [action (apply invoke-prompt protocol method delegator args)]
      (action-chosen delegator action)
      (->> action trigger (write delegator)))))

(defmacro ^:private defactionhandler [kind protocol & proto-methods]
  `(defprotocol-delegated ~kind bothack.actions.IAction respond-action
     ~protocol ~@proto-methods))

(defactionhandler :public ActionHandler
  (choose-action [handler ^bothack.bot.IGame gamestate]))

(defmacro ^:private defprompthandler [kind protocol & proto-methods]
  `(defprotocol-delegated ~kind String
     (partial respond-escapable newline-terminate)
     ~protocol ~@proto-methods))

(defmacro ^:private defmenuhandler [kind protocol & proto-methods]
  `(defprotocol-delegated ~kind Object
     (partial respond-escapable respond-menu)
     ~protocol ~@proto-methods))

(defmenuhandler :internal PickupHandler
  (pick-up-what [handler ^clojure.lang.IPersistentMap options]))

(defmenuhandler :internal NameMenuHandler
  (name-menu [handler ^clojure.lang.IPersistentMap options]))

(defchoicehandler :internal NameWhatHandler
  (name-what [handler ^String prompt]))

(defprompthandler :internal WhatNameHandler
  (what-name [handler ^String prompt]))

(defprompthandler :public OfferHandler
  (offer-how-much [handler ^String prompt]))

(defprompthandler :public LevelTeleportHandler
  (leveltele [handler ^String prompt]))

(defchoicehandler :public WhichRingFingerHandler
  (which-finger [handler ^String prompt]))

(defchoicehandler :internal ReadWhatHandler
  (read-what [handler ^String prompt]))

(defchoicehandler :internal DrinkWhatHandler
  (drink-what [handler ^String prompt]))

(defyesnohandler :internal DrinkHereHandler
  (drink-here [handler ^String prompt]))

(defchoicehandler :internal ZapWhatHandler
  (zap-what [handler ^String prompt]))

(defyesnohandler :internal EatItHandler
  (eat-it [handler ^String what]))

(defyesnohandler :internal SacrificeItHandler
  (sacrifice-it [handler ^String what]))

(defyesnohandler :internal AttachCandlesHandler
  (attach-candelabrum-candles [handler ^String prompt]))

(defyesnohandler :internal StillClimbHandler
  (still-climb [handler ^String prompt]))

(defchoicehandler :internal EatWhatHandler
  (eat-what [handler ^String prompt]))

(defchoicehandler :internal SacrificeWhatHandler
  (sacrifice-what [handler ^String prompt]))

(defchoicehandler :internal DipHandler
  (dip-what [handler ^String prompt])
  (dip-into-what [handler ^String prompt]))

(defyesnohandler :internal DipHereHandler
  (dip-here [handler ^String prompt]))

(defyesnohandler :internal LiftBurdenHandler
  (lift-burden [handler ^clojure.lang.Keyword burden ^String item-label]))

(defyesnohandler :internal LootItHandler
  (loot-it [handler ^String what]))

(defyesnohandler :internal PutSomethingInHandler
  (put-something-in [handler ^String prompt]))

(defyesnohandler :internal TakeSomethingOutHandler
  (take-something-out [handler ^String prompt]))

(defyesnohandler :public StopEatingHandler
  (stop-eating [handler ^String prompt]))

(defmenuhandler :internal TakeOutWhatHandler
  (take-out-what [handler ^clojure.lang.IPersistentMap options]))

(defmenuhandler :internal PutInWhatHandler
  (put-in-what [handler ^clojure.lang.IPersistentMap options]))

(defmenuhandler :internal LootWhatHandler
  (loot-what [handler ^clojure.lang.IPersistentMap options]))

(defchoicehandler :internal ThrowWhatHandler
  (throw-what [handler ^String prompt]))

(defprompthandler :internal EngraveWhatHandler
  (write-what [handler ^String prompt]))

(defchoicehandler :internal EngraveWithWhatHandler
  (write-with-what [handler ^String prompt]))

(defyesnohandler :internal EngraveAppendHandler
  (append-engraving [handler ^String prompt]))

(defyesnohandler :public SellItHandler
  (sell-it [handler ^Integer offer ^String what]))

(defyesnohandler :internal WizmodeEnhanceHandler ; wizmode
  (enhance-without-practice [handler ^String prompt]))

(defprompthandler :internal CreateWhatMonsterHandler ; wizmode
  (create-what-monster [handler ^String text]))

(defyesnohandler :public DoTeleportHandler
  (do-teleport [handler ^String prompt]))

(defmenuhandler :public EnhanceWhatHandler
  (enhance-what [handler ^clojure.lang.IPersistentMap options]))

(defmenuhandler :internal CurrentSkillsHandler
  (current-skills [handler ^clojure.lang.IPersistentMap options]))

(defprompthandler :public MakeWishHandler
  (make-wish [handler ^String prompt]))

(defprompthandler :public GenocideHandler
  (genocide-class [handler ^String prompt])
  (genocide-monster [handler ^String prompt]))

(defprompthandler :public VaultGuardHandler
  (who-are-you [handler ^String prompt]))

(defmenuhandler :public IdentifyWhatHandler
  (identify-what [handler ^clojure.lang.IPersistentMap options]))

(defchoicehandler :internal RubWhatHandler
  (rub-what [handler ^String prompt]))

(defchoicehandler :public ChargeWhatHandler
  (charge-what [handler ^String prompt]))
