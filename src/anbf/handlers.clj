(ns anbf.handlers
  (:require [clojure.tools.logging :as log]
            [anbf.util :refer :all]
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

(defn update-on-known-position
  "When player position on map is known call (apply swap! game f args)"
  [anbf f & args]
  (register-handler anbf priority-top
    (reify FullFrameHandler
      (full-frame [this _]
        (apply swap! (:game anbf) f args)
        (deregister-handler anbf this)))))
