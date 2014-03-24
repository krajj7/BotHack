; The delegator delegates event invocations to all registered handlers (which implement the handler protocol/interface for event types they are interested in).  It is supposed to process event invocations synchronously and strictly sequentially (even if they come from different threads, hence the locking).

(ns anbf.delegator)

; event types

(defprotocol OnlineStatusHandler
  (online [handler])
  (offline [handler]))

(defprotocol RedrawEventHandler
  (redraw [handler old-frame new-frame]))

; end event types

(defn- invoke-single [protocol method handler & args]
  (if (satisfies? protocol handler)
    (apply method handler args)
    handler))

(defn- invoke-all-synchronized [lockobj protocol method handlers & args]
  (locking lockobj
    (doall (map #(apply invoke-single protocol method % args)
                handlers))))

; TODO macro?
(defrecord Delegator [handlers lockobj]
  OnlineStatusHandler
  (online [_]
    (invoke-all-synchronized
      lockobj OnlineStatusHandler online handlers))
  (offline [_]
    (invoke-all-synchronized
      lockobj OnlineStatusHandler offline handlers))
  RedrawEventHandler
  (redraw [_ old-frame new-frame]
    (invoke-all-synchronized
      lockobj RedrawEventHandler redraw handlers old-frame new-frame)))

(defn new-delegator []
  (Delegator. [] (Object.)))

(defn register [delegator handler]
  (Delegator. (conj (:handlers delegator) handler)
              (:lockobj delegator)))
