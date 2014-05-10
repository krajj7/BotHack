(ns anbf.dungeon
  (:import [anbf NHFov NHFov$TransparencyInfo])
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [anbf.util :refer [->Position]]
            [anbf.frame :refer :all]
            [anbf.monster :refer :all]
            [anbf.delegator :refer :all]))

(defrecord Tile
  [position
   glyph
   color
   feature ; :rock :floor :wall :stairs-up :stairs-down :corridor :altar :water :trap :door-open :door-closed :sink :fountain :grave :throne :bars :tree :drawbridge :lava :ice :underwater
   in-fov ; this only updates on full-frame, not on map-drawn when it may be stale data TODO maybe move FOV map to player
   lit ; TODO TODO
   seen ; TODO
   explored ; TODO turn no. when last visible (without any items) or walked
   walked ; TODO turn no.
   searched ; no. of times searched TODO
   items ; [Items]
   monster
   engraving]
  anbf.bot.ITile)

(defn- initial-tile [x y]
  (Tile. (->Position x y) \space nil nil false false nil nil nil 0 [] nil nil))

(defn visible? [tile]
  (and (:lit tile) (:in-fov tile)))

(defrecord Level
  [dlvl
   branch-id
   tiles]
  anbf.bot.ILevel)

(defmethod print-method Level [level w]
  (.write w (str "#anbf.dungeon.Level"
                 (assoc (.without level :tiles) :tiles "<trimmed>"))))

(defn print-tiles
  "Print map, with pred overlayed with X where pred is not true for the tile"
  ([level]
   (print-tiles identity level))
  ([pred level]
   (doall (map (fn [row]
                 (doall (map (fn [tile]
                               (print (if (pred tile)
                                        (:glyph tile)
                                        \X))) row))
                 (println))
               (:tiles level)))))

(defn print-los [level]
  (print-tiles visible? level))

(defn print-fov [level]
  (print-tiles :in-fov level))

(defn- initial-tiles []
  (vec (map (fn [y] (vec (map (fn [x] (initial-tile x y))
                              (range 80)))) (range 20))))

(defn new-level [dlvl branch-id]
  (Level. dlvl branch-id (initial-tiles)))

(defn at
  "Tile at given position on the terminal"
  ([level pos]
   (at level (:x pos) (:y pos)))
  ([level x y]
   {:pre [(>= y 1) (<= y 22) (<= x 80) (>= x 0)]}
   (-> level :tiles (nth (dec y)) (nth x))))

(defn- new-branch-id []
  (-> "branch#" gensym keyword))

(def branches [:main :mines :sokoban :quest :ludios
               :vlad :earth :fire :air :water :astral])

(def branch-entry {:mines :main, :sokoban :main}) ; TODO

