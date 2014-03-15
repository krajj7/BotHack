; This is the interface to NetHack
; The JTA library (http://www.javatelnet.org) is used for Telnet/SSH/shell command runner implementation and terminal emulation
; For now only Telnet is supported

(ns anbf.jta
  (:import anbf.NHTerminal)
  (:import (de.mud.jta PluginLoader Plugin)
           (de.mud.jta.event SocketRequest))
  (:import (java.util Vector)))

; JTA library wrapper
(defrecord JTA
  [pl ; JTA plugin loader
   protocol ; protocol filter plugin (Telnet/SSH/Shell)
   terminal]) ; topmost JTA filter plugin - terminal emulator

(defn new-telnet-jta "vrati JTA instanci s Telnet backendem" []
  ;                       list of packages JTA searches for plugins
  (let [pl (PluginLoader. (Vector. ["de.mud.jta.plugin" "anbf"]))]
    (.addPlugin pl "Socket" "socket")
    (JTA. pl (.addPlugin pl "Telnet" "telnet") (.addPlugin pl "NHTerminal" "terminal"))))

(defn start-jta [jta host port]
  (.broadcast (:pl jta) (SocketRequest. host port)) ; connect
  jta)

(defn stop-jta [jta]
  (.broadcast (:pl jta) (SocketRequest.)) ; disconnect
  jta)

(defn write
  "Writes a string to the terminal back-end"
  [jta ch]
  (.write (:terminal jta) (byte-array (map byte ch)))
  jta)
