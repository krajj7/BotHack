; representation of the game world

(ns anbf.game
  (:import [anbf NHFov NHFov$TransparencyInfo])
  (:require [clojure.tools.logging :as log]
            [anbf.frame :refer [colormap]]
            [anbf.player :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.position :refer :all]
            [anbf.delegator :refer :all]))

(defrecord Game
  [frame
   player
   dungeon
   fov
   turn
   score]
  anbf.bot.IGame
  (frame [this] (:frame this))
  (player [this] (:player this)))

(defn new-game []
  (Game. nil (new-player) (new-dungeon) nil 0 0))

(defn- update-game [game status delegator]
  ; TODO not just merge, emit events on changes
  (->> game keys (select-keys status) (merge game)))

(defn- game-botl [game status delegator]
  (-> game
      (update-in [:dungeon] update-dlvl status delegator)
      (update-in [:player] update-player status delegator)
      (update-game status delegator)))

(defn lit?
  "Actual lit-ness is hard to determine and not that important, this is a pessimistic guess."
  [game tile]
  (or (adjacent? tile (:player game)) ; TODO actual player light radius
      (= \. (:glyph tile))
      (and (= \# (:glyph tile)) (= :white (colormap (:color tile))))))

(defn in-fov? [game pos]
  (get-in (:fov game) [(dec (:y pos)) (:x pos)]))

(defn visible? [game tile]
  "Only considers normal sight, not infravision/ESP/..."
  (and ; TODO not blind
       (in-fov? game tile)
       (lit? game tile)))

(defn- update-fov [game cursor]
  (assoc game :fov
         (.calculateFov (NHFov.) (:x cursor) (dec (:y cursor))
                        (reify NHFov$TransparencyInfo
                          (isTransparent [_ x y]
                            (if (and (<= 0 y 20) (<= 0 x 79))
                              (boolean
                                (transparent?
                                  (((-> game :dungeon curlvl :tiles) y) x)))
                              false))))))

(defn- update-visible-tile [tile]
  (assoc tile
         :seen true
         :feature (if (= (:glyph tile) \space) :rock (:feature tile))))

(defn- update-explored [game]
  (update-in game [:dungeon :levels (branch-key (:dungeon game))
                   (:dlvl (:dungeon game)) :tiles]
             (partial map-tiles (fn [tile]
                                  (if (visible? game tile)
                                    (update-visible-tile tile)
                                    tile)))))

(defn- track-transfer [game old-monster monster]
  (log/debug "transfer:" \newline old-monster "to" \newline monster)
  (update-curlvl-monster game monster into ; TODO type
                         (select-keys old-monster [:peaceful :cancelled])))

(defn- track-monsters
  "Try to transfer monster properties from the old game snapshot to the new, even if the monsters moved slightly."
  [new-game old-game]
  (if (not= (-> old-game :dungeon :dlvl)
            (-> new-game :dungeon :dlvl))
    new-game ; TODO track stair followers?
    (let [old-monsters (vals (-> old-game :dungeon curlvl :monsters))]
      (loop [game new-game
             monsters (vals (-> new-game :dungeon curlvl :monsters))
             dist 0
             ignored-new #{(position (:player new-game))}
             ignored-old #{(position (:player old-game))}]
        (if (> 3 dist)
          (if-let [m (first (remove #(ignored-new (position %)) monsters))]
            (if-let [candidates (seq (filter (fn candidate? [n]
                                               (and (= (:glyph m) (:glyph n))
                                                    (= (:color m) (:color n))
                                                    (= dist (distance m n))))
                                             (remove #(ignored-old
                                                        (position %))
                                                     old-monsters)))]
              (if (next candidates) ; ignore ambiguous cases
                (recur game (rest monsters) dist
                       (conj ignored-new (position m)) ignored-old)
                (recur (track-transfer game (first candidates) m)
                       (rest monsters) dist
                       (conj ignored-new (position m))
                       (conj ignored-old (position (first candidates)))))
              (recur game (rest monsters) dist ignored-new ignored-old))
            (recur game (vals (-> new-game :dungeon curlvl :monsters))
                   (inc dist) ignored-new ignored-old))
          game))))) ; TODO remember/unremember unignored old/new

(defn- update-map [game {:keys [cursor] :as frame} delegator]
  (-> game
      (update-in [:player] #(into % (:cursor frame))) ; update position
      (update-dungeon frame)
      (update-fov cursor)
      (track-monsters game)
      update-explored))

(defn game-handler
  [game delegator]
  (reify
    RedrawHandler
    (redraw [_ frame]
      (swap! game assoc-in [:frame] frame))
    BOTLHandler
    (botl [_ status]
      (swap! game game-botl status delegator))
    FullFrameHandler
    (full-frame [_ frame]
      (swap! game update-map frame delegator))))
