(ns anbf.main
  (:require [clojure.tools.logging :as log]
            [anbf.anbf :refer :all]
            [anbf.util :refer :all]
            [anbf.jta :refer [raw-write]]
            [anbf.delegator :refer :all]
            [clojure.java.io :as io]
            [cemerick.pomegranate :as pom])
  (:gen-class))

(defn- init-ui [anbf]
  (register-handler anbf (reify RedrawHandler
                           (redraw [_ frame]
                             (println frame)))))

(defn- register-javabot-jars []
  (dorun (map pom/add-classpath
              (filter #(-> % .getName (.endsWith ".jar"))
                      (file-seq (io/file "javabots/bot-jars"))))))

(defn -main [& args] []
  (register-javabot-jars)
  (->> (take 1 args) (apply new-anbf) init-ui start (def a)))

; shorthand functions for REPL use
(defn- w
  [ch]
  (raw-write (:jta a) ch))

(defn- p []
  (pause a))

(defn- s []
  (stop a))

(defn- u []
  (if (:inhibited @(:delegator a))
    (unpause a)))
