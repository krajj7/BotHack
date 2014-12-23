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
            [anbf.montype :refer :all]
            [anbf.actions :refer :all]
            [anbf.tile :refer :all]
            [anbf.item :refer :all]
            [anbf.itemid :refer :all]
            [anbf.position :refer :all]
            [anbf.handlers :refer :all]
            [anbf.tracker :refer :all]
            [anbf.sokoban :refer :all]
            [anbf.util :refer :all]
            [anbf.pathing :refer :all]
            [anbf.delegator :refer :all]))

(defrecord Game
  [frame
   player
   dungeon
   branch-id ; current
   dlvl ; current
   discoveries ; database of facts about item identities and appearances
   used-names
   tried ; set of tried armor/scrolls/potions/rings/amulets appearances
   fov
   genocided
   wishes
   turn
   turn* ; internal clock - increments per each action (unlike game turns)
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
              :used-names #{}
              :tried #{}
              :turn* 0
              :wishes 0
              :genocided #{}
              :discoveries (new-discoveries)}))

(defn- update-game-status [game status]
  (->> (keys game) (select-keys status) (into game)))

(defn- update-by-botl [game status]
  (-> game
      (assoc :dlvl (:dlvl status))
      (update :player update-player status)
      (update-game-status status)))

(defn- rogue-ghost? [game level tile]
  (and (:rogue (:tags level))
       (not (blind? (:player game)))
       ;(blank? tile) - not yet updated
       (= \space (get-in game [:frame :lines (:y tile) (:x tile)]))
       (adjacent? (:player game) tile)
       (or (and (:feature tile) (not (rock? tile)))
           (:item-glyph tile)
           (and (->> (neighbors level tile)
                     (filter (some-fn unknown? rock? corridor? door?))
                     (less-than? 2))
                (->> (neighbors level tile) (filter door?) (less-than? 2))))))

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
                             (not (rogue-ghost? game level tile))) :rock
                        :else (:feature tile))))

(defn- update-explored [game]
  (let [level (curlvl game)]
    (update-curlvl game update :tiles
                   (partial map-tiles (fn [tile]
                                        (if (visible? game level tile)
                                          (update-visible-tile game level tile)
                                          tile))))))

(defn- soko-mimic? [{:keys [player last-action*] :as game} level tile]
  (if-let [sokotag (or (:soko-4a (:tags level))
                       (:soko-4b (:tags level)))]
    (and ;(= \8 (:glyph tile)) - not yet updated
         (= \8 (get-in game [:frame :lines (:y tile) (:x tile)]))
         (not (:pushed tile))
         (not (and (= :move (typekw last-action*))
                   (boulder? (at-curlvl (:last-state game) player))
                   (= (in-direction player (:dir last-action*)) (position tile))
                   (adjacent? player tile)))
         (not ((initial-boulders sokotag) (position tile))))))

