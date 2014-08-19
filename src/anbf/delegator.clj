(ns anbf.delegator
  "The delegator delegates event and command invocations to all registered handlers which implement the protocol for the given event type, or the first handler that implements the command protocol.  For commands it writes responses back to the terminal.  Handlers are invoked in order of their priority, for handlers of the same priority order of invocation is not specified. "
  (:require [clojure.data.priority-map :refer [priority-map]]
            [anbf.action :refer :all]
            [anbf.util :refer :all]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defprotocol NetHackWriter
  (write [this cmd] "Write a string to the NetHack terminal as if typed."))

(defrecord Delegator [writer handlers inhibited]
  NetHackWriter
  (write [this cmd] "Write a string to the NetHack terminal as if typed."
    (when-not (:inhibited this)
      (log/debug "writing" cmd)
      ((:writer this) cmd))
    this))

(defn new-delegator [writer]
  (Delegator. writer (priority-map) false))

(defn set-inhibition
  "When inhibited the delegator keeps delegating events but doesn't delegate any commands or writes."
  [delegator state]
  (assoc-in delegator [:inhibited] state))

(defn register
  "Register an event/command handler."
  ([delegator handler]
   (register delegator priority-default handler))
  ([delegator priority handler]
   (update-in delegator [:handlers] assoc handler priority)))

(defn deregister [delegator handler]
  "Deregister a handler from the delegator."
  (update-in delegator [:handlers] dissoc handler))

(defn switch [delegator handler-old handler-new]
  "Replace a command handler with another, keep the priority."
  (if-let [priority (get (:handlers delegator) handler-old)]
    (-> delegator
        (deregister handler-old)
        (register priority handler-new))
    (throw (IllegalArgumentException. "Handler to switch not present"))))

(defn set-writer [delegator writer]
  (assoc-in delegator [:writer] writer))

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
  (if (instance? anbf.bot.IPosition s)
    {:x (.x ^anbf.bot.IPosition s) :y (.y ^anbf.bot.IPosition s)}
    s))

(defn- enter-position [s] (str (to-position (position-map s)) \.))

(defn- newline-terminate [s] (if (.endsWith s "\n") s (str s \newline)))

(defn- respond-escapable [res-transform protocol method delegator & args]
  (if-not (:inhibited delegator)
    (let [res (apply invoke-command protocol method delegator args)]
      (if-not (and (string? res) (empty? res)) ; can return "\n" to send empty response
        (write delegator (res-transform res))
        (do (log/info "Escaping prompt")
            (write delegator esc))))))

(defn- yesno [s] (if s "y" "n"))

(defn- delegation-impl [invoke-fn protocol [method [delegator & args]]]
  `(~method [~delegator ~@args]
            (~invoke-fn ~protocol ~method ~delegator ~@args)
            ~delegator))

(defn- declojurify "my-great-method => myGreatMethod" [sym]
  (as-> (string/split (str sym) #"-") res
    (->> (rest res)
         (map #(apply str (->> % first Character/toUpperCase (conj (rest %)))))
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
       (defnsinterface ~(symbol (str \I protocol)) "anbf.bot"
         ~@(map (partial interface-sig return) proto-methods))
       (extend-type ~(symbol (str "anbf.bot.I" protocol))
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
  (redraw [handler ^anbf.bot.IFrame frame]))

; called when the frame on screen is complete - the cursor is on the player, the map and status lines are completely drawn and NetHack is waiting for input.
(defeventhandler FullFrameHandler
  (full-frame [handler ^anbf.bot.IFrame frame]))

; called when the cursor is on the player – besides full frames this also occurs on location prompts
(defeventhandler KnowPositionHandler
  (know-position [handler ^anbf.bot.IFrame frame]))

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
  (about-to-choose [handler ^anbf.bot.IGame gamestate]))

(defeventhandler ActionChosenHandler
  (action-chosen [handler ^anbf.bot.IAction action]))

(defeventhandler InventoryHandler
  (inventory-list [handler ^clojure.lang.IPersistentMap inventory]))

(defeventhandler MultilineMessageHandler
  (message-lines [handler ^clojure.lang.IPersistentList items]))

; command protocols:

(defmacro ^:private defchoicehandler [protocol & proto-methods]
  `(defprotocol-delegated String (partial respond-escapable identity)
     ~protocol ~@proto-methods))

(defmacro ^:private defyesnohandler [protocol & proto-methods]
  `(defprotocol-delegated Boolean (partial respond-escapable yesno)
     ~protocol ~@proto-methods))

(defmacro ^:private deflocationhandler [protocol & proto-methods]
  `(defprotocol-delegated anbf.bot.IPosition (partial respond-escapable
                                                      enter-position)
     ~protocol ~@proto-methods))

(deflocationhandler TeleportWhereHandler
  (teleport-where [handler]))

(defchoicehandler ChooseCharacterHandler
  (choose-character [handler]))

(defyesnohandler ReallyAttackHandler
  (really-attack [handler ^String text]))

(defyesnohandler SeducedEquipRemoveHandler
  (remove-equip [handler ^String text]))

(defyesnohandler ForceGodHandler ; wizmode
  (force-god [handler ^String text]))

(defyesnohandler DieHandler ; wizmode
  (die [handler ^String text]))

(defyesnohandler KeepSaveHandler ; wizmode
  (keep-save [handler ^String text]))

(defn- respond-action [protocol method delegator & args]
  (if-not (:inhibited delegator)
    (let [action (apply invoke-command protocol method delegator args)]
      (action-chosen delegator action)
      (->> action trigger (write delegator)))))

(defmacro ^:private defactionhandler [protocol & proto-methods]
  `(defprotocol-delegated anbf.bot.IAction respond-action
     ~protocol ~@proto-methods))

(defactionhandler ActionHandler
  (choose-action [handler ^anbf.bot.IGame gamestate]))

(defmacro ^:private defprompthandler [protocol & proto-methods]
  `(defprotocol-delegated String (partial respond-escapable newline-terminate)
     ~protocol ~@proto-methods))

(defprompthandler PromptHandler
  (call-object [handler ^String prompt]))
