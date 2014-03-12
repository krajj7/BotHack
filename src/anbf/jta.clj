; tady by mely byt funkce pro spravu spojeni s NetHackem – skrz Telnet/SSH nebo lokalni PTY, jak umoznuje Javovska knihovna JTA (http://www.javatelnet.org)
; pro zacatek se budu zabyvat jen spojenim pres Telnet

(ns anbf.jta
  (:import anbf.NHTerminal)
  (:import (de.mud.jta PluginLoader Plugin)
           (de.mud.jta.event SocketRequest OnlineStatusListener))
  (:import (java.util Vector)))

; JTA library wrapper
(defrecord jta
  [^PluginLoader pl ; JTA plugin loader
   ^Plugin protocol ; protocol filter plugin (Telnet/SSH/Shell)
   ^NHTerminal terminal ; topmost JTA filter plugin - terminal emulator
   ])

(defn new-telnet-jta "vrati JTA instanci s Telnet backendem" []
  ;                        seznam package kde to hleda pluginy
  (let [pl (PluginLoader. (Vector. ["de.mud.jta.plugin" "anbf"]))]
    (.addPlugin pl "Socket" "socket")
    (jta. pl (.addPlugin pl "Telnet" "telnet") (.addPlugin pl "NHTerminal" "terminal"))))

(defn start-jta [jta host port]
  (.broadcast (:pl jta) (SocketRequest. host port)) ; connect
  jta)

(defn stop-jta [jta]
  (.broadcast (:pl jta) (SocketRequest.)) ; disconnect
  jta)
