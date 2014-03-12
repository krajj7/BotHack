(ns anbf.main
  (:require [anbf.jta :refer :all])
  (:import (de.mud.jta.event OnlineStatusListener))
  (:gen-class))

;(set! *warn-on-reflection* true)

(defrecord anbf
  [config
   jta])
;  ...

(defn new-anbf []
  (anbf. (atom nil) (new-telnet-jta)))

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
   #_ (.registerPluginListener (:pl (:jta anbf))
                            (reify OnlineStatusListener
                              (online [_] (println "main: online"))
                              (offline [_] (println "main: offline"))))
   (let [config @(:config anbf)]
     (start-jta (:jta anbf) (:host config) (:port config)))
   anbf))

(defn stop
  ([]
   (stop s))
  ([anbf]
   (stop-jta (:jta anbf))
   anbf))

(defn -main [& args] []
  (def s (new-anbf))
  (let [jta (:jta s)]
    (println "loaded plugins:")
    (println (.getPlugins (:pl jta)))

    (start s)
    (println "bezi")
    (println "id terminalu:" (.getTerminalID (:emulation @(.state (:terminal jta)))))

    #_ (let [buffer (byte-array 5000)
          proto (:terminal jta)]
      (println (.read proto buffer))
      (println (.read proto buffer))
      (println (.read proto buffer))
      (println (.read proto buffer))
      (println (.write proto (.getBytes "q")))
      (println (.write proto (.getBytes "\n")))
      (println (.read proto buffer))
      (println (String. buffer))
    )

    (stop s)))
