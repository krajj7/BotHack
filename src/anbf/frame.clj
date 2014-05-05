(ns anbf.frame
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :as pprint]
            [clojure.string :as string]))

; an immutable representation of a terminal window contents
; character attributes underline or blink etc. are not represented, only the foreground colors (affected by boldness) are important for NetHack
(defrecord Frame
  [lines ; vector of 24 Strings representing text on each row of the terminal
   colors ; vector of 24 vectors of keywords representing the FG color for the corresponding character (80 per line)
   cursor]
  anbf.bot.IFrame)

(defmethod print-method Frame [f w]
  (.write w "==== <Frame> ====\n")
  (pprint/write (:lines f) :stream w)
  (.write w (format "\nCursor: %s %s\n" (-> f :cursor :x) (-> f :cursor :y)))
  (.write w "=================\n"))

(def colormap
  [nil :red :green :brown :blue ; non-bold
   :magenta :cyan :gray
   :bold :orange :bright-green :yellow ; bold
   :bright-blue :bright-magenta :bright-cyan :white
   :inverse :inv-red :inv-green :inv-brown ; inverse
   :inv-blue :inv-magenta :inv-cyan :inv-gray
   :inv-bold :inv-orange :inv-bright-green :inv-yellow ; inverse+bold
   :inv-bright-blue :inv-bright-magenta :inv-bright-cyan :inv-white])

(defn print-colors [f]
  (println "Colors:")
  (doall (map #(if (every? zero? %)
                 (println nil)
                 (println (map colormap %)))
              (:colors f))))

(defn nth-line
  "Returns the line of text on n-th line of the frame."
  [frame n]
  (nth (:lines frame) n))

(defn topline [frame]
  (nth-line frame 0))

(defn botls [frame]
  (subvec (:lines frame) 22))

(defn cursor-line
  "Returns the line of text where the cursor is on the frame."
  [frame]
  (nth-line frame (-> frame :cursor :y)))

(defn before-cursor
  "Returns the part of the line to the left of the cursor."
  [frame]
  (subs (cursor-line frame) 0 (-> frame :cursor :x)))

(defn before-cursor?
  "Returns true if the given text appears just before the cursor."
  [frame text]
  (.endsWith (before-cursor frame) text))
