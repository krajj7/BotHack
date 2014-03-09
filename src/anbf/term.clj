; implementace Terminal pluginu pro JTA - interpretuje escape sekvence pohybu kurzoru apod. (s vyuzitim pred-implementovane vt320 emulace v JTA)

(ns anbf.term
  (:import (de.mud.jta FilterPlugin PluginBus)
           (de.mud.terminal vt320)
           (de.mud.jta.event TelnetCommandRequest SetWindowSizeRequest))
  (:gen-class 
    :name anbf.NHTerminal
    :extends de.mud.jta.Plugin
    :implements [de.mud.jta.FilterPlugin]
    :state state
    :init init
    :post-init post-init
  ))

(defn -init [^PluginBus bus ^String id]
  [[bus id] (atom {:source nil :emulation nil})])

(defn -post-init [this ^PluginBus bus ^String id]
  (swap! (.state this)
         into {:emulation (proxy [vt320] []
                            (write [b]
                              (-write this b))
                            (sendTelnetCommand [cmd]
                              (.broadcast bus (TelnetCommandRequest. cmd)))
                            ; ignore setWindowSize()
                            ; ignore beep()
                            )}))

(defn -getFilterSource [this ^FilterPlugin source]
  (:source @(.state this)))

(defn -setFilterSource [this ^FilterPlugin source]
  (swap! (.state this) into {:source source}))

(defn -read [this b]
  (.read (:source @(.state this)) b))

(defn -write [this b]
  (.write (:source @(.state this)) b))
