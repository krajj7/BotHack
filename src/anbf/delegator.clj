; The delegator delegates event and command invocations to all registered handlers which implement the protocol for the given event/command type.  It is supposed to process event invocations synchronously and sequentially, even if they come from different threads.

(ns anbf.delegator
  (:require [flatland.ordered.set :refer [ordered-set]]
            [clojure.tools.logging :as log]))

(defrecord Delegator [handlers lockobj])

(defn new-delegator []
  (Delegator. (ordered-set) (Object.)))

(defn register [delegator handler]
  (Delegator. (conj (:handlers delegator) handler)
              (:lockobj delegator)))

(defn deregister [delegator handler]
  (Delegator. (disj (:handlers delegator) handler)
              (:lockobj delegator)))

(defn- invoke-handler [protocol method handler & args]
  (if (satisfies? protocol handler)
    (try
      (apply method handler args)
      (catch Exception e
        (log/error e "Delegator caught handler exception")))))

(defn- invoke-event [protocol method handlers & args]
  "Events are propagated to all handlers satisfying the protocol in the order of their registration."
  (doall (map #(apply invoke-handler protocol method % args)
              handlers)))

(defn- invoke-command [lockobj protocol method handlers & args]
  "Commands are propagated to handlers in reverse order and only up to the first handler that satisfies the protocol and returns a non-null value."
  ; TODO rseq
  (throw (UnsupportedOperationException. "not implemented yet")))

(defn- delegation-impl [invoke-fn protocol [method [delegator & args]]]
  `(~method [~delegator ~@args]
            (locking (:lockobj ~delegator)
              (~invoke-fn ~protocol ~method (:handlers ~delegator) ~@args))))

(defmacro ^:private defprotocol-delegated
  [invoke-fn protocol & proto-methods]
  `(do (defprotocol ~protocol ~@proto-methods)
       (extend-type Delegator ~protocol
         ~@(map (partial delegation-impl invoke-fn protocol)
                proto-methods))))

(defmacro ^:private defeventhandler [protocol & proto-methods]
  `(defprotocol-delegated invoke-event ~protocol ~@proto-methods))

(defmacro ^:private defcommandhandler [protocol & proto-methods]
  `(defprotocol-delegated invoke-command ~protocol ~@proto-methods))

; event protocols:

(defeventhandler ConnectionStatusHandler
  (online [handler])
  (offline [handler]))

(defeventhandler ConnectionStatusHandler
  (online [handler])
  (offline [handler]))

(defeventhandler RedrawHandler
  (redraw [handler frame]))

(defeventhandler GameStateHandler
  (started [handler])
  (ended [handler]))

; command protocols:

; TODO game action requests, prompt/menu reponders
