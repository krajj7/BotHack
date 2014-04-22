; The delegator delegates event and command invocations to all registered handlers which implement the protocol for the given event/command type.  For commands it writes responses back to the terminal.

(ns anbf.delegator
  (:require [flatland.ordered.set :refer [ordered-set]]
            [anbf.action :refer :all]
            [clojure.tools.logging :as log]))

(defprotocol NetHackWriter
  (write [this cmd] "Write a string to the NetHack terminal as if typed."))

(defrecord Delegator [writer handlers-sys handlers-usr inhibited]
  NetHackWriter
  (write [this cmd]
    (if-not (:inhibited this)
      ((:writer this) cmd))
    this))

(defn new-delegator [writer]
  (Delegator. writer (ordered-set) (ordered-set) false))

(defn set-inhibition
  "When inhibited the delegator keeps delegating events but doesn't delegate any commands or writes."
  [delegator state]
  (assoc-in delegator [:inhibited] state))

(defn register-sys [delegator handler]
  (update-in delegator [:handlers-sys] conj handler))

(defn register-usr [delegator handler]
  (update-in delegator [:handlers-usr] conj handler))

(defn deregister [delegator handler]
  (-> delegator
      (update-in [:handlers-usr] disj handler)
      (update-in [:handlers-sys] disj handler)))

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
  "Events are propagated first to system handlers, then to user handlers satisfying the protocol, in both cases in the order of their registration."
  [protocol method delegator & args]
  (doall (map #(apply invoke-handler protocol method % args)
              (concat (:handlers-sys delegator) (:handlers-usr delegator)))))

(defn- invoke-command
  "Commands are propagated first to all system handlers, then to user handlers each in reverse order of registration.  Propagation stops on the first handler that satisfies the protocol and returns a truthy value"
  [protocol method delegator & args]
  (loop [[handler & more-handlers] (concat (rseq (:handlers-sys delegator))
                                           (rseq (:handlers-usr delegator)))]
    ;(log/debug "invoking next command handler" handler)
    (or (apply invoke-handler protocol method handler args)
        (if (seq more-handlers)
          (recur more-handlers)
          (throw (IllegalStateException.
                   (str "No handler responded to command of "
                        (:on-interface protocol))))))))

(defn- respond-prompt [protocol method delegator & args]
  (if-not (:inhibited delegator)
    (write delegator (apply invoke-command protocol method delegator args))))

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

(defmacro ^:private defprotocol-delegated
  [invoke-fn protocol & proto-methods]
  `(do (defprotocol ~protocol ~@proto-methods)
       ; TODO also definterface IFooHandler, extend protocol to the interface
       (extend-type Delegator ~protocol
         ~@(map (partial delegation-impl invoke-fn protocol) proto-methods))))

(defmacro ^:private defeventhandler [protocol & proto-methods]
  `(defprotocol-delegated invoke-event ~protocol ~@proto-methods))

(defmacro ^:private defprompthandler [protocol & proto-methods]
  `(defprotocol-delegated respond-prompt ~protocol ~@proto-methods))

(defmacro ^:private defactionhandler [protocol & proto-methods]
  `(defprotocol-delegated respond-action ~protocol ~@proto-methods))

; event protocols:

(defeventhandler ConnectionStatusHandler
  (online [handler])
  (offline [handler]))

(defeventhandler RedrawHandler
  (redraw [handler frame]))

; called when the frame on screen is complete - the cursor is on the player, the map and status lines are completely drawn and NetHack is waiting for input.
(defeventhandler FullFrameHandler
  (fullFrame [handler frame]))

(defeventhandler GameStateHandler
  (started [handler])
  (ended [handler]))

(defeventhandler ToplineMessageHandler
  (message [handler text]))

(defeventhandler BOTLHandler
  (botl [handler status]))

(defeventhandler MapHandler
  (mapDrawn [handler frame]))

; command protocols:

; defprompthandler (=> String)
; defactionhandler (=> Action)
; defmenuhandler (=> MenuOption?)
; deflocationhandler (=> x y)

(defprompthandler ChooseCharacterHandler
  (chooseCharacter [handler]))

(defactionhandler ActionHandler
  (chooseAction [handler gamestate]))
