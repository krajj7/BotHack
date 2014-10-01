(ns anbf.game
  "representation of the game world"
  (:require [clojure.tools.logging :as log]
            [clojure.string :refer [lower-case]]
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
       (or (and (:feature tile) (not= :rock (:feature tile)))
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

(def ^:private welcome-re #"welcome to NetHack!  You are a.* (\w+)\.|.* (\w+), welcome back to NetHack!")

(defn set-role-handler [anbf]
  (reify ToplineMessageHandler
    (message [this text]
      (when-let [role (some->> (re-first-groups welcome-re text)
                               (find-first some?) lower-case keyword)]
        (log/debug "player role:" role)
        (swap! (:game anbf) assoc-in [:player :role] role)
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
            #"makes you feel great"
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
            #"You now wield|Your.*turns to dust|boils? and explode|freeze and shatter|breaks? apart and explode|Your.* goes out"
            (update-inventory anbf)
            #"shop appears to be deserted"
            (if (< 33 (dlvl @game))
              (swap! game add-curlvl-tag :orcus))
            nil)
          (if-let [level (level-msg text)]
            (update-on-known-position anbf add-curlvl-tag level))
          (if-let [room (room-type text)]
            (update-before-action anbf mark-room room))))))
