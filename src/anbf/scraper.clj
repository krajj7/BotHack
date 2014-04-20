; The screen scraper handles redraw events, tries to determine when the frame is completely drawn and sends off higher-level events.  It looks for prompts and menus and triggers appropriate action selection commands.

(ns anbf.scraper
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [anbf.util :refer :all]
            [anbf.frame :refer :all]
            [anbf.delegator :refer :all]))

(defn- topline-empty? [frame]
  (re-seq #"^ +$" (topline frame)))

(defn- status-drawn?
  "Does the status line look fully drawn? Presumes there are no menus in the frame."
  [frame]
  (let [last-line (nth-line frame 23)
        name-line (nth-line frame 22)]
    (and (< (:cursor-y frame) 22)
         (re-seq #" T:[0-9]+ " last-line)
         ; status may overflow
         (or (not= \space (nth name-line 78))
             (not= \space (nth name-line 79))
             (re-seq #" S:[0-9]+" name-line)))))

(defn- menu?
  "Is there a menu drawn onscreen?"
  [frame]
  (re-seq #"\(end\) $|\([0-9]+ of [0-9]+\)$" (before-cursor frame)))

(defn- choice-prompt
  "If there is a single-letter prompt active, return the prompt text, else nil."
  [frame]
  (if (and (status-drawn? frame) (<= (:cursor-y frame) 1))
    (re-seq #".*\? \[[^\]]+\] (\(.\) )?$" (before-cursor frame))))

(defn- more-prompt? [frame]
  (before-cursor? frame "--More--"))

(defn- more-prompt
  "Returns the whole text before a --More-- prompt, or nil if there is none."
  [frame]
  (when (more-prompt? frame)
    (-> (:lines frame)
        (nth 0) ; TODO handle non-topline --More--
        string/trim
        (string/replace-first #"--More--" ""))))

(defn- location-prompt? [frame]
  (let [topline (topline frame)]
    (or (re-seq #"^Unknown direction: ''' (use hjkl or \.)" topline)
        (re-seq #"(^ *|  ) (.*?)  \(For instructions type a \?\) *$" topline))))

(defn- prompt
  [frame]
  (when (and (<= (:cursor-y frame) 1)
             (before-cursor? frame "##'"))
    (let [prompt-end (subs (cursor-line frame) 0 (- (:cursor-x frame) 4))]
      (if (pos? (:cursor-y frame))
        (str (string/trim topline) " " prompt-end)
        prompt-end))))

(defn- prompt-fn
  [msg]
  (throw (UnsupportedOperationException. "TODO prompt-fn - implement me"))
  ; TODO
;    qr/^For what do you wish\?/         => 'wish',
;    qr/^What do you want to add to the (?:writing|engraving|grafitti|scrawl|text) (?:     in|on|melted into) the (.*?) here\?/ => 'write_what',
;    qr/^"Hello stranger, who are you\?"/ => 'vault_guard',
;    qr/^How much will you offer\?/      => 'donate',
;    qr/^What monster do you want to genocide\?/ => 'genocide_species',
;    qr/^What class of monsters do you wish to genocide\?/ => 'genocide_class',
  )

(defn- game-over? [frame]
  (and (status-drawn? frame)
       (re-seq #"^Do you want your possessions identified\?|^Die\?|^Really quit\?|^Do you want to see what you had when you died\?"
               (topline frame))))

(defn- goodbye? [frame]
  (and (more-prompt? frame)
       (re-seq #"^(Fare thee well|Sayonara|Aloha|Farvel|Goodbye|Be seeing you) "
               (topline frame))))

(defn- game-beginning? [frame]
  (and (.startsWith (nth-line frame 1) "NetHack, Copyright")
       (before-cursor? frame "] ")))

(def ^:private botl1-re #"^(\w+)?.*?St:(\d+(?:\/(?:\*\*|\d+))?) Dx:(\d+) Co:(\d+) In:(\d+) Wi:(\d+) Ch:(\d+)\s*(\w+)\s*(.*)$" )

(def ^:private botl2-re #"^(Dlvl:\d+|Home \d+|Fort Ludios|End Game|Astral Plane)\s+(?:\$|\*):(\d+)\s+HP:(\d+)\((\d+)\)\s+Pw:(\d+)\((\d+)\)\s+AC:([0-9-]+)\s+(?:Exp|Xp|HD):(\d+)(?:\/(\d+))?\s+T:(\d+)\s+(.*?)\s*$")

(defn- parse-botls [[botl1 botl2]]
  (log/debug "parsing botl:\n" botl1 "\n" botl2)
  (merge
    (if-let [status (re-seq botl1-re botl1)]
      {} ; TODO parse, return same keys as in Player
      (log/error "failed to parse botl2 " botl2))
    (if-let [status (re-seq botl2-re botl2)]
      {} ; TODO parse, return same keys as in Player
      (log/error "failed to parse botl2 " botl2))
    (condp #(.contains %2 %1) botl2
      " Sat" {:hunger :satiated}
      " Hun" {:hunger :hungry}
      " Wea" {:hunger :weak}
      " Fai" {:hunger :fainting}
      {:hunger :normal})))

(defn new-scraper [delegator]
  (letfn [(handle-game-start [frame]
            (when (game-beginning? frame)
              (log/debug "Handling game start")
              (condp #(.startsWith %2 %1) (cursor-line frame)
                "There is already a game in progress under your name."
                (send delegator write "y\n") ; destroy old game
                "Shall I pick a character"
                (send delegator choose-character)
                true)))
          (handle-choice-prompt [frame]
            (when-let [text (choice-prompt frame)]
              (log/debug "Handling choice prompt")
              (handle-botl frame)
              ; TODO maybe will need extra newline to push response away from ctrl-p message handler
              ; TODO after-choice-prompt scraper state to catch exceptions (without handle-more)? ("You don't have that object", "You don't have anything to XXX")
              (throw (UnsupportedOperationException. "TODO choice prompt - implement me")))) ; TODO
          (handle-more [frame]
            (when-let [text (more-prompt frame)]
              (log/debug "Handling --More-- prompt")
              ; XXX TODO possibly update map and/or botl?
              (let [res (if (= text "You don't have that object.")
                          handle-choice-prompt
                          (do (send delegator message text) initial))]
                (send delegator write " ")
                res)))
          (handle-menu [frame]
            (when (menu? frame)
              (log/debug "Handling menu")
              (send delegator write " ")
              #_ (throw (UnsupportedOperationException. "TODO menu - implement me"))))
          (handle-direction [frame]
            (when (and (zero? (:cursor-y frame))
                       (before-cursor? frame "In what direction? "))
              (log/debug "Handling direction")
              (handle-botl frame)
              (send delegator map-drawn frame)
              (throw (UnsupportedOperationException. "TODO direction prompt - implement me"))))
          (handle-location [frame]
            (when (location-prompt? frame)
              (log/debug "Handling location")
              (handle-botl frame)
              (send delegator map-drawn frame)
              ; TODO new state to stop repeated botl/map updates while the prompt is active
              (throw (UnsupportedOperationException. "TODO location prompt - implement me"))))
          (handle-last-message [frame]
            (let [msg (-> frame topline string/trim)]
              (when-not (= msg "# #'")
                (send delegator message msg))))
          (handle-prompt [frame]
            (when-let [msg (prompt frame)]
              (handle-botl frame)
              (send delegator write (str (repeat 3 backspace)))
              (send delegator (prompt-fn msg) msg)))
          (handle-game-end [frame]
            ; TODO reg handler for escaping the inventory menu?
            (cond (game-over? frame) (do (handle-botl frame)
                                         (send delegator write \y))
                  (goodbye? frame) (-> delegator
                                       (send write \space)
                                       (send ended))))
          (handle-botl [frame]
            (->> frame botls parse-botls (send delegator botl)))

          (initial [frame]
            ;(log/debug "scraping frame")
            (or (handle-game-start frame)
                (handle-game-end frame)
                (handle-more frame)
                (handle-menu frame)
                (handle-choice-prompt frame)
                ;(handle-direction frame) ; XXX TODO (nevykresleny) handle-direction se zrusi pri ##
                (handle-location frame)
                ; pokud je vykresleny status, nic z predchoziho nesmi nezotavitelne/nerozpoznatelne reagovat na "##"
                (when (status-drawn? frame)
                  ;(log/debug "writing ##' mark")
                  (send delegator write "##'")
                  marked)
                (log/debug "expecting further redraw")))
          ; odeslal jsem marker, cekam jak se vykresli
          (marked [frame]
            ;(log/debug "marked scraping frame")
            ; veci co se daji bezpecne potvrdit pomoci ## muzou byt jen tady, ve druhem to muze byt zkratka, kdyz se vykresleni stihne - pak se ale hur odladi spolehlivost tady
            ; tady (v obou scraperech) musi byt veci, ktere se nijak nezmeni pri ##'
            (or (handle-game-end frame)
                (handle-more frame)
                (handle-menu frame)
                (handle-choice-prompt frame)
                (handle-prompt frame)
                (when (and (= 0 (:cursor-y frame))
                           (before-cursor? frame "# #'"))
                  (send delegator write (str backspace \newline \newline))
                  ;(log/debug "persisted mark")
                  lastmsg-clear)
                (log/debug "marked expecting further redraw")))
          (lastmsg-clear [frame]
            ;(log/debug "scanning for cancelled mark")
            (when (topline-empty? frame)
              ;(log/debug "ctrl+p ctrl+p")
              (send delegator write (str (ctrl \p) (ctrl \p)))
              lastmsg-get))
          ; jakmile je vykresleno "# #" na topline, mam jistotu, ze po dalsim ctrl+p se vykresli posledni herni zprava (nebo "#", pokud zadna nebyla)
          (lastmsg-get [frame]
            ;(log/debug "scanning ctrl+p")
            (when (re-seq #"^# # +" (topline frame))
              ;(log/debug "got second ctrl+p, sending last ctrl+p")
              (send delegator write (str (ctrl \p)))
              lastmsg+action))
          ; cekam na vysledek <ctrl+p>, bud # z predchoziho kola nebo presmahnuta message
          (lastmsg+action [frame]
            ;(log/debug "scanning for last message")
            (or (when-not (or (= (:cursor-y frame) 0)
                              (topline-empty? frame))
                  (if-not (re-seq #"^# +" (topline frame))
                    (send delegator message (string/trim (topline frame)))
                    #_ (log/debug "no last message"))
                  (handle-botl frame)
                  (send delegator map-drawn frame)
                  (send delegator full-frame frame)
                  initial)
                (log/debug "lastmsg expecting further redraw")))]
    initial))

(defn- apply-scraper
  "If the current scraper returns a function when applied to the frame, the function becomes the new scraper, otherwise the current scraper remains.  A fresh scraper is created and applied if the current scraper is nil."
  [current-scraper delegator frame]
  (let [next-scraper ((or current-scraper (new-scraper delegator)) frame)]
    (if (fn? next-scraper)
      next-scraper
      current-scraper)))

(defn scraper-handler [scraper delegator]
  (reify RedrawHandler
    (redraw [_ frame]
      (->> (dosync (alter scraper apply-scraper delegator frame))
           type
           (log/debug "next scraper:")))))
