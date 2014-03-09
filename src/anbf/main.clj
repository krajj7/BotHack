(ns anbf.main
  (:require [anbf.jta :refer :all])
  (:import (de.mud.jta.event OnlineStatusListener))
  (:gen-class))

; instance celeho frameworku
(defrecord ANBF 
  [jta
   ; ...
  ])

(def host "nethack.alt.org")
(def port 23)
;(def host "rainmaker.wunderground.com")
;(def port 3000)

(defn new-system []
  (ANBF. (new-telnet-JTA)))

(defn start
  ([]
   (def s (new-system)) ; "defaultni" instance pro REPL
   (start s))
  ([system]
   (.registerPluginListener (.pl (:jta system)) (reify OnlineStatusListener
                                 (online [_] (println "online"))
                                 (offline [_] (println "offline"))))
   (start-JTA (:jta system) host port)))

(defn stop
  ([]
   (stop s))
  ([system]
   (stop-JTA (:jta system))))

(defn -main [& args] []
  (let [anbf (new-system)]
    (println "loaded plugins:")
    (println (.getPlugins (.pl (:jta anbf))))

    (start anbf)
    (println "bezi")

    #_ (let [buffer (byte-array 5000)
          proto (.terminal (:jta anbf))]
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
