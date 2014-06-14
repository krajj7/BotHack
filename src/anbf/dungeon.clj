(ns anbf.dungeon
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [anbf.monster :refer :all]
            [anbf.util :refer :all]
            [anbf.level :refer :all]
            [anbf.tile :refer :all]
            [anbf.position :refer :all]
            [anbf.delegator :refer :all]))

; branch ID is either a branch keyword from branches or random keyword that will map (via id->branch) to a standard branch keyword when the level branch is recognized.
; a Level should be permanently uniquely identified by its branch-id + dlvl.
(defrecord Dungeon
  [levels ; {:branch-id => sorted{"dlvl" => Level}}, recognized branches merged
   id->branch ; {:branch-id => :branch}, only ids of recognized levels included
   branch-start ; {:branch => dlvl of entrypoint}, only for recognized branches
   branch-id ; current
   dlvl] ; current
  anbf.bot.IDungeon)

(defn- new-branch-id []
  (-> "branch#" gensym keyword))

(def branches #{:main :mines :sokoban :quest :ludios :vlad
                :wiztower :earth :fire :air :water :astral})

(def upwards-branches #{:sokoban :vlad})

(defn upwards? [branch] (upwards-branches branch))

(defn dlvl-number [dlvl]
  (if-let [n (first (re-seq #"\d+" dlvl))]
    (Integer/parseInt n)))

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

; TODO if level looks visited find it by dlvl instead of adding
(defn add-level [dungeon {:keys [branch-id] :as level}]
  (assoc-in
    dungeon [:levels branch-id]
    (assoc (get (:levels dungeon) branch-id
                (sorted-map-by (partial dlvl-compare branch-id)))
           (:dlvl level) level)))

(defn new-dungeon []
  (add-level (Dungeon. {} (reduce #(assoc %1 %2 %2) {} branches)
                       {:earth "Dlvl:1"} :main "Dlvl:1")
             (new-level "Dlvl:1" :main)))

(defn change-dlvl
  "Apply function to dlvl number if there is one, otherwise no change.  The result dlvl may not actually exist."
  [f dlvl]
  (if-let [n (dlvl-number dlvl)]
    (string/replace dlvl #"\d+" (str (f n)))
    dlvl))

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

(defn infer-branch [game]
  game) ; TODO assoc id->branch, merge id to branch in levels

(defn branch-key [{:keys [branch-id] :as dungeon}]
  (get (:id->branch dungeon) branch-id branch-id))

(defn curlvl [{:keys [dungeon] :as game}]
  (-> game :dungeon :levels (get (branch-key dungeon)) (get (:dlvl dungeon))))

(defn curlvl-monsters [game]
  (-> game curlvl :monsters))

(defn add-curlvl-tag [game tag]
  (update-in game [:dungeon :levels (branch-key (:dungeon game))
                   (:dlvl (:dungeon game)) :tags] conj tag))

(defn reset-curlvl-monster
  [game monster]
  (assoc-in game [:dungeon :levels (branch-key (:dungeon game))
                  (:dlvl (:dungeon game)) :monsters (position monster)]
            monster))

(defn update-curlvl-monster
  "Update the monster on current level at given position by applying update-fn to its current value and args.  Throw exception if there is no monster."
  [game pos update-fn & args]
  {:pre [(get-in game [:dungeon :levels (branch-key (:dungeon game))
                       (:dlvl (:dungeon game)) :monsters (position pos)])]}
  (apply update-in game [:dungeon :levels (branch-key (:dungeon game))
                         (:dlvl (:dungeon game)) :monsters (position pos)]
         update-fn args))

(defn update-curlvl-at
  "Update the tile on current level at given position by applying update-fn to its current value and args"
  [game pos update-fn & args]
  (apply update-in game [:dungeon :levels (branch-key (:dungeon game))
                         (:dlvl (:dungeon game)) :tiles (dec (:y pos)) (:x pos)]
         update-fn args))

(defn at-curlvl [game pos]
  (at (curlvl game) pos))

(defn at-player [game]
  (at-curlvl game (:player game)))

(defn lit?
  "Actual lit-ness is hard to determine and not that important, this is a pessimistic guess."
  [game pos]
  (let [tile (at-curlvl game pos)]
    (or (adjacent? tile (:player game)) ; TODO actual player light radius
        (= \. (:glyph tile))
        (and (= \# (:glyph tile)) (= :white (color tile))))))

(defn map-tiles
  "Call f on each tile (or each tuple of tiles if there are more args) in 21x80 vector structures to again produce 21x80 vector of vectors"
  [f & tile-colls]
  (apply (partial mapv (fn [& rows]
                         (apply (partial mapv #(apply f %&)) rows)))
         tile-colls))

(defn ensure-curlvl
  "If current branch-id + dlvl has no level associated, create a new empty level"
  [dungeon]
  (if-not (get-in dungeon [:levels (branch-key dungeon) (:dlvl dungeon)])
    (add-level dungeon (new-level (:dlvl dungeon) (:branch-id dungeon)))
    dungeon))

; TODO change branch-id on staircase ascend/descend event or special levelport (quest/ludios) or trapdoor
(defn update-dlvl [dungeon status delegator]
  (-> dungeon
      (assoc :dlvl (:dlvl status))
      ensure-curlvl))

(defn- gather-monsters [game frame]
  (into {} (map (fn monster-entry [tile glyph color]
                  (if (and (monster? glyph)
                           (not= (position tile) (position (:player game))))
                    (vector (position tile)
                            (new-monster (:x tile) (:y tile)
                                         (:turn game) glyph color))))
                (apply concat (-> game curlvl :tiles))
                (apply concat (drop 1 (:lines frame)))
                (apply concat (drop 1 (:colors frame))))))

(defn- update-monsters [{:keys [dungeon] :as game} frame]
  (-> game
      (assoc-in [:dungeon :levels (branch-key dungeon) (:dlvl dungeon)
                 :monsters] (gather-monsters game frame))))

(defn- floodfill-room [game pos kind]
  (log/debug "room floodfill from:" pos "type:" kind)
  (loop [res game
         open #{pos}]
    ;(log/debug (count open) open)
    (if-let [x (first open)]
      (recur (update-curlvl-at res x assoc :room kind)
             (if (door? (at-curlvl res x))
               (disj open x)
               (into (disj open x)
                     ; walking triggers more refloods to mark unexplored tiles
                     (remove #(or (= \space (:glyph %))
                                  (#{:corridor :wall} (:feature %))
                                  (:room %))
                             (neighbors (curlvl res) x)))))
      res)))

(defn- reflood-room [game pos]
  (let [tile (at-curlvl game pos)]
    (if (and (:room tile) (not (:walked tile)))
      (do (log/debug "room reflood from:" pos "type:" (:room tile))
          (floodfill-room game pos (:room tile)))
      game)))

(defn update-dungeon [{:keys [dungeon] :as game} frame]
  (-> game
      (update-monsters frame)
      (update-in [:dungeon :levels (branch-key dungeon) (:dlvl dungeon) :tiles]
                 (partial map-tiles parse-tile)
                 (drop 1 (:lines frame)) (drop 1 (:colors frame)))
      (reflood-room (:cursor frame))
      (update-curlvl-at (:cursor frame) assoc :walked true)
      infer-branch))

(defn- closest-roomkeeper
  "Presumes having just entered a room"
  [game]
  (min-by #(distance (:player game) %)
          (filter #(and (= \@ (:glyph %)) ; can also find nurses
                        (= :white (color %)))
                  (vals (curlvl-monsters game)))))

(defn room-tag [kind]
  (cond
    (shops kind) :shop
    :else (throw (IllegalArgumentException.
                   (str "don't know tag for room type " kind)))))

(defn mark-room [game kind]
  (let [roomkeeper (closest-roomkeeper game)]
    (-> game
        (floodfill-room roomkeeper kind)
        (add-curlvl-tag (room-tag kind)))))
