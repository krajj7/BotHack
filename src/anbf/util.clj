(ns anbf.util
  (:require [anbf.delegator :refer [register deregister]]))

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

(defn config-get-direct
  [config key]
  (or (get config key)
      (throw (IllegalStateException.
               (str "Configuration missing key: " key)))))

(defn config-get
  "Get a configuration key or return the default, without a default throw an exception if the key is not present."
  ([anbf key]
   (config-get-direct (:config anbf) key))
  ([anbf key default]
   (get (:config anbf) key default)))
