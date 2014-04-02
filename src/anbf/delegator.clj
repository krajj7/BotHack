; The delegator delegates event and command invocations to all registered handlers which implement the protocol for the given event/command type.  It is supposed to process event invocations synchronously and sequentially, even if they come from different threads.

(ns anbf.delegator
  (:require [flatland.ordered.set :refer [ordered-set]]
            [clojure.tools.logging :as log]))

(defrecord Delegator [writer handlers lock])

(defn new-delegator [writer]
  (Delegator. writer (ordered-set) (Object.)))

(defn register [delegator handler]
  (update-in delegator [:handlers] conj handler))

(defn deregister [delegator handler]
  (update-in delegator [:handlers] disj handler))

(defn- invoke-handler [protocol method handler & args]
  (if (satisfies? protocol handler)
    (try
      (apply method handler args)
      (catch Exception e
        (log/error e "Delegator caught handler exception")))))

(defn- invoke-event [protocol method delegator & args]
  "Events are propagated to all handlers satisfying the protocol in the order of their registration."
  (doall (map #(apply invoke-handler protocol method % args)
              (:handlers delegator))))

(defn- invoke-command [protocol method delegator & args]
  "Commands are propagated to handlers in reverse order and only up to the first handler that satisfies the protocol and returns a truthy value."
  (loop [[handler & more-handlers] (rseq (:handlers delegator))]
    (or (apply invoke-handler protocol method handler args)
        (if (seq more-handlers)
          (recur more-handlers)
          (throw (IllegalStateException.
                  (str "No handler responded to command of "
                       (:on-interface protocol))))))))

(defn- respond-prompt [protocol method delegator & args]
  ((:writer delegator) (apply invoke-command protocol method delegator args)))

(defn- delegation-impl [invoke-fn protocol [method [delegator & args]]]
  `(~method [~delegator ~@args]
            (locking (:lock ~delegator)
              (~invoke-fn ~protocol ~method ~delegator ~@args))))

(defmacro ^:private defprotocol-delegated
  [invoke-fn protocol & proto-methods]
  `(do (defprotocol ~protocol ~@proto-methods)
       (extend-type Delegator ~protocol
         ~@(map (partial delegation-impl invoke-fn protocol)
                proto-methods))))

(defmacro ^:private defeventhandler [protocol & proto-methods]
  `(defprotocol-delegated invoke-event ~protocol ~@proto-methods))

(defmacro ^:private defprompthandler [protocol & proto-methods]
  `(defprotocol-delegated respond-prompt ~protocol ~@proto-methods))

; event protocols:

(defeventhandler ConnectionStatusHandler
  (online [handler])
  (offline [handler]))

(defeventhandler RedrawHandler
  (redraw [handler frame]))

(defeventhandler GameStateHandler
  (started [handler])
  (ended [handler]))

; command protocols:

(defprompthandler ChooseCharacterHandler
  (choose-character [handler]))

; defprompthandler (=> String)
; defactionhandler (=> Action)
; defmenuhandler (=> MenuOption?)
; deflocationhandler (=> x y)

; TODO game action requests, prompt/menu reponders