(defn- gather-monsters [game frame]
  (let [level (curlvl game)
        rogue? (:rogue (:tags level))
        soko? (= :sokoban (branch-key game))]
    (into {} (map (fn monster-entry [tile glyph color]
                    (if (and (not= (position tile) (position (:player game)))
                             (or (and rogue? (rogue-ghost? game level tile))
                                 (and soko? (soko-mimic? game level tile))
                                 (monster? glyph color)))
                      (let [monster (new-monster (:x tile) (:y tile)
                                                 (:turn game) glyph color)]
                        (if-some [p (and (#{"gremlin"} (typename monster))
                                         (:gremlins-peaceful game))]
                          (vector (position tile) (assoc monster :peaceful p))
                          (vector (position tile)
                                  (if (and soko? (= \8 glyph))
                                    (assoc monster :peaceful false
                                           :type (name->monster "giant mimic"))
                                    monster))))))
                  (tile-seq level)
                  (->> (:lines frame) rest (apply concat))
                  (->> (:colors frame) rest (apply concat))))))

(defn- parse-map [game frame]
  (-> game
      (update-curlvl assoc :monsters (gather-monsters game frame))
      (remove-monster (:player game))
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
  {:pre [(:player game)]}
  ; TODO wishes
  ; TODO crowning
  (if (or (planes (branch-key game))
          (:sanctum (curlvl-tags game))
          (have game #{"Amulet of Yendor" "Book of the Dead"}))
    4000
    1300))

(defn can-pray? [game]
  {:pre [(:player game)]}
  (and (not (in-gehennom? game))
       (let [tile (at-player game)]
         (not (and (altar? tile) (not= (:alignment (:player game))
                                       (:alignment tile)))))
       (< (prayer-timeout game)
          (- (:turn game) (or (:last-prayer game) -1100)))))

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

(defn- move-action? [game]
  (#{:move :autotravel} (typekw (:last-action* game))))

(defn- moved?
  "Returns true if the player moved during the last action turn"
  [game]
  {:pre [(:player game)]}
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
      (when (= "Fort Ludios" new-dlvl)
        (log/debug "entering ludios")
        (swap! game assoc :branch-id :ludios))
      (when (> (dlvl-number new-dlvl) 35)
        (log/debug "entering wiztower portal")
        (swap! game assoc :branch-id :wiztower))))

(defn update-fleeing [game desc]
  (->> (curlvl-monsters game)
       (filter #(and (adjacent? (:player game) %)
                     (= desc (typename %))))
       (reduce #(update-monster %1 %2 assoc :fleeing true) game)))

(defn- mark-temple [{:keys [player] :as game}]
  (let [level (curlvl game)
        tile (at level player)
        align (:alignment tile)]
    ; temple will only be marked around the altar (boundary is harder to detect than for shops, should be sufficient anyway)
    (if (and align (altar? tile) (some (every-pred priest? :peaceful)
                                       (vals (:monsters level))))
      (as-> game res
        (reduce #(update-at %1 %2 assoc :room :temple :alignment align)
                res
                (including-origin neighbors level player))
        (add-curlvl-tag res :temple))
      game)))

(defn itemid-handler [{:keys [game] :as anbf}]
  (reify FoundItemsHandler
    (found-items [_ items]
      (doseq [item items :when (:cost item)]
        ;; could be death drop
        ;(if (and (potion? item) (= :food (:room (at-player @game))))
        ;  (swap! game add-prop-discovery (appearance-of item) :food true))
        (if (price-id? item)
          (swap! game add-observed-cost (appearance-of item)
                 (/ (:cost item) (:qty item))))))))

(defn unmark-temple [game]
  (update-curlvl game update :tiles
                 (partial map-tiles #(if (= :temple (:room %))
                                       (assoc % :room nil)
                                       %))))

(defn game-handler
  [{:keys [game delegator] :as anbf}]
  (let [portal (atom nil)
        levelport (atom nil)]
    (reify
      AboutToChooseActionHandler
      (about-to-choose [_ _]
        (swap! game update :turn* inc)
        (reset! portal nil)
        (reset! levelport nil)
        (swap! game filter-visible-uniques)
        (when (altar? (at-player @game))
          (swap! game add-curlvl-tag :altar)
          (swap! game mark-temple)))
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
      CommandResponseHandler
      (response-chosen [_ method res]
        (when (or (= genocide-class method) ;FIXME doesn't consider cursed geno!
                  (= genocide-monster method))
          (swap! game update :genocided conj res))
        (when (and (= make-wish method) (not= "nothing" res))
          (swap! game update :wishes inc)))
      MultilineMessageHandler
      (message-lines [_ lines]
        (or (if (and (re-seq things-re (first lines)) (moved? @game))
              (swap! game update-at-player assoc :new-items true))
            (if-let [level (level-msg (first lines))]
              (swap! game add-curlvl-tag level))))
      ToplineMessageHandler
      (message [_ text]
        (swap! game assoc :last-topline text)
        (or (if-let [level (level-msg text)]
              (update-on-known-position anbf add-curlvl-tag level))
            (if-let [room (room-type text)]
              (update-before-action anbf mark-room room))
            (condp-all re-first-group text
              #"You have an eerie feeling|A shiver runs down your|You feel like you are being watched"
              (swap! game unmark-temple)
              #"(?:grabs|swings itself around) you!"
              (swap! game assoc-in [:player :grabbed] true)
              #"can no longer hold you!|You get released!|(?:releases you!|grip relaxes\.)|You kill"
              (swap! game assoc-in [:player :grabbed] false)
              #"Nothing happens"
              (if (and (:stat-drained (:player @game))
                       (= :apply (typekw (:last-action* @game))))
                (swap! game assoc-in [:player :stat-drained] false))
              etext-re
              (if (move-action? @game)
                (update-tile anbf))
              thing-re
              (if (move-action? @game)
                (update-tile anbf))
              #"The ([^!]+) turns to flee!"
              :>> (partial swap! game update-fleeing)
              #"You step onto a level teleport trap!"
              (reset! levelport true)
              #"The (.*) (?:hits|misses|just misses)[!.]"
              :>> #(swap! game recheck-peaceful-status
                          (every-pred (comp (partial = %) typename)
                                      (partial adjacent? (:player @game))))
              #"You've been warned"
              (swap! game recheck-peaceful-status guard?)
              #" appears before you\."
              (swap! game recheck-peaceful-status demon-lord?)
              #"The venom blinds you|You can't see through all the sticky goop"
              (swap! game update-in [:player :state] conj :ext-blind)
              #"Infidel, you have entered Moloch's Sanctum!"
              (swap! game recheck-peaceful-status high-priest?)
              #"The Amulet of Yendor.* feels (hot|very warm|warm)"
              :>> (partial update-on-known-position anbf update-portal-range)
              #"You are slowing down|Your limbs are stiffening"
              (swap! game assoc-in [:player :stoning] true)
              #"You feel (?:more )?limber|What a pity - you just ruined a future piece"
              (swap! game assoc-in [:player :stoning] false)
              #"You don't feel very well|You are turning a little green|Your limbs are getting oozy|Your skin begins to peel away|You are turning into a green slime"
              (log/warn "sliming") ; no message on fix :-(
              #"You feel you could be more dangerous|You feel more confident"
              (swap! game assoc-in [:player :can-enhance] true)
              #"You feel weaker"
              (swap! game assoc-in [:player :stat-drained] true)
              #"makes you feel better"
              (swap! game assoc-in [:player :stat-drained] true)
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
              #"You turn into a| slips from your"
              (-> anbf update-inventory update-tile)
              #"You are almost hit|The altar glows |power of .*increase"
              (update-tile anbf)
              #" activated a magic portal!"
              (do (reset! portal true)
                  (if (planes (branch-key @game))
                    (swap! game (comp ensure-curlvl
                                      #(assoc % :branch-id (next-plane %))))
                    (update-at-player-when-known anbf assoc :feature :portal)))
              #"The walls around you begin to bend and crumble!"
              (swap! game update-at-player assoc :feature :stairs-down)
              #"You now wield|Your.*turns to dust|boils? and explode|freeze and shatter|breaks? apart and explode|catch(?:es)? fire and burn|Your.* goes out|Your.* has gone out|Your.* is consumed!|Your.* has burnt away| stole |You feel a malignant aura surround you|Your.* (?:rust|corrode|rot|smoulder)| snatches |Take off your|let me run my fingers|a djinni emerges|A curse upon thee|murmurs in your ear|suddenly explores!"
              (update-inventory anbf)
              #" reads a scroll | drinks a .*potion|Your brain is eaten!"
              (update-discoveries anbf)
              #"shop appears to be deserted"
              (if (< 33 (dlvl @game))
                (swap! game add-curlvl-tag :orcus))
              #"You hear the rumble of distant thunder|You hear the studio audience applaud!"
              (do (swap! game assoc-in [:player :protection] 0)
                  (swap! game adjust-prayer-timeout))
              #"You feel guilty about losing your pet|Thou art arrogant, mortal|You feel that.* is displeased\."
              (do (log/warn "god angered:" text)
                  (swap! game adjust-prayer-timeout)
                  (swap! game assoc-in [:player :protection] 0)
                  (swap! game assoc :god-angry true)) ; might as well #quit
              #"You feel a strange mental acuity|You feel in touch with the cosmos|thee the gift of Telepathy"
              (swap! game add-intrinsic :telepathy)
              #"Your senses fail|You murderer!"
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
              #"This tastes like slime mold juice"
              (if-let [item (some->> (:last-action @game) :slot
                                     (inventory-slot @game))]
                (if (and (blessed? item)
                         (= "potion of see invisible" (item-name @game item)))
                  (swap! game add-intrinsic :see-invis)))
              #"You see an image of someone stalking you|You feel transparent|You feel very self-conscious|Your vision becomes clear"
              (swap! game add-intrinsic :see-invis)
              #"You feel perceptive!"
              (swap! game add-intrinsic :search)
              #"You thought you saw something|You tawt you taw a puttie tat"
              (swap! game remove-intrinsic :see-invis)
              #"You feel quick!|grant thee the gift of Speed|You speed up|Your quickness feels more natural"
              (swap! game add-intrinsic :speed)
              #"You feel slower|You feel slow!|You slow down|Your quickness feels less natural"
              (swap! game remove-intrinsic :speed)
              nil))))))
