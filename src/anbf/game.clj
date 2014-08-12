(ns anbf.game
  "representation of the game world"
  (:require [clojure.tools.logging :as log]
            [anbf.player :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.fov :refer :all]
            [anbf.monster :refer :all]
            [anbf.actions :refer :all]
            [anbf.tile :refer :all]
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
   fov
   turn
   score]
  anbf.bot.IGame
  (frame [this] (:frame this))
  (player [this] (:player this)))

(defn new-game []
  (Game. nil (new-player) (new-dungeon) :main nil nil 0 0))

(defn- update-game [game status]
  (->> game keys (select-keys status) (merge game)))

(defn- update-by-botl [game status]
  (-> game
      (assoc :dlvl (:dlvl status))
      (update-in [:player] update-player status)
      (update-game status)))

(defn- update-visible-tile [tile rogue?]
  (assoc tile
         :seen true
         :feature (if (and (= (:glyph tile) \space)
                           (or (nil? (:feature tile)) (not rogue?)))
                    :rock
                    (:feature tile))))

(defn- update-explored [game]
  (let [level (curlvl game)]
    (update-in game [:dungeon :levels (branch-key game) (:dlvl game) :tiles]
               (partial map-tiles (fn [tile]
                                    (if (and (visible? game level tile)
                                             (not (boulder? tile)))
                                      (update-visible-tile
                                        tile ((:tags level) :rogue))
                                      tile))))))

(defn- rogue-ghost? [game tile glyph]
  (and (or (:feature tile) (seq (:items tile)))
       (not (#{:rock :wall} (:feature tile)))
       (= glyph \space)
       (:rogue (curlvl-tags game))
       (adjacent? (:player game) tile)))

(defn gather-monsters [game frame]
  (into {} (map (fn monster-entry [tile glyph color]
                  (if (or (rogue-ghost? game tile glyph)
                          (and (monster? glyph color)
                               (not= (position tile)
                                     (position (:player game)))))
                    (vector (position tile)
                            (new-monster (:x tile) (:y tile)
                                         (:turn game) glyph color))))
                (->> (:tiles (curlvl game)) (apply concat))
                (->> (:lines frame) (drop 1) (apply concat))
                (->> (:colors frame) (drop 1) (apply concat)))))

(defn- parse-map [game frame]
  (-> game
      (assoc-in [:dungeon :levels (branch-key game) (:dlvl game)
                 :monsters] (gather-monsters game frame))
      (update-in [:dungeon :levels (branch-key game) (:dlvl game) :tiles]
                 (partial map-tiles parse-tile)
                 (->> (:lines frame) (drop 1))
                 (->> (:colors frame) (drop 1)))))

(defn- update-dungeon [{:keys [dungeon] :as game} frame]
  (-> game
      (parse-map frame)
      infer-branch
      infer-tags
      level-blueprint
      (reflood-room (:cursor frame))
      (update-curlvl-at (:cursor frame) dissoc :blocked)
      (update-curlvl-at (:cursor frame) assoc :walked true)))

(defn- looks-engulfed? [{:keys [cursor lines] :as frame}]
  (let [row-before (dec (:x cursor))
        row-after (inc (:x cursor))
        line-above (nth lines (dec (:y cursor)))
        line-at (nth lines (:y cursor))
        line-below (nth lines (inc (:y cursor)))]
    (and (= "/-\\" (subs line-above row-before (inc row-after)))
         (re-seq #"\|.\|" (subs line-at row-before (inc row-after)))
         (= "\\-/" (subs line-below row-before (inc row-after))))))

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
  (or (case msg
        "You enter what seems to be an older, more primitive world." :rogue
        "The odor of burnt flesh and decay pervades the air." :votd
        "Look for a ...ic transporter." :quest
        "So be it." :gehennom
        (condp re-seq msg
          ; TODO other roles (multiline)
          #"You feel your mentor's presence; perhaps .*is nearby.|You sense the presence of |In your mind, you hear the taunts of Ashikaga Takauji" :end
          nil))))

(defn game-handler
  [{:keys [game delegator] :as anbf}]
  (reify
    RedrawHandler
    (redraw [_ frame]
      (swap! game assoc-in [:frame] frame))
    BOTLHandler
    (botl [_ status]
      (let [old-dlvl (:dlvl @game)
            new-dlvl (:dlvl status)
            changed (not= old-dlvl new-dlvl)]
        (if (and changed (some? old-dlvl))
          (swap! game #(assoc-in % [:dungeon :levels (branch-key %) (:dlvl %)
                                    :explored] (log/spy (curlvl-exploration-index %)))))
        (swap! game update-by-botl status)
        (when changed
          (dlvl-changed @delegator old-dlvl new-dlvl)
          (if (and (some? old-dlvl)
                   (= "Home" (subs old-dlvl 0 4))
                   (= "Dlvl" (subs new-dlvl 0 4)))
            (swap! game assoc :branch-id :main) ; kicked out of quest
            (swap! game ensure-curlvl)))))
    KnowPositionHandler
    (know-position [_ frame]
      (swap! game update-in [:player] into (:cursor frame)))
    FullFrameHandler
    (full-frame [_ frame]
      (swap! game update-map frame))
    InventoryHandler
    (inventory-list [_ inventory]
      (swap! game assoc-in [:player :inventory] inventory))
    ToplineMessageHandler
    (message [_ text]
      (or (condp re-seq text
            #"Your .* feels? somewhat better"
            (swap! game assoc-in [:player :leg-hurt] false)
            nil)
          (if-let [level (level-msg text)]
            (update-on-known-position anbf add-curlvl-tag level))
          (if-let [room (room-type text)]
            (update-on-known-position anbf mark-room room))))))
