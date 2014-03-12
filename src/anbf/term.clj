; implementace Terminal pluginu pro JTA - interpretuje escape sekvence pohybu kurzoru apod. (s vyuzitim pred-implementovane vt320 emulace v JTA)

(ns anbf.term
  (:import (de.mud.jta FilterPlugin PluginBus)
           (de.mud.terminal vt320 VDUDisplay VDUBuffer)
           (de.mud.jta.event TelnetCommandRequest SetWindowSizeRequest
                             TerminalTypeListener LocalEchoListener
                             OnlineStatusListener))
  (:gen-class 
    :name anbf.NHTerminal
    :extends de.mud.jta.Plugin
    :implements [de.mud.jta.FilterPlugin]
    :state state
    :init init
    :post-init post-init
  ))

(defn -getFilterSource [this ^FilterPlugin source]
  (:source @(.state this)))

(defn -setFilterSource [this ^FilterPlugin source]
  (swap! (.state this) into {:source source}))

(defn -read [this ^bytes b]
  (.read (:source @(.state this)) b))

(defn -write [this ^bytes b]
  (.write (:source @(.state this)) b))

(defn -init [^PluginBus bus ^String id]
  [[bus id] (atom
              {:source nil ; source FilterPlugin
               :emulation nil ; vt320/VDUBuffer/VDUInput
               :display nil ; VDUDisplay
               :reader nil})]) ; Thread

(defn -post-init [this-terminal ^PluginBus bus ^String id]
  (let [state (.state this-terminal)
        emulation (proxy [vt320] []
                    (write [^bytes b]
                      (-write this-terminal b))
                    (sendTelnetCommand [cmd]
                      (.broadcast bus (TelnetCommandRequest. cmd)))
                    ; ignore setWindowSize()
                    ; ignore beep()
                    )
        display (reify VDUDisplay
                  (redraw [this-display]
                    ; TODO redraw event dle (:emulation @state), resp. (.getVDUBuffer this-display)
                    (println "Terminal: redraw called"))
                  (updateScrollBar [_]
                    nil)
                  (^void setVDUBuffer [this-display ^VDUBuffer buffer]
                    (.setDisplay buffer this-display)
                    (println "Terminal: set buffer + display"))
                  (getVDUBuffer [this-display]
                    (:emulation @state)))]
    (.setVDUBuffer display emulation)
    (swap! state
           into {:emulation emulation
                 :display display})
    (doto bus
      (.registerPluginListener (reify TerminalTypeListener
                                 (getTerminalType [_]
                                   (.getTerminalID emulation))))
      (.registerPluginListener (reify LocalEchoListener
                                 (setLocalEcho [_ echo]
                                   (.setLocalEcho emulation echo))))
      (.registerPluginListener (reify OnlineStatusListener
                                 ; TODO launch reader
                                 (online [_]
                                   (println "Terminal: online")
                                   ; TODO in a loop, putString into emulation...  redraw should be called eventually
                                   (let [buffer (byte-array 256)]
                                     (println (.read this-terminal buffer))
                                     (println (String. buffer))))
                                 (offline [_]
                                   (println "Terminal: offline")))))))
