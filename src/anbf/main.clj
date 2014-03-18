(ns anbf.main
  (:require [anbf.jta :refer :all])
  (:import (de.mud.jta.event OnlineStatusListener))
  (:gen-class))

(defrecord ANBF
  [config
   jta])
;  ...

(defn new-anbf []
  (ANBF. (atom nil) (new-telnet-jta)))

(defn- load-config
  ([]
   (load-config "anbf-config.clj"))
  ([fname]
   (binding [*read-eval* false]
     (read-string (slurp fname)))))

(defn start
  ([]
   (def s (new-anbf)) ; "default" instance for the REPL
   (start s))
  ([anbf]
   (reset! (:config anbf) (load-config))
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
  (start))
