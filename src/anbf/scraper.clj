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
    (and (< (-> frame :cursor :y) 22)
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
  (if (and (status-drawn? frame) (<= (-> frame :cursor :y) 1))
    (first (first (re-seq #".*\? \[[^\]]+\] (\(.\) )?$"
                          (before-cursor frame))))))

(defn- more-prompt? [frame]
  (before-cursor? frame "--More--"))

(defn- more-prompt
  "Returns the whole text before a --More-- prompt, or nil if there is none."
  [frame]
  (when (more-prompt? frame)
    (-> (:lines frame)
        (nth 0) ; TODO handle non-topline --More--, possibly concatenate lines (long engravings cause multiline messages)
        string/trim
        (string/replace-first #"--More--" ""))))

(defn- location-prompt? [frame]
  (let [topline (topline frame)]
    (or (re-seq #"^Unknown direction: ''' (use hjkl or \.)" topline)
        (re-seq #"(^ *|  ) (.*?)  \(For instructions type a \?\) *$" topline))))

(defn- prompt
  [frame]
  (when (and (<= (-> frame :cursor :y) 1)
             (before-cursor? frame "##'"))
    (let [prompt-end (subs (cursor-line frame) 0 (- (-> frame :cursor :x) 4))]
      (if (pos? (-> frame :cursor :y))
        (str (string/trim topline) " " prompt-end)
        prompt-end))))

(defn- prompt-fn [msg]
  (throw (UnsupportedOperationException. "TODO prompt-fn - implement me"))
  ; TODO
;    qr/^For what do you wish\?/         => 'wish',
;    qr/^What do you want to add to the (?:writing|engraving|grafitti|scrawl|text) (?:     in|on|melted into) the (.*?) here\?/ => 'write_what',
;    qr/^"Hello stranger, who are you\?"/ => 'vault_guard',
;    qr/^How much will you offer\?/      => 'donate',
;    qr/^What monster do you want to genocide\?/ => 'genocide_species',
;    qr/^What class of monsters do you wish to genocide\?/ => 'genocide_class',
  )

(defn- choice-fn [msg]
  (cond
    (re-seq #"^Really attack (.*?)\?" msg) really-attack
    :default (throw (UnsupportedOperationException.
                      (str "unimplemented choice prompt: " msg)))))

(defn- game-over? [frame]
  (re-seq #"^Do you want your possessions identified\?|^Die\?|^Really quit\?|^Do you want to see what you had when you died\?"
          (topline frame)))

(defn- goodbye? [frame]
  (and (more-prompt? frame)
       (re-seq #"^(Fare thee well|Sayonara|Aloha|Farvel|Goodbye|Be seeing you) "
               (topline frame))))

(defn- game-beginning? [frame]
  (and (.startsWith (nth-line frame 1) "NetHack, Copyright")
       (before-cursor? frame "] ")))

(def ^:private botl1-re #"^(\w+)?.*?St:(\d+(?:\/(?:\*\*|\d+))?) Dx:(\d+) Co:(\d+) In:(\d+) Wi:(\d+) Ch:(\d+)\s*(\w+)\s*(?:S:(\d+))?.*$" )

(def ^:private botl2-re #"^(Dlvl:\d+|Home \d+|Fort Ludios|End Game|Astral Plane)\s+(?:\$|\*):(\d+)\s+HP:(\d+)\((\d+)\)\s+Pw:(\d+)\((\d+)\)\s+AC:([0-9-]+)\s+(?:Exp|Xp|HD):(\d+)(?:\/(\d+))?\s+T:(\d+)\s+(.*?)\s*$")

(defn- parse-botls [[botl1 botl2]]
  ;(log/debug "parsing botl:\n" botl1 "\n" botl2)
  (merge
    (if-let [status (re-first-groups botl1-re botl1)]
      {:nickname (status 0)
       :stats (zipmap [:str :dex :con :int :wis :cha] (subvec status 1 7))
       :alignment (-> (status 7) string/lower-case keyword)
       :score (some-> (status 8) Integer/parseInt)}
      (log/error "failed to parse botl1 " botl1))
    (if-let [status (re-first-groups botl2-re botl2)]
      ; TODO state, burden
      (zipmap [:dlvl :gold :hp :maxhp :pw :maxpw :ac :xplvl :xp :turn]
              (conj (map #(if % (Integer/parseInt %)) (subvec status 1 10))
                    (status 0)))
      (log/error "failed to parse botl2 " botl2))
    (condp #(.contains %2 %1) botl2
      " Sat" {:hunger :satiated}
      " Hun" {:hunger :hungry}
      " Wea" {:hunger :weak}
      " Fai" {:hunger :fainting}
      {:hunger :normal})))

(defn- emit-botl [frame delegator]
  (->> frame botls parse-botls (send delegator botl)))

(defn new-scraper [delegator & [mark-kw]]
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
              (emit-botl frame delegator)
              ; TODO prompt may re-appear in lastmsg+action as topline msg
              ; TODO after-choice-prompt scraper state to catch exceptions (without handle-more)? ("You don't have that object", "You don't have anything to XXX") - zpusobi i znacka!
              (send delegator (choice-fn text) text)))
          (handle-more [frame]
            (when-let [text (more-prompt frame)]
              (log/debug "Handling --More-- prompt")
              ; XXX TODO possibly update map and/or botl?
              ; TODO You wrest one last charge from the worn-out wand. => no-mark?
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
            (when (and (zero? (-> frame :cursor :x))
                       (before-cursor? frame "In what direction? "))
              (log/debug "Handling direction")
              (emit-botl frame delegator)
              (send delegator map-drawn frame)
              (throw (UnsupportedOperationException. "TODO direction prompt - implement me"))))
          (handle-location [frame]
            (when (location-prompt? frame)
              (log/debug "Handling location")
              (emit-botl frame delegator)
              (send delegator map-drawn frame)
              ; TODO new state to stop repeated botl/map updates while the prompt is active, also to stop multiple commands
              (throw (UnsupportedOperationException. "TODO location prompt - implement me"))))
          (handle-prompt [frame]
            (when-let [msg (prompt frame)]
              (emit-botl frame delegator)
              (send delegator write (str (repeat 3 backspace)))
              (send delegator (prompt-fn msg) msg)))
          (handle-game-end [frame]
            ; TODO reg handler for escaping the inventory menu?
            (cond (game-over? frame) (send delegator write \y)
                  (goodbye? frame) (-> delegator
                                       (send write \space)
                                       (send ended))))

          (initial [frame]
            (or (handle-game-start frame)
                (handle-game-end frame)
                (handle-more frame)
                (handle-menu frame)
                (handle-choice-prompt frame)
                ;(handle-direction frame) ; XXX TODO (nevykresleny) handle-direction se zrusi pri ##
                (handle-location frame)
                ; pokud je vykresleny status, nic z predchoziho nesmi invazivne reagovat na "##"
                (when (status-drawn? frame)
                  ;(log/debug "writing ##' mark")
                  (send delegator write "##'")
                  marked)
                (log/debug "expecting further redraw")))
          ; TODO v kontextech akci kde ##' muze byt destruktivni (direction prompt - kick,wand,loot,talk...) cekam dokud se neobjevi neco co prokazatelne neni zacatek direction promptu, pak poslu znacku.
          (no-mark [frame]
            (or ; TODO (undrawn-direction? frame)
                (handle-direction frame)
                (log/debug "no-mark - not direction prompt")
                initial))
          ; odeslal jsem marker, cekam jak se vykresli
          (marked [frame]
            ; veci co se daji bezpecne potvrdit pomoci ## muzou byt jen tady, ve druhem to muze byt zkratka, kdyz se vykresleni stihne - pak se ale hur odladi spolehlivost tady
            ; tady (v obou scraperech) musi byt veci, ktere se nijak nezmeni pri ##'
            (or (handle-game-end frame)
                (handle-more frame)
                (handle-menu frame)
                (handle-choice-prompt frame)
                (handle-prompt frame)
                (handle-location frame)
                (when (and (= 0 (-> frame :cursor :y))
                           (before-cursor? frame "# #'"))
                  (send delegator write (str backspace \newline \newline))
                  lastmsg-clear)
                (log/debug "marked expecting further redraw")))
          (lastmsg-clear [frame]
            (when (topline-empty? frame)
              (send delegator write (str (ctrl \p) (ctrl \p)))
              lastmsg-get))
          ; jakmile je vykresleno "# #" na topline, mam jistotu, ze po dalsim ctrl+p se vykresli posledni herni zprava (nebo "#", pokud zadna nebyla)
          (lastmsg-get [frame]
            (when (re-seq #"^# # +" (topline frame))
              (send delegator write (str (ctrl \p)))
              lastmsg+action))
          ; cekam na vysledek <ctrl+p>, bud # z predchoziho kola nebo presmahnuta message
          (lastmsg+action [frame]
            (or (when-not (or (= 0 (-> frame :cursor :y))
                              (topline-empty? frame)
                              (.startsWith (topline frame) "# # "))
                  (if-not (re-seq #"^# +" (topline frame))
                    (send delegator message (string/trim (topline frame)))
                    #_ (log/debug "no last message"))
                  (emit-botl frame delegator)
                  (send delegator map-drawn frame)
                  (send delegator full-frame frame)
                  initial)
                (log/debug "lastmsg expecting further redraw")))]
    (if (= mark-kw :no-mark)
      no-mark
      initial)))

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
      (dosync (alter scraper apply-scraper delegator frame))
      #_ (->> (dosync (alter scraper apply-scraper delegator frame))
              type
              (log/debug "next scraper:")))))
; TODO handler co switchne na no-mark u nekterych akci
