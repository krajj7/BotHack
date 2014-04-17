; The delegator delegates event and command invocations to all registered handlers which implement the protocol for the given event/command type.  For commands it writes responses back to the terminal.

(ns anbf.delegator
  (:require [flatland.ordered.set :refer [ordered-set]]
            [anbf.action :refer :all]
            [clojure.tools.logging :as log]))

(defprotocol NetHackWriter
  (write [this cmd] "Write a string to the NetHack terminal as if typed."))

(defrecord Delegator [writer handlers inhibited]
  NetHackWriter
  (write [this cmd]
    (if-not (:inhibited this)
      ((:writer this) cmd))
    this))

(defn new-delegator [writer]
  (Delegator. writer (ordered-set) false))

(defn set-inhibition
  "When inhibited the delegator keeps delegating events but doesn't delegate any commands or writes."
  [delegator state]
  (assoc-in delegator [:inhibited] state))

(defn register [delegator handler]
  (update-in delegator [:handlers] conj handler))

(defn deregister [delegator handler]
  (update-in delegator [:handlers] disj handler))

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
  "Events are propagated to all handlers satisfying the protocol in the order of their registration."
  [protocol method delegator & args]
  (doall (map #(apply invoke-handler protocol method % args)
              (:handlers delegator))))

(defn- invoke-command
  "Commands are propagated to handlers in reverse order and only up to the first handler that satisfies the protocol and returns a truthy value."
  [protocol method delegator & args]
  (if-not (:inhibited delegator)
    (loop [[handler & more-handlers] (rseq (:handlers delegator))]
      ;(log/debug "invoking next command handler" handler)
      (or (apply invoke-handler protocol method handler args)
          (if (seq more-handlers)
            (recur more-handlers)
            (throw (IllegalStateException.
                     (str "No handler responded to command of "
                          (:on-interface protocol)))))))))

(defn- respond-prompt [protocol method delegator & args]
  (write delegator (apply invoke-command protocol method delegator args)))

(defn- respond-action [protocol method delegator & args]
  ; TODO PerformedAction event a podle nej se obcas treba vymeni scraper?
  (log/info "<<< bot logic >>>")
  (as-> (apply invoke-command protocol method delegator args) action
    (apply perform action args)
    (write delegator action)))

(defn- delegation-impl [invoke-fn protocol [method [delegator & args]]]
  `(~method [~delegator ~@args]
            (~invoke-fn ~protocol ~method ~delegator ~@args)
            ~delegator))

(defmacro ^:private defprotocol-delegated
  [invoke-fn protocol & proto-methods]
  `(do (defprotocol ~protocol ~@proto-methods)
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

; called when the frame on screen is complete - the cursor is on the player, the map and status lines are completely drawn.
(defeventhandler FullFrameHandler
  (full-frame [handler frame]))

(defeventhandler GameStateHandler
  (started [handler])
  (ended [handler]))

(defeventhandler ToplineMessageHandler
  (message [handler text]))

; command protocols:

; defprompthandler (=> String)
; defactionhandler (=> Action)
; defmenuhandler (=> MenuOption?)
; deflocationhandler (=> x y)

(defprompthandler ChooseCharacterHandler
  (choose-character [handler]))

(defactionhandler ActionHandler
  (choose-action [handler anbf]))
