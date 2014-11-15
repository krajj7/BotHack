(ns anbf.game
  "representation of the game world"
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [anbf.player :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.level :refer :all]
            [anbf.fov :refer :all]
            [anbf.frame :refer :all]
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

(defmethod print-method Game [game w]
  (.write w (str "#anbf.game.Game"
                 (assoc (.without game :discoveries)
                        :discoveries "<trimmed>"))))

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
  (and (not (blind? (:player game)))
       ;(blank? tile) - not yet updated
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
         :feature (cond (and (#{:water :air} (branch-key game))
                             (not (rock? tile)) (blank? tile)) :floor
                        (and (blank? tile) (unknown? tile)
                             (or (not (:rogue (:tags level)))
                                 (not (rogue-ghost? game level tile)))) :rock
                        :else (:feature tile))))

(defn- update-explored [game]
  (let [level (curlvl game)]
    (update-curlvl game update :tiles
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
                      (let [monster (new-monster (:x tile) (:y tile)
                                                 (:turn game) glyph color)]
                        (if-some [p (and (#{"gremlin"} (typename monster))
                                         (:gremlins-peaceful game))]
                          (vector (position tile) (assoc monster :peaceful p))
                          (vector (position tile) monster)))))
                  (tile-seq level)
                  (->> (:lines frame) rest (apply concat))
                  (->> (:colors frame) rest (apply concat))))))

(defn- parse-map [game frame]
  (-> game
      (update-curlvl assoc :monsters (gather-monsters game frame))
      (update-curlvl update :tiles (partial map-tiles parse-tile)
                     (rest (:lines frame))
                     (rest (:colors frame)))))

(defn- update-dungeon [{:keys [turn] :as game} {:keys [cursor] :as frame}]
  (-> game
      (parse-map frame)
      infer-branch
      infer-tags
      level-blueprint
      (reflood-room cursor)
      (update-at cursor dissoc :blocked)
      (update-at cursor update :first-walked #(or % turn))
      (update-at cursor assoc :walked turn)))

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
  (if (or (planes (branch-key game))
          (:sanctum (curlvl-tags game))
          (have game #{"Amulet of Yendor" "Book of the Dead"}))
    4000
    1300))

(defn can-pray? [game]
  (and (not (in-gehennom? game))
       (< (prayer-timeout game)
          (- (:turn game) (or (:last-prayer game) -1000)))))

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
                                      (map #(get races % (str->kw %))))]
        (log/debug "player role:" role "- race:" race)
        (swap! (:game anbf) update :player assoc
               :role role
               :race race
               :intrinsics (into (initial-intrinsics role)
                                 (initial-intrinsics race)))
        (deregister-handler anbf this)))))

(defn moved?
  "Returns true if the player moved during the last action turn"
  [game]
  (or (not (:last-position game))
      (not= (:last-position game) (position (:player game)))))

(defn- update-portal-range [{:keys [player] :as game} temp]
  (let [dist (case temp
               "hot" 3
               "very warm" 8
               "warm" 12)
        in-range (set (rectangle (position (- (:x player) dist)
                                           (- (:y player) dist))
                                 (position (+ (:x player) dist)
                                           (+ (:y player) dist))))]
    (update-curlvl game update :tiles
                   (partial map-tiles #(if-not (in-range (position %))
                                         (assoc % :walked 1)
                                         %)))))

(defn- update-peaceful-status [game monster-selector]
  (->> (curlvl-monsters game)
       (filter monster-selector)
       (reduce #(update-monster %1 %2 assoc :peaceful :update)
               game)))

(defn- portal-handler [{:keys [game] :as anbf} level new-dlvl]
  (or (when (= "Astral Plane" new-dlvl)
        (log/debug "entering astral")
        (swap! game assoc :branch-id :astral))
      (when (subbranches (branch-key @game level))
        (log/debug "leaving subbranch via portal")
        (swap! game assoc :branch-id :main))
      (when (= "Home" (subs new-dlvl 0 4))
        (log/debug "entering quest portal")
        (swap! game assoc :branch-id :quest))
      (when (> (dlvl-number new-dlvl) 35)
        (log/debug "entering wiztower portal")
        (swap! game assoc :branch-id :wiztower))
      (when (= "Fort Ludios" new-dlvl)
        (log/debug "entering ludios")
        (swap! game assoc :branch-id :ludios))))

