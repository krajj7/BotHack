(ns anbf.main
  (:require [clojure.tools.logging :as log]
            [anbf.anbf :refer :all]
            [anbf.util :refer :all]
            [anbf.jta :refer [raw-write]]
            [anbf.delegator :refer :all])
  (:gen-class))

(defn- init-ui [anbf]
  (register-handler anbf (reify RedrawHandler
                           (redraw [_ frame]
                             (println frame)))))

(defn -main [& args] []
  (->> (take 1 args) (apply new-anbf) init-ui start (def a)))

(defn- w
  "Helper write function for the REPL"
  [ch]
  (raw-write (:jta a) ch))

(defn- p []
  (pause a))

(defn- s []
  (stop a))

(defn- u []
  (if (:inhibited @(:delegator a)) (unpause a)))
