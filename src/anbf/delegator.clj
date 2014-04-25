; The delegator delegates event and command invocations to all registered handlers which implement the protocol for the given event type, or the first handler that implements the command protocol.  For commands it writes responses back to the terminal.  Handlers are invoked in order of their priority, for handlers of the same priority order of invocation is not specified.

(ns anbf.delegator
  (:require [clojure.data.priority-map :refer [priority-map]]
            [anbf.action :refer :all]
            [anbf.util :refer :all]
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

(def priority-default 0)
; bots should not go beyond these (their interface specifies an int priority)
(def priority-top (dec Integer/MIN_VALUE))
(def priority-bottom (inc Integer/MAX_VALUE))

(defn register
  "Register an event/command handler."
  ([delegator handler]
   (register delegator priority-default handler))
  ([delegator priority handler]
   (update-in delegator [:handlers] assoc handler priority)))

(defn deregister [delegator handler]
  (update-in delegator [:handlers] dissoc handler))

(defn switch [delegator handler-old handler-new]
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
  (doall (map #(apply invoke-handler protocol method (first %) args)
              (:handlers delegator))))

(defn- invoke-command
  [protocol method delegator & args]
  (loop [[[handler _] & more-handlers] (seq (:handlers delegator))]
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

(defn- respond-action [protocol method delegator & args]
  ; TODO PerformedAction event a podle nej se obcas treba vymeni scraper?
  (when-not (:inhibited delegator)
    (log/info "<<< bot logic >>>")
    (as-> (apply invoke-command protocol method delegator args) action
      (apply perform action args)
      (write delegator action))))

(defn- delegation-impl [invoke-fn protocol [method [delegator & args]]]
  `(~method [~delegator ~@args]
            (~invoke-fn ~protocol ~method ~delegator ~@args)
            ~delegator))

(defn- interface-sig [[method [_ & args]]]
  `(~method [~@args]))

(defmacro ^:private defprotocol-delegated
  [invoke-fn protocol & proto-methods]
  `(do (defprotocol ~protocol ~@proto-methods)
       (definterface ~(symbol (str \I protocol))
         ~@(map interface-sig proto-methods))
       ; TODO extend protocol to the interface
       (extend-type Delegator ~protocol
         ~@(map (partial delegation-impl invoke-fn protocol) proto-methods))))

(defn- with-tags [tag specs]
  (map (fn [[method args]] (list (with-meta method {:tag tag}) args)) specs))

(defmacro ^:private defeventhandler [protocol & proto-methods]
  `(defprotocol-delegated invoke-event ~protocol
     ~@(with-tags 'void proto-methods)))

; TODO newline-terminated prompt handler, only write up to the first newline or add it
; TODO menu handler, location handler

(defmacro ^:private defchoicehandler [protocol & proto-methods]
  `(defprotocol-delegated respond-choice ~protocol
     ~@(with-tags String proto-methods)))

(defmacro ^:private defactionhandler [protocol & proto-methods]
  `(defprotocol-delegated respond-action ~protocol
     ~@(with-tags anbf.action.Action proto-methods)))

; event protocols:

(defeventhandler ConnectionStatusHandler
  (online [handler])
  (offline [handler]))

(defeventhandler RedrawHandler
  (redraw [handler ^anbf.bot.IFrame frame]))

; called when the frame on screen is complete - the cursor is on the player, the map and status lines are completely drawn and NetHack is waiting for input.
(defeventhandler FullFrameHandler
  (fullFrame [handler ^anbf.bot.IFrame frame]))

(defeventhandler GameStateHandler
  (started [handler])
  (ended [handler]))

(defeventhandler ToplineMessageHandler
  (message [handler ^String text]))

(defeventhandler BOTLHandler
  (botl [handler ^clojure.lang.IPersistentMap status]))

(defeventhandler MapHandler
  (mapDrawn [handler ^anbf.bot.IFrame frame]))

; command protocols:

(defchoicehandler ChooseCharacterHandler
  (chooseCharacter [handler]))

(defactionhandler ActionHandler
  (chooseAction [handler ^anbf.bot.IGame gamestate]))