(defn update-fleeing [game desc]
  (->> (curlvl-monsters game)
       (filter #(and (adjacent? (:player game) %)
                     (= desc (typename %))))
       (reduce #(update-monster %1 %2 assoc :fleeing true) game)))

(defn game-handler
  [{:keys [game delegator] :as anbf}]
  (let [portal (atom nil)
        levelport (atom nil)]
    (reify
      AboutToChooseActionHandler
      (about-to-choose [_ _]
        (reset! portal nil)
        (reset! levelport nil)
        (swap! game filter-visible-uniques))
      DlvlChangeHandler
      (dlvl-changed [_ old-dlvl new-dlvl]
        (swap! game assoc :gremlins-peaceful nil)
        (if (and @levelport
                 (= :mines (branch-key @game))
                 (not (neg? (dlvl-compare (branch-entry @game :mines)
                                          new-dlvl))))
          (swap! game assoc :branch-id :main))
        (if @portal
          (portal-handler anbf (curlvl (:last-state @game)) new-dlvl)))
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
            (update-on-known-position anbf apply-default-blueprint)
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
        (or (if (and (re-seq things-re (first lines)) (moved? @game))
              (update-tile anbf))
            (if-let [level (level-msg (first lines))]
              (update-on-known-position anbf add-curlvl-tag level))))
      ToplineMessageHandler
      (message [_ text]
        (swap! game assoc :last-topline text)
        (or (if-let [level (level-msg text)]
              (update-on-known-position anbf add-curlvl-tag level))
            (if-let [room (room-type text)]
              (update-before-action anbf mark-room room))
            (condp-all re-first-group text
              thing-re
              (if (moved? @game)
                (update-tile anbf))
              #"The ([^!]+) turns to flee!"
              :>> (partial swap! game update-fleeing)
              #"You step onto a level teleport trap!"
              (reset! levelport true)
              #" appears before you\."
              (swap! game update-peaceful-status demon-lord?)
              #"Infidel, you have entered Moloch's Sanctum!"
              (swap! game update-peaceful-status high-priest?)
              #"The Amulet of Yendor.* feels (hot|very warm|warm)"
              :>> (partial update-on-known-position anbf update-portal-range)
              #"You are slowing down|Your limbs are stiffening"
              (swap! game assoc-in [:player :stoning] true)
              #"You feel limber|What a pity - you just ruined a future piece"
              (swap! game assoc-in [:player :stoning] false)
              #"You don't feel very well|You are turning a little green|Your limbs are getting oozy|Your skin begins to peel away|You are turning into a green slime"
              (log/warn "sliming") ; no message on fix :-(
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
              #"It's a wall\."
              (swap! game assoc-in [:player :trapped] false)
              #"You sink into the lava"
              (update-at-player-when-known anbf assoc :feature :lava)
              #"You turn into a"
              (-> anbf update-inventory update-tile)
              #"You are almost hit"
              (update-tile anbf)
              #" activated a magic portal!"
              (do (reset! portal true)
                  (if (planes (branch-key @game))
                    (swap! game (comp ensure-curlvl
                                      #(assoc % :branch-id (next-plane %))))
                    (update-at-player-when-known anbf assoc :feature :portal)))
              #"The walls around you begin to bend and crumble!"
              (swap! game update-at-player assoc :feature :stairs-down)
              #"You now wield|Your.*turns to dust|boils? and explode|freeze and shatter|breaks? apart and explode|catch(?:es)? fire and burn|Your.* goes out|Your.* has gone out|Your.* is consumed!|Your.* has burnt away| stole |You feel a malignant aura surround you|Your.* (rust|corrode|rot|smoulder)"
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
              #"You feel stealthy|I grant thee the gift of Stealth"
              (swap! game add-intrinsic :stealth)
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
              #"You feel quick!|grant thee the gift of Speed"
              (swap! game add-intrinsic :speed)
              #"You feel slower|You feel slow!|You slow down|Your quickness feels less natural"
              (swap! game remove-intrinsic :speed)
              nil))))))
