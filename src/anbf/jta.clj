; This is the interface to NetHack
; The JTA library (http://www.javatelnet.org) is used for Telnet/SSH/shell command runner implementation and terminal emulation
; For now only Telnet is supported

(ns anbf.jta
  (:require [anbf.delegator :refer :all]
            [anbf.term :refer [set-delegator]])
  (:import [anbf.NHTerminal]
           [de.mud.jta PluginLoader Plugin PluginConfig]
           [de.mud.jta.event SocketRequest OnlineStatusListener
                             ConfigurationRequest]
           [java.util Vector Properties]))

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
                                 (send delegator offline))
                               (online [_]
                                 (send delegator online))))))

(defn- new-jta [pl protocol delegator]
  (JTA. pl protocol (-> (.addPlugin pl "NHTerminal" "terminal")
                        (set-delegator delegator))))

(defn new-shell-jta
  "Returns a set up JTA instance using the Shell plugin as protocol handler, running the given command."
  [delegator command]
  (let [pl (plugin-loader delegator)
        protocol (.addPlugin pl "Shell" "protocol")]
    (.broadcast pl (ConfigurationRequest.
                     (doto (PluginConfig. (Properties.))
                       (.setProperty "Shell" "command" command))))
    (new-jta pl protocol delegator)))

(defn new-ssh-jta
  "Returns a set up JTA instance using the SSH plugin as protocol handler."
  [delegator user pass]
  (let [pl (plugin-loader delegator)
        protocol (.addPlugin pl "JTAJSch" "protocol")]
    (.broadcast pl (ConfigurationRequest.
                     (doto (PluginConfig. (Properties.))
                       (.setProperty "SSH" "user" user)
                       (.setProperty "SSH" "password" pass))))
    (new-jta pl protocol delegator)))

(defn new-telnet-jta
  "Returns a set up JTA instance using the Telnet plugin as protocol handler."
  [delegator]
  (let [pl (plugin-loader delegator)]
    (.addPlugin pl "Socket" "socket")
    (new-jta pl (.addPlugin pl "Telnet" "protocol") delegator)))

(defn start-jta [jta host port]
  (.broadcast (:pl jta) (SocketRequest. host port)) ; connect
  jta)

(defn stop-jta [jta]
  (.broadcast (:pl jta) (SocketRequest.)) ; disconnect
  jta)

(defn raw-write
  "Writes a string to the terminal back-end."
  [jta ch]
  (io! (.write (:terminal jta)
               (->> ch str (map byte) byte-array))))
