(ns anbf.main
  (:require [anbf.jta :refer :all])
  (:import (de.mud.jta.event OnlineStatusListener))
  (:gen-class))

(defrecord anbf
  [jta
   config
   ; ...
  ])

(defn new-anbf []
  (anbf. (new-telnet-jta) (atom nil)))

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
   (.registerPluginListener (:pl (:jta anbf))
                            (reify OnlineStatusListener
                              (online [_] (println "online"))
                              (offline [_] (println "offline"))))
   (let [config @(:config anbf)]
     (start-jta (:jta anbf) (:host config) (:port config)))))

(defn stop
  ([]
   (stop s))
  ([anbf]
   (stop-jta (:jta anbf))))

(defn -main [& args] []
  (let [anbf (new-anbf)]
    (println "loaded plugins:")
    (println (.getPlugins (:pl (:jta anbf))))

    (start anbf)
    (println "bezi")

    #_ (let [buffer (byte-array 5000)
          proto (:terminal (:jta anbf))]
      (println (.read proto buffer))
      (println (.read proto buffer))
      (println (.read proto buffer))
      (println (.read proto buffer))
      (println (.write proto (.getBytes "q")))
      (println (.write proto (.getBytes "\n")))
      (println (.read proto buffer))
      (println (String. buffer))
    )

    (stop anbf)))
