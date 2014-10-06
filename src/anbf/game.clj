(ns anbf.game
  "representation of the game world"
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [anbf.player :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.level :refer :all]
            [anbf.fov :refer :all]
            [anbf.monster :refer :all]
            [anbf.actions :refer :all]
            [anbf.tile :refer :all]
            [anbf.item :refer :all]
            [anbf.itemid :refer :all]
            [anbf.position :refer :all]
            [anbf.handlers :refer :all]
            [anbf.tracker :refer :all]
            [anbf.util :refer :all]
            [anbf.pathing :refer :all]
            [anbf.delegator :refer :all]))

(defrecord Game
  [frame
   player
   dungeon
   branch-id ; current
   dlvl ; current
   discoveries ; database of facts about item identities and appearances, TODO elimination on change - if some exclusive appearance is left with only 1 possibility, nothing else can have that appearance
   fov
   turn
   score]
  anbf.bot.IGame
  (frame [this] (:frame this))
  (player [this] (:player this)))

(defn new-game []
  (map->Game {:player (new-player)
              :dungeon (new-dungeon)
              :branch-id :main
              :discoveries initial-discoveries}))

(defn- update-game-status [game status]
  (->> (keys game) (select-keys status) (into game)))

(defn- update-by-botl [game status]
  (-> game
      (assoc :dlvl (:dlvl status))
      (update :player update-player status)
      (update-game-status status)))

(defn- rogue-ghost? [game level tile]
  (and ;(blank? tile) - not yet updated
       (= \space (get-in game [:frame :lines (:y tile) (:x tile)]))
       (adjacent? (:player game) tile)
       (or (and (:feature tile) (not (rock? tile)))
           (:item-glyph tile)
           (->> (neighbors level tile)
                (filter (some-fn unknown? rock? corridor?))
                (less-than? 2)))))

