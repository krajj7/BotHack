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

(defn- topline [frame]
  (nth-line frame 0))

(defn- topline-empty? [frame]
  (re-seq #"^ +$" (topline frame)))

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
    (log/debug "Handling game start")
    (condp #(.startsWith %2 %1) (cursor-line frame)
      "There is already a game in progress under your name."
      (write @delegator "y\n") ; destroy old game
      "Shall I pick a character"
      (choose-character @delegator)
      true)))

(defn- menu?
  "Is there a menu drawn onscreen?"
  [frame]
  (re-seq #"\(end\) $|\([0-9]+ of [0-9]+\)$" (before-cursor frame)))

(defn- choice-prompt
  [frame]
  "If there is a single-letter prompt active, return the prompt text, else nil."
  (if (<= (:cursor-y frame) 1)
    (re-seq #"\? \[[^\]]\] " (before-cursor frame))))

(defn- handle-choice-prompt
  [frame delegator]
  (when-let [text (choice-prompt frame)]
    (log/debug "Handling choice prompt")
    (throw (UnsupportedOperationException. "TODO choice prompt - implement me")))) ; TODO

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
    (log/debug "Handling --More-- prompt")
    ; XXX TODO possibly update map and/or botl?
    (message @delegator text)
    (write @delegator " ")))

(defn- handle-menu
  [frame handler]
  (when (menu? frame)
    (log/debug "Handling menu")
    (throw (UnsupportedOperationException. "TODO menu - implement me"))))

(defn- handle-direction
  [frame delegator]
  (when (and (zero? (:cursor-y frame))
             (before-cursor? frame "In what direction? "))
    (log/debug "Handling direction")
    (throw (UnsupportedOperationException. "TODO direction prompt - implement me"))))

(defn- location-prompt? [frame]
  (re-seq #"(^ *|  ) (.*?)  \(For instructions type a \?\) *$"
          (topline frame)))

(defn- handle-location
  [frame delegator]
  (when (location-prompt? frame)
    (log/debug "Handling location")
    (throw (UnsupportedOperationException. "TODO location prompt - implement me"))))

(defn- handle-last-message
  [frame delegator]
  (let [msg (-> frame topline string/trim)]
    (when-not (= msg "# #'")
      (message @delegator msg))))

(defn new-scraper [{:keys [delegator] :as anbf}]
  (letfn [(initial [frame]
            (log/debug "scraping frame")
            (or (handle-game-start frame delegator)
                (handle-more frame delegator)
                (handle-menu frame delegator)
                ;(handle-direction frame delegator) ; XXX TODO (nevykresleny) handle-direction se zrusi pri ##
                (handle-location frame delegator)
                ; pokud je vykresleny status, nic z predchoziho nesmi nezotavitelne/nerozpoznatelne reagovat na "##"
                (when (status-drawn? frame)
                  (log/debug "writing ##' mark")
                  (write @delegator "##'")
                  marked)
                (log/debug "expecting further redraw")))
          ; odeslal jsem marker, cekam jak se vykresli
          (marked [frame]
            (log/debug "marked scraping frame")
            ; veci co se daji bezpecne potvrdit pomoci ## muzou byt jen tady, ve druhem to muze byt zkratka, kdyz se vykresleni stihne - pak se ale hur odladi spolehlivost tady
            ; tady (v obou scraperech) musi byt veci, ktere se nijak nezmeni pri ##'
            (or (handle-more frame delegator)
                (handle-menu frame delegator)
                (when (and (= 0 (:cursor-y frame))
                           (before-cursor? frame "# #'"))
                  (write @delegator (str backspace \newline \newline))
                  (log/debug "persisted mark")
                  lastmsg-clear)
                ; TODO recognize prompt
                ; moznosti: topl=" xxxxxx? ##'" => prompt
                ;           You don't have that object.--More-- => choice
                ;           "Unknown direction: ''' (use hjkl or .)." => location prompt
                (log/debug "marked expecting further redraw")))
          (lastmsg-clear [frame]
            (log/debug "scanning for cancelled mark")
            (when (topline-empty? frame)
              (log/debug "ctrl+p ctrl+p")
              (write @delegator (str (ctrl \p) (ctrl \p)))
              lastmsg-get))
          ; jakmile je vykresleno "# #" na topline, mam jistotu, ze po dalsim ctrl+p se vykresli posledni herni zprava (nebo "#", pokud zadna nebyla)
          (lastmsg-get [frame]
            (log/debug "scanning ctrl+p")
            (when (re-seq #"^# # +" (topline frame))
              (log/debug "got second ctrl+p, sending last ctrl+p")
              (write @delegator (str (ctrl \p)))
              lastmsg+action))
          ; cekam na vysledek <ctrl+p>, bud # z predchoziho kola nebo presmahnuta message
          (lastmsg+action [frame]
            (log/debug "scanning for last message")
            (or (when-not (or (= (:cursor-y frame) 0)
                              (topline-empty? frame))
                  (if-not (re-seq #"^# +" (topline frame))
                    (message @delegator (string/trim (topline frame)))
                    (log/debug "no last message"))
                  (log/debug "publishing full frame")
                  (full-frame @delegator frame)
                  (choose-action @delegator)
                  initial)
                (log/debug "lastmsg expecting further redraw")))]
    initial))

(defn- switch-scraper [anbf current-scraper next-scraper]
  (if-not (compare-and-set! (:scraper anbf)
                            current-scraper next-scraper)
    (throw (IllegalStateException. "scraper was changed by some other thread during a redraw event handling, this should never happen"))
    next-scraper))

(defn scraper-handler [anbf]
  (reify RedrawHandler
    (redraw [_ frame]
      (let [current-scraper (or @(:scraper anbf)
                                (switch-scraper anbf nil (new-scraper anbf)))
            next-scraper (current-scraper frame)]
        (if (fn? next-scraper)
          (switch-scraper anbf current-scraper next-scraper))))))
