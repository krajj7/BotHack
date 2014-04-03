; The screen scraper handles redraw events, tries to determine when the frame is completely drawn and sends off higher-level events.  It looks for prompts and menus and triggers appropriate action selection commands.

(ns anbf.scraper
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
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
  [frame text]
  (.endsWith (before-cursor frame) text))

(defn- status-drawn?
  "Does the status line look fully drawn? Presumes there are no menus in the frame. Probably not 100% reliable."
  [frame]
  (let [last-line (nth-line frame 23)
        name-line (nth-line frame 22)]
    (and (< (:cursor-y frame) 22)
         (re-seq #" T:[0-9]+ " last-line)
         ; status may overflow
         (or (not= \space (nth name-line 78))
             (not= \space (nth name-line 79))
             (re-seq #" S:[0-9]+" name-line)))))

(defn- handle-game-start
  [frame delegator]
  (when (and (.startsWith (nth-line frame 1) "NetHack, Copyright")
             (before-cursor? frame "] "))
    (condp #(.startsWith %2 %1) (cursor-line frame)
      "There is already a game in progress under your name."
      (write @delegator "y\n") ; destroy old game
      "Shall I pick a character"
      (choose-character @delegator)
      true)))

(defn- menu?
  "Is there a menu drawn onscreen?"
  [frame]
  ;TODO
  )

(defn- prompt
  [frame]
  "If there is a single-letter prompt active, return the prompt text, else nil."
  (re-seq #"\? \[[^\]]\]" (cursor-line frame)))

(defn- handle-prompt
  [frame delegator]
  (when-let [text (prompt frame)]
    (throw (UnsupportedOperationException. "TODO prompt - implement me")))) ; TODO

(defn- more-prompt
  "Returns the whole text before a --More-- prompt, or nil if there is none."
  [frame]
  (when (before-cursor? frame "--More--")
    (-> (:lines frame)
        (nth 0)
        string/trim
        (string/replace-first #"--More--" ""))))

(defn- handle-more
  [frame delegator]
  (when-let [text (more-prompt frame)]
    (message @delegator text)
    (write @delegator " ")))

(defn- handle-direction
  [frame delegator]
  (when (and (zero? (:cursor-y frame))
             (before-cursor? frame "In what direction?"))
    ; TODO
    (throw (UnsupportedOperationException. "TODO direction prompt - implement me"))))

(defn scraper [{:keys [delegator] :as anbf}]
  (reify RedrawHandler
    (redraw [this frame]
      (log/info "scraping frame")
      (or (handle-game-start frame delegator)
          (handle-more frame delegator)
          (handle-prompt frame delegator)
          (handle-direction frame delegator))
      (log/info "expecting further redraw")
      )))

(defn replace-scraper [anbf new-scraper]
  (replace-handler anbf @(:scraper anbf) new-scraper))
