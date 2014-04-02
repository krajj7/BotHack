; The screen scraper handles redraw events, tries to determine when the frame is completely drawn and sends off higher-level events.  It looks for prompts and menus and triggers appropriate action selection commands.

(ns anbf.scraper
  (:require [clojure.tools.logging :as log]
            [anbf.util :refer :all]
            [anbf.delegator :refer :all]))

(defn- nth-line
  "Returns the line of text on n-th line of the frame."
  [frame n]
  (nth (:lines frame) n))

(defn- cursor-line
  "Returns the line of text where the cursor is on the frame."
  [frame]
  (nth-line frame (:cursor-y frame)))

(defn- before-cursor
  "Returns the part of the line to the left of the cursor."
  [frame]
  (subs (cursor-line frame) 0 (:cursor-x frame)))

(defn- before-cursor?
  "Returns true if the given text appears just before the cursor."
  [text frame]
  (.endsWith (before-cursor frame) text))

(defn scraper [{:keys [delegator] :as anbf}]
  (reify RedrawHandler
    (redraw [this frame]
      (log/info "scraping frame")
      (when (and (.startsWith (nth-line frame 1) "NetHack, Copyright")
                 (before-cursor? "] " frame))
        ; game just started
        ;(println "cursor line:" (cursor-line frame))
        (condp #(.startsWith %2 %1) (cursor-line frame)
          "There is already a game in progress under your name."
          (raw-write anbf "y\n") ; destroy old game
          "Shall I pick a character"
          (choose-character @delegator)
          nil)))))