(defn- update-visible-tile [game level tile]
  (assoc tile
         :seen (or (:seen tile) (if-not (boulder? tile) true))
         :dug (if (and (= :mines (branch-key game))
                       (not-any? #{:end :minetown} (:tags level))
                       (or (corridor? tile)
                           (and (some (some-fn :dug corridor?)
                                      (neighbors level tile) )
                                (or (boulder? tile)
                                    (and (= \* (:glyph tile))
                                         (nil? (:color tile)))))))
                true
                (:dug tile))
         :feature (cond (and (blank? tile) (unknown? tile)
                             (or (not (:rogue (:tags level)))
                                 (not (rogue-ghost? game level tile)))) :rock
                        :else (:feature tile))))

(defn- update-explored [game]
  (let [level (curlvl game)]
    (update-in game [:dungeon :levels (branch-key game) (:dlvl game) :tiles]
               (partial map-tiles (fn [tile]
                                    (if (visible? game level tile)
                                      (update-visible-tile game level tile)
                                      tile))))))

(defn- gather-monsters [game frame]
  (let [level (curlvl game)
        rogue? (:rogue (:tags level))]
    (into {} (map (fn monster-entry [tile glyph color]
                    (if (and (not= (position tile)
                                   (position (:player game)))
                             (or (and rogue? (rogue-ghost? game level tile))
                                 (monster? glyph color)))
                      (vector (position tile)
                              (new-monster (:x tile) (:y tile)
                                           (:turn game) glyph color))))
                  (tile-seq level)
                  (->> (:lines frame) rest (apply concat))
                  (->> (:colors frame) rest (apply concat))))))

(defn- parse-map [game frame]
  (-> game
      (assoc-in [:dungeon :levels (branch-key game) (:dlvl game)
                 :monsters] (gather-monsters game frame))
      (update-in [:dungeon :levels (branch-key game) (:dlvl game) :tiles]
                 (partial map-tiles parse-tile)
                 (rest (:lines frame))
                 (rest (:colors frame)))))

(defn- update-dungeon [{:keys [turn] :as game} {:keys [cursor] :as frame}]
  (-> game
      (parse-map frame)
      infer-branch
      infer-tags
      level-blueprint
      (reflood-room cursor)
      (update-curlvl-at cursor dissoc :blocked)
      (update-curlvl-at cursor update :first-walked #(or % turn))
      (update-curlvl-at cursor assoc :walked turn)))

(defn- looks-engulfed? [{:keys [cursor lines] :as frame}]
  (if (and (< 0 (:x cursor) 79)
           (< 1 (:y cursor) 21))
    (let [row-before (dec (:x cursor))
          row-after (inc (:x cursor))
          line-above (nth lines (dec (:y cursor)))
          line-at (nth lines (:y cursor))
          line-below (nth lines (inc (:y cursor)))]
      (and (= "/-\\" (subs line-above row-before (inc row-after)))
           (re-seq #"\|.\|" (subs line-at row-before (inc row-after)))
           (= "\\-/" (subs line-below row-before (inc row-after)))))))

(defn- update-map [game frame]
  (if (looks-engulfed? frame)
    (assoc-in game [:player :engulfed] true)
    (-> game
        (assoc-in [:player :engulfed] false)
        (update-dungeon frame)
        (update-fov (:cursor frame))
        (track-monsters game)
        update-explored)))

(defn- level-msg [msg]
  (condp re-seq msg
    #"You enter what seems to be an older, more primitive world\." :rogue
    #"The odor of burnt flesh and decay pervades the air\." :votd
    #"Look for a \.\.\.ic transporter\." :quest
    #"So be it\." :gehennom
    #"Through clouds of sulphurous gasses, you see a rock palisade|Once again, you stand in sight of Lord Surtur's lair" :end
    #"You feel your mentor's presence; perhaps .*is nearby.|You sense the presence of |In your mind, you hear the taunts of Ashikaga Takauji" :end
    nil))

(defn prayer-timeout
  ">95% confidence"
  [game]
  ; TODO wishes
  ; TODO crowning
  (if (or (#{:earth :fire :air :water :astral} (branch-key game))
          (:sanctum (curlvl-tags game))
          (have game #{"Amulet of Yendor" "Book of the Dead"}))
    4000
    1300))

(defn can-pray? [game]
  (and (not (in-gehennom? game))
       (< (prayer-timeout game)
          ((fnil - nil -1000) (:turn game) (:last-prayer game)))))

(def ^:private welcome-re #"welcome to NetHack!  You are a.* (\w+ \w+)\.|.* (\w+ \w+), welcome back to NetHack!")

(def races {"dwarven" :dwarf
            "elven" :elf
            "gnomish" :gnome})

(defn set-race-role-handler [anbf]
  (reify ToplineMessageHandler
    (message [this text]
      (when-let [[race role] (some->> (re-first-groups welcome-re text)
                                      (find-first some?)
                                      (#(string/split % #" "))
                                      (map (comp #(get races % (keyword %))
                                                 string/lower-case)))]
        (log/debug "player role:" role "- race:" race)
        (swap! (:game anbf) update :player assoc
               :role role
               :race race
               :intrinsics (into (initial-intrinsics role)
                                 (initial-intrinsics race)))
        (deregister-handler anbf this)))))

(defn game-handler
  [{:keys [game delegator] :as anbf}]
  (reify
    ActionChosenHandler
    (action-chosen [_ action]
      (if-not (#{:call :name :discoveries :inventory :look :farlook}
                       (typekw action))
        (swap! game #(assoc % :last-action action
                            :last-position (position (:player %))
                            :last-path (get action :path (:last-path %))))))
    AboutToChooseActionHandler
    (about-to-choose [_ game]
      (swap! (:game anbf) filter-visible-uniques))
    RedrawHandler
    (redraw [_ frame]
      (swap! game assoc :frame frame))
    BOTLHandler
    (botl [_ status]
      (let [old-dlvl (:dlvl @game)
            new-dlvl (:dlvl status)]
        (swap! game update-by-botl status)
        (when (not= old-dlvl new-dlvl)
          (dlvl-changed @delegator old-dlvl new-dlvl)
          (if (and old-dlvl
                   (= "Home" (subs old-dlvl 0 4))
                   (= "Dlvl" (subs new-dlvl 0 4)))
            (swap! game assoc :branch-id :main) ; kicked out of quest
            (swap! game ensure-curlvl)))))
    KnowPositionHandler
    (know-position [_ frame]
      (swap! game update :player into (:cursor frame)))
    FullFrameHandler
    (full-frame [_ frame]
      (swap! game update-map frame))
    MultilineMessageHandler
    (message-lines [_ lines]
      (if-let [level (level-msg (first lines))]
        (update-on-known-position anbf add-curlvl-tag level)))
    ToplineMessageHandler
    (message [_ text]
      (swap! game assoc :last-topline text)
      (or (condp re-seq text
            #"You feel you could be more dangerous|You feel more confident"
            (swap! game assoc-in [:player :can-enhance] true)
            #"You feel weaker"
            (swap! game assoc-in [:player :stat-drained] true)
            #"makes you feel (better|great)"
            (swap! game assoc-in [:player :stat-drained] false)
            #"You feel feverish"
            (swap! game assoc-in [:player :lycantrophy] true)
            #"You feel purified"
            (swap! game assoc-in [:player :lycantrophy] false)
            #"Your .* feels? somewhat better"
            (swap! game assoc-in [:player :leg-hurt] false)
            #"You turn into a"
            (-> anbf update-inventory update-items)
            #"You are almost hit"
            (update-items anbf)
            #"The walls around you begin to bend and crumble!"
            (swap! game update-at-player assoc :feature :stairs-down)
            #"You now wield|Your.*turns to dust|boils? and explode|freeze and shatter|breaks? apart and explode|Your.* goes out|Your.* has gone out|Your.* is consumed!|Your.* has burnt away| stole |You feel a malignant aura surround you"
            (update-inventory anbf)
            #"shop appears to be deserted"
            (if (< 33 (dlvl @game))
              (swap! game add-curlvl-tag :orcus))
            #"You hear the rumble of distant thunder|You hear the studio audience applaud!|You feel guilty about losing your pet|Thou art arrogant, mortal|You feel that.* is displeased\."
            (do (log/warn "god angered:" text)
                (swap! game assoc :god-angry true)) ; might as well #quit
            #"You feel a strange mental acuity|You feel in touch with the cosmos"
            (swap! game add-intrinsic :telepathy)
            #"Your senses fail"
            (swap! game remove-intrinsic :telepathy)
            #"You feel in control of yourself|You feel centered in your personal space"
            (swap! game add-intrinsic :telecontrol)
            #"You feel a momentary chill|You be chillin|You feel cool|You are uninjured|You don't feel hot|The fire doesn't feel hot|You feel rather warm|You feel mildly (?:warm|hot)|enveloped in flames\. But you resist the effects|It seems quite tasty"
            (swap! game add-intrinsic :fire)
            #"You feel warmer"
            (swap! game remove-intrinsic :fire)
            #"You feel full of hot air|You feel warm|duck some of the blast|You don't feel cold|The frost doesn't seem cold|You feel a (?:little|mild) chill|You're covered in frost. But you resist the effects|You feel mildly chilly"
            (swap! game add-intrinsic :cold)
            #"You feel cooler"
            (swap! game remove-intrinsic :cold)
            #"You feel wide awake|You feel awake!"
            (swap! game add-intrinsic :sleep)
            #"You feel tired!"
            (swap! game remove-intrinsic :sleep)
            #"You feel grounded|Your health currently feels amplified|You feel insulated|You feel a mild tingle"
            (swap! game add-intrinsic :shock)
            #"You feel conductive"
            (swap! game remove-intrinsic :shock)
            #"You feel(?: especially)? healthy|You feel hardy"
            (swap! game add-intrinsic :poison)
            #"You feel a little sick"
            (swap! game remove-intrinsic :poison)
            #"You feel very jumpy|You feel diffuse"
            (swap! game add-intrinsic :teleport)
            #"You feel very firm|You feel totally together"
            (swap! game add-intrinsic :disintegration)
            #"You feel sensitive"
            (swap! game add-intrinsic :warning)
            #"You feel less sensitive"
            (swap! game remove-intrinsic :warning)
            #"You feel clumsy"
            (swap! game remove-intrinsic :stealth)
            #"You feel less attractive"
            (swap! game remove-intrinsic :aggravate)
            #"You feel less jumpy"
            (swap! game remove-intrinsic :teleport)
            #"You feel hidden"
            (swap! game add-intrinsic :invisibility)
            #"You feel paranoid"
            (swap! game remove-intrinsic :invisibility)
            #"You see an image of someone stalking you|You feel transparent|You feel very self-conscious|Your vision becomes clear"
            (swap! game add-intrinsic :see-invis)
            #"You feel perceptive!"
            (swap! game add-intrinsic :search)
            #"You thought you saw something|You tawt you taw a puttie tat"
            (swap! game remove-intrinsic :see-invis)
            #"You feel quick!"
            (swap! game add-intrinsic :speed)
            #"You feel slower|You feel slow!"
            (swap! game remove-intrinsic :speed)
            nil)
          (if-let [level (level-msg text)]
            (update-on-known-position anbf add-curlvl-tag level))
          (if-let [room (room-type text)]
            (update-before-action anbf mark-room room))))))
