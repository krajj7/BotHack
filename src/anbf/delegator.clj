; The delegator delegates event and command invocations to all registered handlers which implement the protocol for the given event type, or the first handler that implements the command protocol.  For commands it writes responses back to the terminal.  Handlers are invoked in order of their priority, for handlers of the same priority order of invocation is not specified.

(ns anbf.delegator
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
    (if-not (:inhibited this)
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
  (dorun (map #(apply invoke-handler protocol method (first %) args)
              (:handlers delegator))))

(defn- invoke-command
  [protocol method delegator & args]
  (loop [[handler & more-handlers] (keys (:handlers delegator))]
    ;(log/debug "invoking next command handler" handler)
    (or (apply invoke-handler protocol method handler args)
        (if (seq more-handlers)
          (recur more-handlers)
          (throw (IllegalStateException.
                   (str "No handler responded to command of "
                        (:on-interface protocol))))))))

(defn- respond-choice [protocol method delegator & args]
  (if-not (:inhibited delegator)
    (let [res (apply invoke-command protocol method delegator args)]
      (if (seq res)
        (write delegator res)
        (do (log/info "Escaping choice")
            (write delegator esc))))))

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

(defeventhandler GameStateHandler
  (started [handler])
  (ended [handler]))

(defeventhandler ToplineMessageHandler
  (message [handler ^String text]))

(defeventhandler BOTLHandler
  (botl [handler ^clojure.lang.IPersistentMap status]))

(defeventhandler MapHandler
  (map-drawn [handler ^anbf.bot.IFrame frame]))

(defeventhandler ActionChosenHandler
  (action-chosen [handler ^anbf.bot.IAction action]))

; command protocols:

; TODO newline-terminated prompt handler, only write up to the first newline or add it
; TODO menu handler, location handler

(defmacro ^:private defchoicehandler [protocol & proto-methods]
  `(defprotocol-delegated String respond-choice ~protocol
     ~@proto-methods))

(defchoicehandler ChooseCharacterHandler
  (choose-character [handler]))

(defn- respond-action [protocol method delegator & args]
  (if-not (:inhibited delegator)
    (let [action (apply invoke-command protocol method delegator args)]
      (action-chosen delegator action)
      (->> action trigger (write delegator)))))

(defmacro ^:private defactionhandler [protocol & proto-methods]
  `(defprotocol-delegated anbf.bot.IAction respond-action ~protocol
     ~@proto-methods))

(defactionhandler ActionHandler
  (choose-action [handler ^anbf.bot.IGame gamestate]))
