(ns anbf.scraper
  "The screen scraper handles redraw events, tries to determine when the frame is completely drawn and sends off higher-level events.  It looks for prompts and menus and triggers appropriate action selection commands."
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [anbf.util :refer :all]
            [anbf.frame :refer :all]
            [anbf.delegator :refer :all]))

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

(defn- menu-head
  "Return menu title"
  [frame]
  (if (not-any? inverse? (nth (:colors frame) 0))
    (topline frame)))

(defn- menu-page
  "Return [first last] page of a menu, if there is one displayed."
  [frame]
  (condp re-seq (before-cursor frame)
    #"\(end\) $" [1 1]
    #"\(([0-9]+) of ([0-9]+)\)$" :>> #(-> % first (subvec 1))
    nil))

(defn- menu-end?
  "Is this the last page of a menu?"
  [frame]
  (if-let [[f l] (menu-page frame)]
    (= f l)))

(defn- menu?
  "Is there a menu drawn onscreen?"
  [frame]
  (some? (menu-page frame)))

(defn- menu-line
  "Return [character menu-item] pair for the menu line or nil for headers etc."
  [start line colors]
  (if-not (inverse? (nth colors start))
    (re-first-groups #"^(.)  ?[-+#] (.*?)\s*$" (subs line start))))

(defn- menu-options
  "Return map of menu options (current page)"
  [frame]
  (let [xstart (->> (nth-line frame 0) (re-seq #"^ *") first count)
        yend (-> frame :cursor :y)]
    (into {} (map #(menu-line xstart %1 %2)
                  (take yend (:lines frame))
                  (take yend (:colors frame))))))

(defn- menu-fn [head]
  (condp re-seq head
    (throw (UnsupportedOperationException. (str "Unknown menu " head)))))

(defn- choice-prompt
  "If there is a single-letter prompt active, return the prompt text, else nil."
  [frame]
  (if (and (status-drawn? frame) (<= (-> frame :cursor :y) 1))
    (first (first (re-seq #".*\?\"? \[[^\]]+\] (\(.\) )?$"
                          (before-cursor frame))))))

(defn- more-prompt? [frame]
  (before-cursor? frame "--More--"))

(defn- more-items [frame]
  (let [xstart (->> (nth-line frame 0) (take-while #(= \space %)) count)
        yend (-> frame :cursor :y)]
    (map #(-> % (subs xstart) string/trim)
         (take yend (:lines frame)))))

(defn- more-list-prompt? [frame]
  (let [ycursor (-> frame :cursor :y)]
    (or (and (more-prompt? frame) (< 1 ycursor))
        (and (pos? ycursor) (= " --More--" (before-cursor frame))))))

(defn- more-list [frame]
  (if (more-list-prompt? frame)
    (more-items frame)))

(defn- more-prompt
  "Returns the whole text before a --More-- prompt, or nil if there is none."
  [frame]
  (when (more-prompt? frame)
    (string/replace (topline+ frame) #"--More--" "")))

(defn- location-prompt? [frame]
  (let [topline (topline frame)]
    (or (re-seq #"^Unknown direction: ''' \(use hjkl or \.\)" topline)
        (re-seq #"\(For instructions type a \?\)$" topline))))

(defn- prompt
  [frame]
  (when (and (<= (-> frame :cursor :y) 1)
             (before-cursor? frame "##'"))
    (subs (topline+ frame) 0 (- (-> frame :cursor :x) 4))))

(defn- prompt-fn [msg]
  (condp re-seq msg
    #"Call .*:" call-object
    (throw (UnsupportedOperationException. "TODO prompt-fn - implement me")))
  ; TODO
;    qr/^For what do you wish\?/         => 'wish',
;    qr/^What do you want to add to the (?:writing|engraving|grafitti|scrawl|text) (?:     in|on|melted into) the (.*?) here\?/ => 'write_what',
;    qr/^"Hello stranger, who are you\?"/ => 'vault_guard',
;    qr/^How much will you offer\?/      => 'donate',
;    qr/^What monster do you want to genocide\?/ => 'genocide_species',
;    qr/^What class of monsters do you wish to genocide\?/ => 'genocide_class',
  )

(defn- choice-fn [msg]
  (condp re-first-group msg
    #"^Shall I remove your|^Take off your |let me run my fingers" remove-equip
    #"^Force the gods to be pleased\?" force-god
    #"^Really attack .*\?" really-attack
    #"^Are you sure you want to enter\?" enter-gehennom
    #"^Die\?" die
    #"^Do you want to keep the save file\?" keep-save
    (throw (UnsupportedOperationException.
             (str "unimplemented choice prompt: " msg)))))

(defn- game-over? [frame]
  (re-seq #"^Do you want your possessions identified\?|^Really quit\?|^Do you want to see what you had when you died\?"
          (topline frame)))

(defn- goodbye? [frame]
  (and (more-prompt? frame)
       (not (re-seq #" level \d+" (topline frame))) ; Sayonara level 10 => not game end
       (not (re-seq #"welcome .* NetHack" (topline frame))) ; game start
       (re-seq #"^(Fare thee well|Sayonara|Aloha|Farvel|Goodbye|Be seeing you) "
               (topline frame))))

(defn- game-beginning? [frame]
  (and (.startsWith ^String (nth-line frame 1) "NetHack, Copyright")
       (before-cursor? frame "] ")))

(def ^:private botl1-re #"^(\w+)?.*?St:(\d+(?:\/(?:\*\*|\d+))?) Dx:(\d+) Co:(\d+) In:(\d+) Wi:(\d+) Ch:(\d+)\s*(\w+)\s*(?:S:(\d+))?.*$" )

(def ^:private botl2-re #"^(Dlvl:\d+|Home \d+|Fort Ludios|End Game|Astral Plane)\s+(?:\$|\*):(\d+)\s+HP:(\d+)\((\d+)\)\s+Pw:(\d+)\((\d+)\)\s+AC:([0-9-]+)\s+(?:Exp|Xp|HD):(\d+)(?:\/(\d+))?\s+T:(\d+)\s+(.*?)\s*$")

(defn- parse-botls [[botl1 botl2]]
  (merge
    (if-let [status (re-first-groups botl1-re botl1)]
      {:nickname (status 0)
       :stats (zipmap [:str :dex :con :int :wis :cha] (subvec status 1 7))
       :alignment (-> (status 7) string/lower-case keyword)
       :score (some-> (status 8) Integer/parseInt)}
      (log/error "failed to parse botl1 " botl1))
    (if-let [status (re-first-groups botl2-re botl2)]
      (zipmap [:dlvl :gold :hp :maxhp :pw :maxpw :ac :xplvl :xp :turn]
              (conj (map #(if % (Integer/parseInt %)) (subvec status 1 10))
                    (status 0)))
      (log/error "failed to parse botl2 " botl2))
    {:state (reduce #(if (.contains ^String botl2 (key %2))
                       (conj %1 (val %2))
                       %1)
                    #{}
                    {" Bl" :blind " Stun" :stun " Conf" :conf
                     " Foo" :ill " Ill" :ill " Hal" :hallu})
     :burden (condp #(.contains ^String %2 %1) botl2
               " Overl" :overloaded
               " Overt" :overtaxed
               " Stra" :strained
               " Stre" :stressed
               " Bur" :burdened
               nil)
     :hunger (condp #(.contains ^String %2 %1) botl2
               " Sat" :satiated
               " Hun" :hungry
               " Wea" :weak
               " Fai" :fainting
               nil)}))

(defn- emit-botl [delegator frame]
  (->> frame botls parse-botls (send delegator botl)))

(defn- flush-more-list [delegator items]
  (when-not (nil? @items)
    (log/debug "Flushing --More-- list")
    (send delegator message-lines @items)
    (ref-set items nil)))

(defn new-scraper [delegator & [mark-kw]]
  (let [player (ref nil)
        head (ref nil)
        items (ref nil)]
    (letfn [(handle-game-start [frame]
              (when (game-beginning? frame)
                (log/debug "Handling game start")
                (condp #(.startsWith ^String %2 %1) (cursor-line frame)
                  "There is already a game in progress under your name."
                  (send delegator write "y\n") ; destroy old game
                  "Shall I pick a character"
                  (send delegator choose-character)
                  true)))
            (handle-choice-prompt [frame]
              (when-let [text (choice-prompt frame)]
                (log/debug "Handling choice prompt")
                (emit-botl delegator frame)
                ; TODO prompt may re-appear in lastmsg+action as topline msg
                ; TODO after-choice-prompt scraper state to catch exceptions (without handle-more)? ("You don't have that object", "You don't have anything to XXX") - zpusobi i znacka!
                (send delegator (choice-fn text) text)))
            (handle-more [frame]
              (or (when-let [item-list (more-list frame)]
                    (log/debug "Handling --More-- list")
                    (if (nil? @items)
                      (ref-set items []))
                    (alter items into item-list)
                    ; message about a feature that would normally appear as topline message may become part of a list when there are items on the tile
                    (when (and (empty? (nth @items 1))
                               (not (.endsWith ^String (nth @items 0) ":")))
                      (send delegator message (nth @items 0))
                      (alter items subvec 2))
                    (send delegator write " ")
                    initial)
                  (when-let [text (more-prompt frame)]
                    (log/debug "Handling --More-- prompt")
                    ; TODO You wrest one last charge from the worn-out wand. => no-mark?
                    (let [res (case text
                                "You don't have that object."
                                handle-choice-prompt
                                "To what position do you want to be teleported?"
                                location
                                (do (send delegator message text) initial))]
                      (send delegator write " ")
                      res))))
            (handle-menu [frame]
              (when (menu? frame)
                (log/debug "Handling menu")
                (when (nil? @items)
                  (ref-set head (menu-head frame))
                  (ref-set items {}))
                (alter items merge (menu-options frame))
                (if-not (menu-end? frame)
                  (send delegator write " ")
                  (do (log/debug "At menu end")
                      (if @head
                        (send delegator (menu-fn @head) @items)
                        (do (send delegator inventory-list @items)
                            (send delegator write " ")))
                      (ref-set items nil)
                      initial))))
            (handle-direction [frame]
              (when (and (zero? (-> frame :cursor :x))
                         (before-cursor? frame "In what direction? "))
                (log/debug "Handling direction")
                (emit-botl delegator frame)
                (throw (UnsupportedOperationException. "TODO direction prompt - implement me"))))
            (handle-prompt [frame]
              (when-let [msg (prompt frame)]
                (emit-botl delegator frame)
                (send delegator write (apply str (repeat 3 backspace)))
                (send delegator (prompt-fn msg) msg)
                initial))
            (handle-game-end [frame]
              (cond (game-over? frame) (send delegator write \y)
                    (goodbye? frame) (-> delegator
                                         (send write \space)
                                         (send ended))))

            (sink [frame] ; for hallu corner-case, discard insignificant extra redraws (cursor stopped on player while the bottom of the map isn't hallu-updated)
              (log/debug "sink discarding redraw"))
            (initial [frame]
              (or (handle-game-start frame)
                  (handle-game-end frame)
                  (handle-more frame)
                  (handle-menu frame)
                  (handle-choice-prompt frame)
                  ;(handle-direction frame) ; XXX TODO (nevykresleny) handle-direction se zrusi pri ##
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
                  (when (and (= 0 (-> frame :cursor :y))
                             (before-cursor? frame "# #'"))
                    (send delegator write (str backspace \newline \newline))
                    lastmsg-clear)
                  (log/debug "marked expecting further redraw")))
            (lastmsg-clear [frame]
              (when (empty? (topline frame))
                (send delegator write (str (ctrl \p) (ctrl \p)))
                lastmsg-get))
            (lastmsg-get [frame]
              (when (and (= "# #" (topline frame))
                         (< (-> frame :cursor :y) 22))
                (ref-set player (:cursor frame))
                (send delegator write (str (ctrl \p)))
                lastmsg+action))
            (lastmsg+action [frame]
              (or (when (and (more-prompt? frame) (= 1 (-> frame :cursor :y)))
                    (send delegator write "\n##\n\n")
                    lastmsg-clear)
                  (if (= "# #" (topline frame))
                    (ref-set player (:cursor frame)))
                  (when (= (:cursor frame) @player)
                    (if-not (.startsWith ^String (topline frame) "#")
                      (send delegator message (topline frame))
                      #_ (log/debug "no last message"))
                    (emit-botl delegator frame)
                    (send delegator know-position frame)
                    (flush-more-list delegator items)
                    (send delegator full-frame frame)
                    sink)
                  (log/debug "lastmsg expecting further redraw")))
            (location [frame]
              (or (when (location-prompt? frame)
                    (log/debug "Handling location")
                    (emit-botl delegator frame)
                    (send delegator know-position frame)
                    (flush-more-list delegator items)
                    (send delegator write \<) ; to stop repeated botl/map updates while the prompt is active causing multiple commands
                    (send delegator teleport-where)
                    initial)
                  (log/debug "location expecting further redraw")))]
      (if (= mark-kw :no-mark)
        no-mark
        initial))))

(defn- apply-scraper
  "If the current scraper returns a function when applied to the frame, the function becomes the new scraper, otherwise the current scraper remains.  A fresh scraper is created and applied if the current scraper is nil."
  [current-scraper delegator frame]
  (let [next-scraper ((or current-scraper (new-scraper delegator)) frame)]
    (if (fn? next-scraper)
      next-scraper
      current-scraper)))

(defn scraper-handler [scraper delegator]
  (reify
    ActionChosenHandler
    (action-chosen [_ _]
      (dosync (ref-set scraper nil))) ; escape sink
    RedrawHandler
    (redraw [_ frame]
      #_(dosync (alter scraper apply-scraper delegator frame))
      (->> (dosync (alter scraper apply-scraper delegator frame))
              type
              (log/debug "next scraper:")))))
; TODO handler co switchne na no-mark u nekterych akci
