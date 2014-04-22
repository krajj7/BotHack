(ns anbf.util
  (:require [anbf.delegator :refer :all]))

(defn register-handler
  "Register a system handler implementing command/event protocols it is interested in to the delegator"
  [anbf handler]
  (send (:delegator anbf) register-sys handler)
  anbf)

(defn deregister-handler
  "Deregister the given handler from the delegator"
  [anbf handler]
  (send (:delegator anbf) deregister handler)
  anbf)

(defn replace-handler
  "Deregister a handler and register a different system handler"
  [anbf handler-old handler-new]
  (send (:delegator anbf)
        #(-> % (deregister handler-old) (register-sys handler-new)))
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

(defn ctrl [ch]
  "Returns a char representing CTRL+<ch>"
  (char (- (int ch) 96)))

(def esc (str (char 27)))

(def backspace (str (char 8)))
