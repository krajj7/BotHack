(ns anbf.game
  "representation of the game world"
  (:import [anbf NHFov NHFov$TransparencyInfo])
  (:require [clojure.tools.logging :as log]
            [anbf.player :refer :all]
            [anbf.dungeon :refer :all]
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
   turn
   score]
  anbf.bot.IGame
  (frame [this] (:frame this))
  (player [this] (:player this)))

(defn new-game []
  (Game. nil (new-player) (new-dungeon) :main "Dlvl:1" 0 0))

(defn- update-game [game status]
  (->> game keys (select-keys status) (merge game)))

(defn- update-by-botl [game status]
  (-> game
      (assoc :dlvl (:dlvl status))
      (update-in [:player] update-player status)
      (update-game status)))

(defn- update-fov [game cursor]
  (assoc-in game [:player :fov]
            (.calculateFov (NHFov.) (:x cursor) (dec (:y cursor))
                           (reify NHFov$TransparencyInfo
                             (isTransparent [_ x y]
                               (if (and (<= 0 y 20) (<= 0 x 79))
                                 (boolean
                                   (transparent?
                                     (((-> game curlvl :tiles) y) x)))
                                 false))))))

(defn- update-visible-tile [tile]
  (assoc tile
         :seen true
         :feature (if (= (:glyph tile) \space) :rock (:feature tile))))

(defn- update-explored [game]
  (update-in game [:dungeon :levels (branch-key game) (:dlvl game) :tiles]
             (partial map-tiles (fn [tile]
                                  (if (visible? game tile)
                                    (update-visible-tile tile)
                                    tile)))))

(defn- update-map [game frame]
  (if (-> game :player :engulfed)
    game
    (-> game
        (update-dungeon frame)
        (update-fov (:cursor frame))
        (track-monsters game)
        update-explored)))

(defn- engulfed? [game frame]
  false) ; TODO

(defn- handle-frame [game frame]
  (-> game
      ensure-curlvl
      (update-in [:player] into (:cursor frame)) ; update position
      (assoc-in [:player :engulfed] (engulfed? game frame))
      (update-map frame)))

(defn- level-msg [msg]
  (case msg
    "You enter what seems to be an older, more primitive world." :rogue
    "The odor of burnt flesh and decay pervades the air." :votd
    nil))

(defn game-handler
  [{:keys [game] :as anbf}]
  (reify
    RedrawHandler
    (redraw [_ frame]
      (swap! game assoc-in [:frame] frame))
    BOTLHandler
    (botl [_ status]
      (swap! game update-by-botl status))
    FullFrameHandler
    (full-frame [_ frame]
      (swap! game handle-frame frame))
    ToplineMessageHandler
    (message [_ text]
      (or (if-let [level (level-msg text)]
            (update-on-known-position anbf add-curlvl-tag level))
          (if-let [room (room-type text)]
            (update-on-known-position anbf mark-room room))))))
