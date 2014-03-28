(ns anbf.util
  (:require [anbf.delegator :refer [register deregister]]
            [anbf.jta :refer [write]]))

(defn register-handler
  "Register a handler implementing command/event protocols it is interested in to the delegator"
  [anbf handler]
  (swap! (:delegator anbf) register handler)
  anbf)

(defn deregister-handler
  "Deregister the given handler from the delegator"
  [anbf handler]
  (swap! (:delegator anbf) deregister handler)
  anbf)

(defn replace-handler
  "Deregister a handler and register a different one"
  [anbf handler-old handler-new]
  (swap! (:delegator anbf) #(-> %
                                (deregister handler-old)
                                (register handler-new)))
  anbf)

(defn raw-write
  "Sends a raw string to the NetHack terminal as if typed."
  [anbf ch]
  (write (:jta anbf) ch))
