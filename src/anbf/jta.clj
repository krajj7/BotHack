; This is the interface to NetHack
; The JTA library (http://www.javatelnet.org) is used for Telnet/SSH/shell command runner implementation and terminal emulation
; For now only Telnet is supported

(ns anbf.jta
  (:require [anbf.delegator :refer :all]
            [anbf.term :refer [set-delegator]])
  (:import anbf.NHTerminal)
  (:import (de.mud.jta PluginLoader Plugin)
           (de.mud.jta.event SocketRequest OnlineStatusListener))
  (:import (java.util Vector)))

; JTA library wrapper
(defrecord JTA
  [pl ; JTA plugin loader
   protocol ; protocol filter plugin (Telnet/SSH/Shell)
   terminal]) ; topmost JTA filter plugin - terminal emulator

(defn new-telnet-jta 
  [delegator]
  "returns a JTA instance with basic plugins set up (using the Telnet plugin as protocol handler)"
  ;                       list of packages JTA searches for plugins
  (let [pl (PluginLoader. (Vector. ["de.mud.jta.plugin" "anbf"]))
        _ (.addPlugin pl "Socket" "socket")
        protocol (.addPlugin pl "Telnet" "protocol")
        terminal (.addPlugin pl "NHTerminal" "terminal")]
    (set-delegator terminal delegator)
    ; translate the JTA event for ANBF:
    (.registerPluginListener pl (reify OnlineStatusListener
                                  (offline [_]
                                    (offline @delegator))
                                  (online [_]
                                    (online @delegator))))
    (JTA. pl protocol terminal)))

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
