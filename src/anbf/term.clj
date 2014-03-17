; An implementation of a Terminal plugin for JTA without a GUI
; It interprets terminal escape sequences, cursor movement etc. using the vt320 emulation in JTA, keeps a representation of the screen in memory for querying and publishes redraw events for a higher-level interpretation elsewhere.
; Similar in structure to the JTA Terminal.java except it doesn't have the GUI-related stuff

(ns anbf.term
  (:require [clojure.pprint :refer [pprint]])
  (:import (de.mud.jta FilterPlugin PluginBus)
           (de.mud.terminal vt320 VDUDisplay VDUBuffer)
           (de.mud.jta.event TelnetCommandRequest SetWindowSizeRequest
                             TerminalTypeListener LocalEchoListener
                             OnlineStatusListener)
           (java.io IOException))
  (:gen-class 
    :name anbf.NHTerminal
    :extends de.mud.jta.Plugin
    :implements [de.mud.jta.FilterPlugin Runnable]
    :state state
    :init init
    :post-init post-init))

(defn -getFilterSource [this source]
  (:source @(.state this)))

(defn -setFilterSource [this source]
  (swap! (.state this) into {:source source}))

(defn -read [this b]
  (.read (:source @(.state this)) b))

(defn -write [this b]
  (.write (:source @(.state this)) b))

; an immutable representation of a terminal window contents
; character attributes underline or blink etc. are not represented, only the foreground colors (affected by boldness) are important for NetHack
(defrecord Frame
  [colors ; vector of 24 vectors where each element represents the FG color for the corresponding character (80 per line)
   lines ; vector of 24 Strings representing text on each row of the terminal
   cursor-x ; cursor position
   cursor-y])

(def colors [nil :red :green :brown :blue :magenta :cyan :gray ; non-bold
             :bold :orange :bright-green :yellow :bright-blue :bright-magenta :bright-cyan :white]) ; bold

(defn print-frame [f]
  (println "==============")
  (println "Colors:")
  (doall (map #(if (every? nil? %1)
                 (println nil)
                 (println %1))
              (:colors f)))
  (println "Chars:")
  (pprint (:lines f))
  (println "Cursor:" (:cursor-x f) (:cursor-y f)))

(def ^:private fg-color-mask 0x1e0)
(def ^:private boldness-mask 0x1)

(defn- unpack-colors
  "for an int[] (row) of JTA attributes make a vector of FG colors (represented by keywords or nil for black)"
  [attrs]
  (map #(as-> % bits
              (bit-and fg-color-mask bits)
              (if (zero? bits) 0 (dec (bit-shift-right bits 5)))
              (+ bits (* 8 (bit-and boldness-mask %))) ; modify by boldness
              (colors bits))
       (take 80 attrs)))

(defn frame-from-buffer
  "Makes an immutable snapshot (Frame) of a JTA terminal buffer (takes only last 24 lines)."
  [buf]
  (Frame. (vec (map unpack-colors
                    (take-last 24 (.charAttributes buf))))
  ; turns char[][] into a vector of Strings with null bytes replaced by spaces
          (vec (map #(apply str (replace {(char 0) \space} %))
                    (take-last 24 (.charArray buf))))
          (.getCursorColumn buf)
          (.getCursorRow buf)))

(defn update-frame
  "Returns an updated frame snapshot as modified by a redraw."
  [f newbuf update]
  nil) ; TODO

(defn -init [bus id]
  [[bus id] (atom
              {:source nil ; source FilterPlugin
               :emulation nil ; vt320/VDUBuffer/VDUInput
               :display nil})]) ; VDUDisplay

(defn -run [this]
  (println "Terminal: reader started")
  (let [state @(.state this)
        buffer (byte-array 256)]
    (try 
      (loop []
        ;(println "Terminal: about to .read()")
        (let [n (.read (:source state) buffer)] ; blocking read
          (if (pos? n)
            ; latin1 is the default JTA swears by
            (.putString (:emulation state) (String. buffer 0 n "latin1")))
          (if-not (neg? n) ; -1 would mean the stream is dead
            (recur))))
      (catch IOException e
        (println "Terminal: reader IOException"))))
  (println "Terminal: reader broke out of loop, ending"))

(defn -post-init [this-terminal bus id]
  (let [state (.state this-terminal)
        emulation (proxy [vt320] []
                    (write [b]
                      (-write this-terminal b))
                    (sendTelnetCommand [cmd]
                      (.broadcast bus (TelnetCommandRequest. cmd)))
                    ; ignore setWindowSize()
                    ; ignore beep()
                    )
        display (reify VDUDisplay
                  (redraw [this-display]
                    ;(println "Terminal: redraw called")
                    ; TODO if all lines updated, start with a new frame
                    ; TODO predavat vysledne framy nahoru do frameworku
                    (def x emulation)
                    (def y (frame-from-buffer emulation))
                    (println "new frame:")
                    (print-frame y))
                  (updateScrollBar [_]
                    nil)
                  (setVDUBuffer [this-display buffer]
                    ;(println "Terminal: set buffer + display")
                    (.setDisplay buffer this-display))
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
                         ; the reader thread is going to stop itself on IO error
                                 (offline [_]
                                   (println "Terminal: offline"))
                                 (online [_]
                                   (println "Terminal: online")
                                   (.start (Thread. this-terminal))))))))
