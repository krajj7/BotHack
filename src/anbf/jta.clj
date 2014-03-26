; This is the interface to NetHack
; The JTA library (http://www.javatelnet.org) is used for Telnet/SSH/shell command runner implementation and terminal emulation
; For now only Telnet is supported

(ns anbf.jta
  (:require [anbf.delegator :refer :all]
            [anbf.term :refer [set-delegator]])
  (:import anbf.NHTerminal)
  (:import (de.mud.jta PluginLoader Plugin PluginConfig)
           (de.mud.jta.event SocketRequest OnlineStatusListener
                             ConfigurationRequest))
  (:import (java.util Vector Properties)))

; JTA library wrapper
(defrecord JTA
  [pl ; JTA plugin loader
   protocol ; protocol filter plugin (Telnet/SSH/Shell)
   terminal]) ; topmost JTA filter plugin - terminal emulator

(defn- plugin-loader [delegator]
  ;                   list of packages JTA searches for plugins
  (doto (PluginLoader. (Vector. ["de.mud.jta.plugin" "anbf"]))
    ; translate the JTA event
    (.registerPluginListener (reify OnlineStatusListener
                               (offline [_]
                                 (offline @delegator))
                               (online [_]
                                 (online @delegator))))))

(defn- new-jta [pl protocol delegator]
  (JTA. pl protocol (-> pl
                        (.addPlugin "NHTerminal" "terminal")
                        (set-delegator delegator))))

(defn new-shell-jta 
  [delegator command]
  "returns a set up JTA instance using the Shell plugin as protocol handler, running the given command"
  (let [pl (plugin-loader delegator)
        protocol (.addPlugin pl "Shell" "protocol")]
    (.broadcast pl (ConfigurationRequest. 
                     (doto (PluginConfig. (Properties.))
                       (.setProperty "Shell" "command" command))))
    (new-jta pl protocol delegator)))

(defn new-telnet-jta 
  [delegator]
  "returns a set up JTA instance using the Telnet plugin as protocol handler"
  (let [pl (plugin-loader delegator)]
    (.addPlugin pl "Socket" "socket")
    (new-jta pl (.addPlugin pl "Telnet" "protocol") delegator)))

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
