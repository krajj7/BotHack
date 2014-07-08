(ns anbf.jta
  "This is the interface to NetHack.  The JTA library (http://www.javatelnet.org) is used for shell/SSH/Telnet implementation and terminal emulation."
  (:require [anbf.delegator :refer :all]
            [anbf.util :refer :all]
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

(defn- new-jta [pl protocol config delegator]
  (if (config-get config :ttyrec false)
    (.addPlugin pl "Ttyrec" "ttyrec"))
  (JTA. pl protocol (-> (.addPlugin pl "NHTerminal" "terminal")
                        (set-delegator delegator))))

(defmulti init-jta
  "Returns a set up JTA instance using protocol handler given in the config."
  (fn [config delegator] (config-get config :interface)))

(defmethod init-jta :shell [config delegator]
  (let [pl (plugin-loader delegator)
        protocol (.addPlugin pl "Shell" "protocol")]
    (.broadcast pl (ConfigurationRequest.
                     (doto (PluginConfig. (Properties.))
                       (.setProperty "Shell" "command"
                                     (config-get config :nh-command)))))
    (new-jta pl protocol config delegator)))

(defmethod init-jta :ssh [config delegator]
  (let [pl (plugin-loader delegator)
        protocol (.addPlugin pl "JTAJSch" "protocol")]
    (.broadcast pl (ConfigurationRequest.
                     (doto (PluginConfig. (Properties.))
                       (.setProperty "SSH" "user"
                                     (config-get config :ssh-user))
                       (.setProperty "SSH" "password"
                                     (config-get config :ssh-pass)))))
    (new-jta pl protocol config delegator)))

(defmethod init-jta :telnet [config delegator]
  (let [pl (plugin-loader delegator)]
    (.addPlugin pl "Socket" "socket")
    (new-jta pl (.addPlugin pl "Telnet" "protocol") config delegator)))

(defmethod init-jta :default [_ _]
  (throw (IllegalArgumentException. "Invalid :interface configuration")))

(defn start-jta [jta host port]
  (.broadcast (:pl jta) (SocketRequest. host port)) ; connect
  jta)

(defn stop-jta [jta]
  (.broadcast (:pl jta) (SocketRequest.)) ; disconnect
  jta)

(defn raw-write
  "Writes a string to the terminal back-end."
  [jta ch]
  (io! (.write ^anbf.NHTerminal (:terminal jta)
               (->> ch str (map byte) byte-array))))
