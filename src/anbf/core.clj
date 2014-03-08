(ns anbf.core
  (:import (de.mud.jta PluginLoader PluginConfig))
  (:import (de.mud.jta.event SocketRequest OnlineStatusListener))
  (:import (de.mud.telnet TelnetWrapper))
  (:import (java.util Vector))
  (:gen-class))

(defn- telnetWrapperTest []
  (let [t (TelnetWrapper.)
        buf (byte-array (1024))]
    (.connect t "nethack.alt.org", 23)
    ;(.connect t "rainmaker.wunderground.com" 3000)
    (let [numbytes (.read t buf)]
        [(str (String. buf 0 numbytes)) numbytes])))

(defn -main [& args] []
  ;                                 cesty kde hleda pluginy
  (let [pl (PluginLoader. (Vector. ["de.mud.jta.plugin" "anbf"]))]
    (doto pl
      (.addPlugin "Socket" "socket")
      (.addPlugin "Telnet" "telnet")
      ;(.addPlugin "NethackTerminal" "nethackterm")
      )
    ;(println (.getPlugins pl))
    (.registerPluginListener pl (reify OnlineStatusListener
                                  (online [_] (println "online"))
                                  (offline [_] (println "offline"))))
    (.broadcast pl (SocketRequest. "nethack.alt.org" 23)) ; connect
    (Thread/sleep 1500)
    (.broadcast pl (SocketRequest.)) ; disconnect
    ))
