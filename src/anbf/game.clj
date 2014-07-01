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
  (update-in game [:dungeon :levels (branch-key game) (:dlvl game) :tiles]
             (partial map-tiles (fn [tile]
                                  (if (visible? game tile)
                                    (update-visible-tile
                                      tile (:rogue (curlvl-tags game)))
                                    tile)))))

(defn- rogue-ghost? [game tile glyph]
  (and (:feature tile)
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
                (apply concat (-> game curlvl :tiles))
                (apply concat (drop 1 (:lines frame)))
                (apply concat (drop 1 (:colors frame))))))

(defn- parse-map [game frame]
  (-> game
      (assoc-in [:dungeon :levels (branch-key game) (:dlvl game)
                 :monsters] (gather-monsters game frame))
      (update-in [:dungeon :levels (branch-key game) (:dlvl game) :tiles]
                 (partial map-tiles parse-tile)
                 (drop 1 (:lines frame)) (drop 1 (:colors frame)))))

(defn- update-dungeon [{:keys [dungeon] :as game} frame]
  (-> game
      (parse-map frame)
      infer-branch
      infer-tags
      (reflood-room (:cursor frame))
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

(defn- handle-frame [game frame]
  (-> game
      (update-in [:player] into (:cursor frame)) ; update position
      (update-map frame)))

(defn- level-msg [msg]
  (case msg
    "You enter what seems to be an older, more primitive world." :rogue
    "The odor of burnt flesh and decay pervades the air." :votd
    nil))

(defn game-handler
  [{:keys [game delegator] :as anbf}]
  (reify
    RedrawHandler
    (redraw [_ frame]
      (swap! game assoc-in [:frame] frame))
    BOTLHandler
    (botl [_ status]
      (let [old-dlvl (:dlvl @game)
            new-dlvl (:dlvl status)]
        (swap! game update-by-botl status)
        (when (not= old-dlvl new-dlvl)
          (dlvl-changed @delegator old-dlvl new-dlvl)
          (swap! game ensure-curlvl))))
    FullFrameHandler
    (full-frame [_ frame]
      (swap! game handle-frame frame))
    InventoryHandler
    (inventory-list [_ inventory]
      (swap! game assoc-in [:player :inventory] inventory))
    ToplineMessageHandler
    (message [_ text]
      (or (condp re-seq text
            #"Your legs? feels? somewhat better"
            (swap! game assoc-in [:player :leg-hurt] false)
            nil)
          (if-let [level (level-msg text)]
            (update-on-known-position anbf add-curlvl-tag level))
          (if-let [room (room-type text)]
            (update-on-known-position anbf mark-room room))))))
