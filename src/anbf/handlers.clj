(ns anbf.handlers
  (:require [clojure.tools.logging :as log]
            [anbf.delegator :refer :all]))

(defn register-handler
  [anbf & args]
  (send (:delegator anbf) #(apply register % args))
  anbf)

(defn deregister-handler
  [anbf handler]
  (send (:delegator anbf) deregister handler)
  anbf)

(defn replace-handler
  [anbf handler-old handler-new]
  (send (:delegator anbf) switch handler-old handler-new)
  anbf)
