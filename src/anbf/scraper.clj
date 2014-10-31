(ns anbf.scraper
  "The screen scraper handles redraw events, tries to determine when the frame is completely drawn and sends off higher-level events.  It looks for prompts and menus and triggers appropriate action selection commands."
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [anbf.util :refer :all]
            [anbf.frame :refer :all]
            [anbf.action :refer :all]
            [anbf.actions :refer :all]
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
  "Return [current last] page of a menu, if there is one displayed."
  [frame]
  (condp re-seq (before-cursor frame)
    #"\(end\) $" [1 1]
    #"\(([0-9]+) of ([0-9]+)\)$" :>> #(-> % first (subvec 1)
                                          ((partial mapv parse-int)))
    nil))

(defn- menu-curpage [frame] (nth (menu-page frame) 0))

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
    (if-let [[chr s] (re-first-groups #"^(.)  ?[-+#] (.*?)\s*$"
                                      (subs line start))]
      [(.charAt chr 0) s])))

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
    #"What do you wish to do\?" name-menu
    #"Pick up what\?" pick-up-what
    #"Put in what\?" put-in-what
    #"Take out what\?" take-out-what
    #"Loot which containers\?" loot-what
    (throw (UnsupportedOperationException. (str "Unknown menu " head)))))

(defn- multi-menu?
  "Do we need to confirm menu selections (=> true), or does single selection close the menu? (=> false)"
  [head]
  (re-seq #"What do you wish to do\?" head))

(defn- choice-prompt
  "If there is a single-letter prompt active, return the prompt text, else nil."
  [frame]
  (if (and (status-drawn? frame)
           (<= (-> frame :cursor :y) 1)
           (> (-> frame :cursor :x) ; no text after cursor
              (count (re-first-group #"^(.*[^ ]) *$" (cursor-line frame)))))
    (some->> (topline+ frame)
             (re-seq #".*\?\"?  ?\[[^\]]+\]( \(.\))?$")
             ffirst)))

(defn- more-prompt? [frame]
  (before-cursor? frame "--More--"))

(defn- more-items [frame]
  (let [xstart (max (- (-> frame :cursor :x) 9) 0)
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

(defn- location-fn [msg]
  (condp #(.startsWith %2 %1) msg
    "Where do you want to travel to?" travel-where
    "To what location" teleport-where
    "(For instructions type a ?)" teleport-where ; assuming there was a topline msg "To what position do you want to be teleported?--More--"
    (throw (UnsupportedOperationException.
             (str "unknown location message" msg)))))

(def ^:private location-re #"^Unknown direction: ''' \(use hjkl or \.\)|.*\(For instructions type a \?\)$")

(defn- location-prompt [frame]
  (some-> (first (re-seq location-re (topline frame)))
          location-fn))

(defn- prompt
  [frame]
  (when (and (<= (-> frame :cursor :y) 1)
             (before-cursor? frame "##'"))
    (-> (topline+ frame)
        (subs 0 (- (-> frame :cursor :x) 4))
        string/trim)))

(defn- prompt-fn [msg]
  (condp re-seq msg
    #"^What do you want to name this" name-item
    #"^Call .*:" call-item
    #"^How much will you offer\?" offer-how-much
    #"^To what level do you want to teleport\?" leveltele
    #"^What do you want to (?:write|engrave|burn|scribble|scrawl|melt) (?:in|into|on) the (.*?) here\?" write-what
    #"^What do you want to add to the (?:writing|engraving|grafitti|scrawl|text) (?:in|on|melted into) the (.*?) here\?" write-what
    (throw (UnsupportedOperationException. (str "unknown prompt msg " msg))))
  ; TODO
;    qr/^For what do you wish\?/         => 'wish',
;    qr/^"Hello stranger, who are you\?"/ => 'vault_guard',
;    qr/^What monster do you want to genocide\?/ => 'genocide_species',
;    qr/^What class of monsters do you wish to genocide\?/ => 'genocide_class',
  )

(defn- choice-fn [msg]
  (condp re-first-group msg
    #"^\"Shall I remove|^\"Take off your |let me run my fingers" seduced-remove
    #"^Force the gods to be pleased\?" force-god
    #"^Really attack (.*)\?" :>> (partial list really-attack)
    #"^Are you sure you want to enter\?" enter-gehennom
    #"^What do you want to wield" wield-what
    #"^What do you want to wear" wear-what
    #"^What do you want to put on" put-on-what
    #"^What do you want to take off" take-off-what
    #"^What do you want to remove" remove-what
    #"^What do you want to ready" ready-what
    #"^What do you want to drop" drop-single
    #"^Die\?" die
    #"^Do you want to keep the save file\?" keep-save
    #"^What do you want to use or apply" apply-what
    #"^What do you want to name\?" name-what
    #"There is .*force its lock\?" force-lock
    #"[Uu]nlock it\? " unlock-it
    #"[Ll]ock it\? " lock-it
    #"^What do you want to read\?" read-what
    #"^What do you want to drink\?" drink-what
    #"^What do you want to zap\?" zap-what
    #"^Which ring-finger," which-finger
    #"^\"Cad!  You did [0-9]+ zorkmids worth of damage!\"  Pay\?" pay-damage
    #"^There (?:is|are) ([^;]+) here; eat (it|one)\?" :>> (partial list eat-it)
    #"^There (?:is|are) ([^;]+) here; sacrifice (it|one)\?"
    :>> (partial list sacrifice-it)
    #"^What do you want to eat\?" eat-what
    #"^What do you want to sacrifice\?" sacrifice-what
    #"^Attach the .*to the candelabrum\?" attach-candelabrum-candles
    #"^Beware, there will be no return! Still climb\?" still-climb
    #"^You have a little trouble lifting ([^.]+)\. Continue\?"
    :>> (partial list lift-burden :light)
    #"^You have much trouble lifting ([^.]+)\. Continue\?"
    :>> (partial list lift-burden :heavy)
    #"^You have extreme difficulty lifting ([^.]+)\. Continue\?"
    :>> (partial list lift-burden :extreme)
    #"There is ([^,]+) here, loot it\?"
    :>> (partial list loot-it)
    #"Stop eating\?" stop-eating
    #"Do you want to take something out.*" take-something-out
    #"Do you wish to put something in\?" put-something-in
    #"What do you want to dip\?" dip-what
    #"What do you want to dip.* into\?" dip-into-what
    #"What do you want to throw\?" throw-what
    #"What do you want to write with" write-with-what
    #"Do you want to add to the current engraving" append-engraving
    (throw (UnsupportedOperationException.
             (str "unimplemented choice prompt: " msg)))))

(defn- choice-call [msg]
  (log/debug "choice:" msg)
  (let [res (choice-fn msg)]
    (if (list? res)
      res
      (list res msg))))

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
       :alignment (str->kw (status 7))
       :score (-> (status 8) parse-int)}
      (log/error "failed to parse botl1 " botl1))
    (if-let [status (re-first-groups botl2-re botl2)]
      (zipmap [:dlvl :gold :hp :maxhp :pw :maxpw :ac :xplvl :xp :turn]
              (conj (map parse-int (subvec status 1 10))
                    (status 0)))
      (log/error "failed to parse botl2 " botl2))
    {:state (set (for [[substr state] {" Bl" :blind " Stun" :stun " Conf" :conf
                                       " Foo" :ill " Ill" :ill " Hal" :hallu}
                       :when (.contains ^String botl2 substr)]
                   state))
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

(defn- undrawn?
  "Can the topline possibly be this not-yet-drawn message?"
  [frame what]
  (let [topline (topline frame)
        len (min (count topline) (count what))]
    (= (subs topline 0 len) (subs what 0 len))))

(defn new-scraper
  ([delegator] (new-scraper delegator nil))
  ([delegator no-mark-prompt]
   (let [player (ref nil)
         head (ref nil)
         items (ref nil)
         menu-nextpage (ref nil)
         prev (ref no-mark-prompt)]
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
                 (ref-set menu-nextpage nil)
                 (emit-botl delegator frame)
                 ; TODO prompt may re-appear in lastmsg+action as topline msg
                 (apply send delegator (choice-call text))
                 initial))
             (handle-more [frame]
               (or (when-let [item-list (more-list frame)]
                     (log/debug "Handling --More-- list")
                     (ref-set menu-nextpage nil)
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
                     (let [res (condp re-seq text
                                 #"^You don't have that object\."
                                 handle-choice-prompt
                                 #"^To what position do you want to be teleported\?"
                                 handle-location
                                 #"^You wrest one last "
                                 (do (send delegator message text) no-mark)
                                 (do (send delegator message text) initial))]
                       (send delegator write " ")
                       res))))
             (handle-menu-response-start [frame]
               (or (when (and (menu? frame)
                              (= 1 (menu-curpage frame)))
                     (log/debug "first page menu response")
                     (ref-set menu-nextpage 1)
                     (handle-menu-response frame))
                   (log/debug "menu response start - not yet rewound")))
             (handle-menu-response [frame]
               (or (when (and (menu? frame)
                              (= @menu-nextpage (menu-curpage frame)))
                     (log/debug "responding to menu page" @menu-nextpage
                                "options" (menu-options frame))
                     (send delegator (menu-fn @head) (menu-options frame))
                     (when-not (multi-menu? @head)
                       (send delegator write \space))
                     (alter menu-nextpage inc)
                     (when (menu-end? frame)
                       (log/debug "last menu page response done")
                       (ref-set items nil)
                       initial))
                   (log/debug "menu reponse - continuing")
                   handle-menu-response))
             (handle-menu [frame]
               (when (and (menu? frame)
                          (nil? @menu-nextpage))
                 (log/debug "Handling menu")
                 (when (nil? @items)
                   (ref-set head (menu-head frame))
                   (log/debug "Menu start")
                   (ref-set items {}))
                 (alter items merge (menu-options frame))
                 ;(log/debug "items so far:" @items)
                 (if-not (menu-end? frame)
                   (send delegator write " ")
                   (do (log/debug "Menu end")
                       (if @head
                         (let [[cur end] (menu-page frame)]
                           (if (= 1 end)
                             (handle-menu-response-start frame)
                             (do (->> (repeat (dec end) \<)
                                      (apply str)
                                      (send delegator write)) ; rewind menu
                                 handle-menu-response-start)))
                         (do (send delegator inventory-list @items)
                             (ref-set items nil)
                             (send delegator write " ")
                             initial))))))
             (handle-direction [frame]
               (when (and (zero? (-> frame :cursor :y))
                          (re-seq #"^In what direction.*\?" (topline frame)))
                 (log/debug "Handling direction")
                 (emit-botl delegator frame)
                 (send delegator what-direction (topline frame))
                 initial))
             (handle-prompt [frame]
               (when-let [msg (prompt frame)]
                 (log/debug "prompt:" msg)
                 (emit-botl delegator frame)
                 (send delegator write (string/join (repeat 3 backspace)))
                 (send delegator (prompt-fn msg) msg)
                 initial))
             (handle-game-end [frame]
               (cond (game-over? frame) (send delegator write \y)
                     (goodbye? frame) (-> delegator
                                          (send write \space)
                                          (send ended))))
             (handle-location [frame]
               (or (when-let [ev (location-prompt frame)]
                     (log/debug "Handling location")
                     (emit-botl delegator frame)
                     (if-not (.contains (topline frame) "travel to?") ; autotravel may jump to preivously selected position
                       (send delegator know-position frame))
                     (flush-more-list delegator items)
                     (send delegator write \-) ; nuke topline for next redraw to stop repeated botl/map updates while the prompt is active causing multiple commands; this may cause "Can't find dungeon feature" errors on Juiblex's or the planes, but they are unimportant
                     (send delegator ev)
                     initial)))
             (sink [frame] ; for hallu corner-case, discard insignificant extra redraws (cursor stopped on player while the bottom of the map isn't hallu-updated)
               (log/debug "sink discarding redraw"))
             (initial [frame]
               (or (handle-game-start frame)
                   (handle-game-end frame)
                   (handle-more frame)
                   (handle-menu frame)
                   (handle-choice-prompt frame)
                   ;(handle-direction frame)
                   ;(handle-location frame)
                   ; pokud je vykresleny status, nic z predchoziho nesmi invazivne reagovat na "##"
                   (when (status-drawn? frame)
                     ;(log/debug "writing ##' mark")
                     (send delegator write "##'")
                     marked)
                   (log/debug "expecting further redraw")))
             ; v kontextech akci kde ##' muze byt destruktivni (direction prompt - kick,wand,loot,talk...) cekam dokud se neobjevi neco co prokazatelne neni zacatek direction promptu, pak poslu znacku.
             ; dany kontext musi eventualne neco napsat na topline
             (no-mark [frame]
               (log/debug "no-mark maybe direction/location prompt, prev ="
                          @prev)
               (or (= @prev (topline frame)) (ref-set prev nil)
                   (log/debug "no-mark - new topline:" (topline frame))
                   (handle-direction frame)
                   (undrawn? frame "In what direction")
                   (handle-location frame)
                   (undrawn? frame "Where do you want")
                   (log/debug "no-mark - not direction/location prompt")
                   (initial frame)))
             ; odeslal jsem marker, cekam jak se vykresli
             (marked [frame]
               ; veci co se daji bezpecne potvrdit pomoci ## muzou byt jen tady, ve druhem to muze byt zkratka, kdyz se vykresleni stihne - pak se ale hur odladi spolehlivost tady
               ; tady (v obou scraperech) musi byt veci, ktere se nijak nezmeni pri ##'
               (or (handle-game-end frame)
                   (handle-more frame)
                   (handle-menu frame)
                   (handle-choice-prompt frame)
                   (handle-prompt frame)
                   (when (and (zero? (-> frame :cursor :y))
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
               (or (when (and (more-prompt? frame) (topline-cursor? frame))
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
                   (log/debug "lastmsg expecting further redraw")))]
       (if no-mark-prompt
         no-mark
         initial)))))

(defn- apply-scraper
  "If the current scraper returns a function when applied to the frame, the function becomes the new scraper, otherwise the current scraper remains.  A fresh scraper is created and applied if the current scraper is nil."
  [orig-scraper delegator frame]
  (let [current-scraper (or orig-scraper (new-scraper delegator))
        next-scraper (current-scraper frame)]
    (if (fn? next-scraper)
      next-scraper
      current-scraper)))

(defn scraper-handler [scraper delegator]
  (let [no-mark (fn [prompt]
                  (dosync (ref-set scraper (new-scraper delegator prompt))
                          (log/debug "no-mark scraper, prev =" prompt)))]
    (reify
      ThrowWhatHandler
      (throw-what [_ prompt]
        (no-mark prompt))
      ApplyItemHandler
      (apply-what [_ prompt]
        (no-mark prompt))
      ActionChosenHandler
      (action-chosen [_ action]
        (dosync
          (if (= :autotravel (typekw action))
            (no-mark "")
            (ref-set scraper nil)) ; escape sink
          (log/debug "reset scraper for" (type action))))
      RedrawHandler
      (redraw [_ frame]
        #_(dosync (alter scraper apply-scraper delegator frame))
        (->> (dosync (alter scraper apply-scraper delegator frame))
             type
             (log/debug "next scraper:"))))))
