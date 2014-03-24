(ns anbf.main
  (:require [anbf.jta :refer :all]
            [anbf.delegator :refer :all]
            [anbf.term :refer :all])
  (:import (de.mud.jta.event OnlineStatusListener))
  (:gen-class))

(defrecord ANBF
  [config
   delegator
   jta])
;  ...

(defn- load-config [fname]
  (binding [*read-eval* false]
    (read-string (slurp fname))))

(defn register-handler
  "Register a handler implementing event protocols it is interested in to the delegator"
  [anbf handler]
  (swap! (:delegator anbf) register handler)
  anbf)

(defn new-anbf
  ([]
   (new-anbf "anbf-config.clj"))
  ([fname]
   (let [delegator (atom (new-delegator))
         anbf (ANBF. (atom (load-config fname))
                     delegator
                     (new-telnet-jta delegator))]
     (-> anbf
         (register-handler (reify OnlineStatusHandler
                             (online [this]
                               (println "Connection status: online")
                               this)
                             (offline [this]
                               (println "Connection status: offline")
                               this)))
         (register-handler (reify RedrawEventHandler
                             (redraw [this _ new-frame]
                               (println "new frame:")
                               (def y new-frame) ; XXX for debugging
                               (print-frame new-frame))))))))

(defn start
  ([]
   (def s (new-anbf)) ; "default" instance for the REPL
   (start s))
  ([anbf]
   (let [config @(:config anbf)]
     (start-jta (:jta anbf) (:host config) (:port config)))
   anbf))

(defn stop
  ([]
   (stop s))
  ([anbf]
   (stop-jta (:jta anbf))
   anbf))

(defn raw-command
  "Sends a raw string to the NetHack terminal as if typed."
  [anbf ch]
  (write (:jta anbf) ch))

(defn -main [& args] []
  (let [anbf (new-anbf (comment TODO config fname from args))] ; TODO
    (def s anbf)
    (start anbf)))