(defn dlvl-number [dlvl]
  (if-let [n (first (re-seq #"\d+" dlvl))]
    (Integer/parseInt n)))

(defn change-dlvl
  "Apply function to dlvl number if there is one, otherwise no change.  The result dlvl may not actually exist."
  [f dlvl]
  (if-let [n (dlvl-number dlvl)]
    (string/replace dlvl #"\d+" (str (f n)))
    dlvl))

(def upwards-branches [:sokoban :vlad])

(defn upwards? [branch] (some #(= branch %) upwards-branches))

(defn prev-dlvl
  "Dlvl closer to branch entry (for dlvl within the branch), no change for unnumbered dlvls."
  [branch dlvl]
  (if (upwards? branch)
    (change-dlvl inc dlvl)
    (change-dlvl dec dlvl)))

(defn next-dlvl
  "Dlvl further from branch entry (for dlvl within the branch), no change for unnumbered dlvls."
  [branch dlvl]
  (if (upwards? branch)
    (change-dlvl dec dlvl)
    (change-dlvl inc dlvl)))

(defn dlvl-compare
  "Only makes sense for dlvls within one branch."
  ([branch d1 d2]
   (if (upwards? branch)
     (dlvl-compare d2 d1)
     (dlvl-compare d1 d2)))
  ([d1 d2]
   (if (every? #(.contains % ":") [d1 d2])
     (compare (dlvl-number d1) (dlvl-number d2))
     (compare d1 d2))))

; branch ID is either a branch keyword from branches or random keyword that will map (via id->branch) to a standard branch keyword when the level branch is recognized.
; a Level should be permanently uniquely identified by its branch-id + dlvl.
(defrecord Dungeon
  [levels ; {:branch-id => sorted{"dlvl" => Level}}, recognized branches merged
   id->branch ; {:branch-id => :branch}, only ids of recognized levels included
   branch-start ; {:branch => dlvl of entrypoint}, only for recognized branches
   branch-id ; current
   dlvl] ; current
  anbf.bot.IDungeon)

(defn- recognize-branch [dungeon branch-id branch]
  dungeon) ; TODO assoc id->branch, merge id to branch in levels

; TODO if level looks visited find it by dlvl instead of adding
(defn add-level [dungeon {:keys [branch-id] :as level}]
  (assoc-in
    dungeon [:levels branch-id]
    (assoc (get (:levels dungeon) branch-id
                (sorted-map-by (partial dlvl-compare branch-id)))
           (:dlvl level) level)))

(defn new-dungeon []
  (add-level (Dungeon. {} (reduce #(assoc %1 %2 %2) (hash-map) branches)
                       {:earth "Dlvl:1"} :main "Dlvl:1")
             (new-level "Dlvl:1" :main)))

(defn monster? [glyph]
  (or (Character/isLetterOrDigit glyph)
      (some #(= glyph %) [\& \@ \' \; \:])))

(defn item? [glyph]
  (some #(= glyph %) [\" \) \[ \! \? \/ \= \+ \* \( \` \0 \$ \%]))

(defn- update-monster [tile new-glyph new-color]
  ; TODO events (monster gone, monster appeared)
  (assoc tile :monster
         (if (monster? new-glyph)
           (monster new-glyph new-color)
           nil)))

(defn- update-items [tile new-glyph new-color]
  tile) ; TODO

(defn- feature [tile new-glyph new-color]
  (case new-glyph
    \space (if (visible? tile) :rock (:feature tile)) ; TODO :air
    \. (if (= (colormap new-color) :cyan) :ice :floor)
    \< :stairs-up
    \> :stairs-down
    \\ (if (= (colormap new-color) :yellow) :throne :grave)
    \{ (if (= (colormap new-color) nil) :sink :fountain)
    \} :TODO ; TODO :bars :tree :drawbridge :lava :underwater
    \# :corridor ; TODO :cloud
    \_ :altar
    \~ :water
    \^ :trap
    \] :door-closed
    \| (if (= (colormap new-color) :brown) :door-open :wall)
    \- (if (= (colormap new-color) :brown) :door-open :wall)
    (log/error "unrecognized appearance of tile" tile)))

(defn walkable? [tile]
  (some #(= % (:feature tile))
        [:ice :floor :air :altar :door-open :sink :fountain :corridor :trap
         :throne :grave :stairs-up :stairs-down]))

(defn boulder? [tile]
  (= (:glyph tile) \0))

(defn transparent? [tile]
  (and (not (boulder? tile))
       (not-any? #(= % (:feature tile))
             [nil :rock :wall :tree :door-closed :cloud])))

(defn walkable-by [tile monster]
  ; TODO change feature: closed door to open door, rock/wall/tree to corridor.  consider xorns, slimes (door-ooze), fliers (over water/lava), fish
  tile)

(defn- update-feature [tile new-glyph new-color]
  ; TODO events
  (cond (monster? new-glyph) (walkable-by tile (:monster tile))
        (item? new-glyph) tile
        :else (assoc tile :feature (feature tile new-glyph new-color))))

(defn- update-tile [tile new-glyph new-color]
  (if (or (not= new-color (:color tile)) (not= new-glyph (:glyph tile)))
    (-> tile
        (update-monster new-glyph new-color)
        (update-items new-glyph new-color)
        (update-feature new-glyph new-color)
        (assoc :glyph new-glyph
               :color new-color))
    tile))

(defn- update-tiles [tiles new-lines new-colors]
  (vec (map (fn [tile line color-row]
              (vec (map update-tile tile line color-row)))
            tiles new-lines new-colors)))

(defn branch-key [{:keys [branch-id] :as dungeon}]
  (get (:id->branch dungeon) branch-id branch-id))

(defn curlvl [dungeon]
  (-> dungeon :levels (get (branch-key dungeon)) (get (:dlvl dungeon))))

; TODO change branch-id on staircase ascend/descend event or special levelport (quest/ludios)

; TODO event
(defn- update-dlvl [dungeon status delegator]
  (assoc dungeon :dlvl (:dlvl status)))

(defn- update-los-row [row visibility]
  (map (fn [tile v] (assoc tile :in-fov v)) row visibility))

; TODO TODO also lit/unlit
(defn- update-los [level cursor]
  (update-in level [:tiles]
             #(map update-los-row %
                   (.calculateFov (NHFov.) (:x cursor) (dec (:y cursor))
                                  (reify NHFov$TransparencyInfo
                                    (isTransparent [_ x y]
                                      (transparent? ((% y) x))))))))

; TODO update :explored (was in LOS, seen any items)
(defn- update-map [{:keys [dlvl] :as dungeon} {:keys [cursor] :as frame}]
  (-> dungeon
      (update-in [:levels (branch-key dungeon) dlvl] update-los cursor)
      (update-in [:levels (branch-key dungeon) dlvl :tiles]
                 update-tiles (drop 1 (:lines frame)) (drop 1 (:colors frame)))
      ; TODO guess branch
      ; mark player as friendly
      (assoc-in [:levels (branch-key dungeon) dlvl :tiles
                 (dec (:y cursor)) (:x cursor) :monster :friendly] true)))

(defn dungeon-handler
  [game delegator]
  (reify
    FullFrameHandler
    (full-frame [_ frame]
      ; TODO not when engulfed
      (swap! game update-in [:dungeon] update-map frame)
      (print-los @game))
    BOTLHandler
    (botl [_ status]
      (swap! game update-in [:dungeon] update-dlvl status delegator))))
