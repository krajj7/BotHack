; The delegator delegates event and command invocations to all registered handlers which implement the protocol for the given event/command type.  It is supposed to process event invocations synchronously and sequentially, even if they come from different threads.

(ns anbf.delegator
  (:require [flatland.ordered.set :refer [ordered-set]]
            [clojure.tools.logging :as log]))

; event protocols

(defprotocol ConnectionStatusHandler
  (online [handler])
  (offline [handler]))

(defprotocol RedrawHandler
  (redraw [handler frame]))

(defprotocol GameStateHandler
  (started [handler])
  (ended [handler]))

; end event protocols

; command protocols

; TODO game action requests, prompt/menu reponders

; end command protocols

(defn- invoke-handler [protocol method handler & args]
  (if (satisfies? protocol handler)
    (apply method handler args)))

(defn- invoke-event [lockobj protocol method handlers & args]
  "Events are propagated to all handlers satisfying the protocol in the order of their registration."
  (locking lockobj
    (try
      (doall (map #(apply invoke-handler protocol method % args)
                  handlers))
      (catch Exception e
        (log/error e "Delegator caught handler exception")))))

(defn- invoke-command [lockobj protocol method handlers & args]
  "Commands are propagated to handlers in reverse order and only up to the first handler that satisfies the protocol and returns a non-null value."
  (locking lockobj
    ; TODO rseq
    (throw (UnsupportedOperationException. "not implemented yet"))))

; TODO macro?
(defrecord Delegator [handlers lockobj]
  ConnectionStatusHandler
  (online [_]
    (invoke-event
      lockobj ConnectionStatusHandler online handlers))
  (offline [_]
    (invoke-event
      lockobj ConnectionStatusHandler offline handlers))
  RedrawHandler
  (redraw [_ frame]
    (invoke-event
      lockobj RedrawHandler redraw handlers frame))
  GameStateHandler
  (started [_]
    (invoke-event
      lockobj GameStateHandler started handlers))
  (ended [_]
    (invoke-event
      lockobj GameStateHandler ended handlers)))

(defn new-delegator []
  (Delegator. (ordered-set) (Object.)))

(defn register [delegator handler]
  (Delegator. (conj (:handlers delegator) handler)
              (:lockobj delegator)))

(defn deregister [delegator handler]
  (Delegator. (disj (:handlers delegator) handler)
              (:lockobj delegator)))
