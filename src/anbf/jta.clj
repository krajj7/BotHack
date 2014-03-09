; tady by mely byt funkce pro spravu spojeni s NetHackem – skrz Telnet/SSH nebo lokalni PTY, jak umoznuje Javovska knihovna JTA (http://www.javatelnet.org)
; pro zacatek se budu zabyvat jen spojenim pres Telnet

(ns anbf.jta
  (:import (de.mud.jta PluginLoader))
  (:import (de.mud.jta.event SocketRequest OnlineStatusListener))
  (:import (java.util Vector)))

; obalovak pro JTA knihovnu - vsechno je mutable
(deftype JTA
  [pl ; JTA plugin loader
   protocol ; protocol filter plugin (Telnet/SSH/Shell)
   terminal ; topmost JTA filter plugin - terminal emulator
   ])

(defn new-telnet-JTA "vrati JTA instanci s Telnet backendem" []
  ;                                 cesty kde hleda pluginy
  (let [pl (PluginLoader. (Vector. ["de.mud.jta.plugin" "anbf"]))]
    (.addPlugin pl "Socket" "socket")
    (JTA. pl (.addPlugin pl "Telnet" "telnet") (.addPlugin pl "NHTerminal" "terminal"))))

(defn start-JTA [jta host port]
  (.broadcast (.pl jta) (SocketRequest. host port)) ; connect
  jta)

(defn stop-JTA [jta]
  (.broadcast (.pl jta) (SocketRequest.)) ; disconnect
  jta)
