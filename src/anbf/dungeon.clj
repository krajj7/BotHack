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
   id->branch] ; {:branch-id => :branch}, only ids of recognized levels included
  anbf.bot.IDungeon)

(defn- new-branch-id []
  (-> "branch#" gensym keyword))

(def branches #{:main :mines :sokoban :quest :ludios :vlad
                :wiztower :earth :fire :air :water :astral})

(def subbranches #{:mines :sokoban :ludios :vlad :quest :earth :wiztower})

(def upwards-branches #{:sokoban :vlad})

(def portal-branches #{:quest :ludios :air :fire :water :astral})

(defn upwards? [branch] (upwards-branches branch))

(defn dlvl-number [dlvl]
  (if-let [n (first (re-seq #"\d+" dlvl))]
    (Integer/parseInt n)))

(defn dlvl [game-or-level]
  (dlvl-number (:dlvl game-or-level)))

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
(defn add-level [{:keys [dungeon] :as game} {:keys [branch-id] :as level}]
  (assoc-in
    game [:dungeon :levels branch-id]
    (assoc (get (:levels dungeon) branch-id
                (sorted-map-by (partial dlvl-compare branch-id)))
           (:dlvl level) level)))

(defn new-dungeon []
  (Dungeon. {} (reduce #(assoc %1 %2 %2) {} branches)))

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

(defn branch-key
  ([{:keys [dungeon branch-id] :as game}]
   (branch-key game game))
  ([{:keys [dungeon] :as game} level-or-branch-id]
   (let [branch-id (if (keyword? level-or-branch-id)
                     level-or-branch-id
                     (:branch-id level-or-branch-id))]
     (get (:id->branch dungeon) branch-id branch-id))))

(defn curlvl [game]
  {:pre [(:dungeon game)]}
  (-> game :dungeon :levels (get (branch-key game)) (get (:dlvl game))))

(defn curlvl-monsters [game]
  {:pre [(:dungeon game)]}
  (-> game curlvl :monsters))

(defn add-curlvl-tag [game & tags]
  {:pre [(:dungeon game)]}
  (log/debug "tagging curlvl with" tags)
  (apply update-in game [:dungeon :levels (branch-key game) (:dlvl game) :tags]
         conj tags))

(defn curlvl-tags [game]
  {:pre [(:dungeon game)]}
  (-> game curlvl :tags))

(defn remove-curlvl-monster [game pos]
  {:pre [(:dungeon game)]}
  (update-in game [:dungeon :levels (branch-key game) (:dlvl game) :monsters]
             dissoc (position pos)))

(defn reset-curlvl-monster [game monster]
  {:pre [(:dungeon game)]}
  (assoc-in game [:dungeon :levels (branch-key game) (:dlvl game)
                  :monsters (position monster)]
            monster))

(defn update-curlvl-monster
  "Update the monster on current level at given position by applying update-fn to its current value and args.  Throw exception if there is no monster."
  [game pos update-fn & args]
  {:pre [(get-in game [:dungeon :levels (branch-key game)
                       (:dlvl game) :monsters (position pos)])]}
  (apply update-in game [:dungeon :levels (branch-key game)
                         (:dlvl game) :monsters (position pos)]
         update-fn args))

(defn monster-at [level pos]
  {:pre [(:monsters level)]}
  (get-in level [:monsters (position pos)]))

(defn curlvl-monster-at [game pos]
  (-> game curlvl (monster-at pos)))

(defn update-curlvl-at
  "Update the tile on current level at given position by applying update-fn to its current value and args"
  [game pos update-fn & args]
  {:pre [(:dungeon game)]}
  (apply update-in game [:dungeon :levels (branch-key game) (:dlvl game) :tiles
                         (dec (:y pos)) (:x pos)]
         update-fn args))

(defn at-curlvl [game pos]
  {:pre [(:dungeon game)]}
  (at (curlvl game) pos))

(defn at-player [game]
  {:pre [(:dungeon game)]}
  (at-curlvl game (:player game)))

(defn get-branch
  "Returns {Dlvl => Level} map for branch-id (or current branch)"
  ([game]
   (get-branch game (branch-key game)))
  ([game branch-id]
   (get-in game [:dungeon :levels (branch-key game branch-id)])))

(defn get-level
  "Return Level in the given branch with the given tag or dlvl, if such was visited already"
  [game branch dlvl-or-tag]
  (if-let [levels (get-branch game branch)]
    (if (keyword? dlvl-or-tag)
      (some #(and ((:tags %) dlvl-or-tag) %) (vals levels))
      (levels dlvl-or-tag))))

(defn lit?
  "Actual lit-ness is hard to determine and not that important, this is a pessimistic guess."
  [player level pos]
  (let [tile (at level pos)]
    (or (adjacent? pos player) ; TODO actual player light radius
        (= \. (:glyph tile))
        (and (= \# (:glyph tile)) (= :white (:color tile))))))

(defn map-tiles
  "Call f on each tile (or each tuple of tiles if there are more args) in 21x80 vector structures to again produce 21x80 vector of vectors"
  [f & tile-colls]
  (apply (partial mapv (fn [& rows]
                         (apply (partial mapv #(apply f %&)) rows)))
         tile-colls))

(def ^:private main-features ; these don't appear in the mines
  #{:door-closed :door-open :door-locked :altar :sink :fountain :throne})

(defn- has-features? [level]
  "checks for features not occuring in the mines (except town/end)"
  (some #(main-features (:feature %)) (apply concat (:tiles level))))

(defn- same-glyph-diag-walls
  " the check we make is that any level where there are diagonally adjacent
  walls with the same glyph, it's mines. that captures the following:
  .....
  ..---
  ..-..
  .....
  thanks TAEB!"
  [level]
  (some (fn has-same-glyph-diag-neighbor? [tile]
          (->> (neighbors level tile)
               (remove #(straight (towards tile %)))
               (some #(and (= :wall (:feature tile) (:feature %))
                           (= (:glyph tile) (:glyph %))))))
        (apply concat (take-nth 2 (:tiles level)))))

(def soko1-14 "                                |..^^^^0000...|                                 ")
(def soko2-12 "                                |..^^^<|.....|                                  ")

(defn- in-soko? [game]
  (and (<= 5 (dlvl-number (:dlvl game)) 9)
       (or (= soko1-14 (get-in game [:frame :lines 14]))
           (= soko2-12 (get-in game [:frame :lines 12])))))

(defn- recognize-branch [game level]
  (cond (in-soko? game) :sokoban
        (has-features? level) :main
        (same-glyph-diag-walls level) :mines))

(defn branch-entry
  "Return Dlvl of :main containing entrance to branch, if already visited"
  [game branch]
  (if-let [l (get-level game :main (branch-key game branch))]
    (:dlvl l)))

(defn- merge-branch-id [{:keys [dungeon] :as game} branch-id branch]
  (log/debug "merging branch-id" branch-id "to branch" branch)
  ;(log/debug dungeon)
  (-> game
      (assoc-in [:dungeon :id->branch branch-id] branch)
      (update-in [:dungeon :levels branch]
                 #(into (-> dungeon :levels branch-id) %))
      (update-in [:dungeon :levels :main (branch-entry game branch-id) :tags]
                 #(-> % (disj branch-id) (conj branch)))
      (update-in [:dungeon :levels] dissoc branch-id)))

(defn infer-branch [game]
  (if (branches (branch-key game))
    game ; branch already known
    (let [level (curlvl game)]
      (if-let [branch (recognize-branch game level)]
        (merge-branch-id game (:branch-id level) branch)
        game)))) ; failed to recognize

(defn in-maze-corridor? [level pos]
  (->> (neighbors level pos) (filter #(= (:feature %) :wall)) count (< 5)))

(defn infer-tags [game]
  (let [level (curlvl game)
        dlvl (dlvl level)
        tags (:tags level)
        branch (branch-key game)
        has-features? (has-features? level)]
    (cond-> game
      (and (= :main branch) (<= 21 dlvl 28)
           (not (tags :medusa))
           (not-any? #(not (or (= :water (log/spy (:feature (at level 2 %))))
                               (monster-at level (position 2 %))))
                     (range 2 21))) (add-curlvl-tag :medusa :medusa-1)
      (and (= :main branch) (<= 21 dlvl 28)
           (not (tags :medusa))
           (nil? (:feature (at level 5 20)))
           (not-any? #(and (not= :water (:feature (at level 5 %)))
                           (not= :floor (:feature (at level 4 %))))
                     [13 14 15])) (add-curlvl-tag :medusa :medusa-2)
      (and (= :main branch) (<= 10 dlvl 12)
           (not (tags :bigroom))
           (some (fn lots-floors? [row]
                   (->> (for [x (range 3 78)] (at level x row))
                        (take-while #(not= :corridor %))
                        (filter #(or (= :floor (:feature %))
                                     (monster-at level %)))
                        count (<= 46)))
                 [8 16])) (add-curlvl-tag :bigroom)
      (and (= :main branch) (<= 5 dlvl 9)
           (some #(= "Oracle" (:type %))
                 (:monsters level))) (add-curlvl-tag :oracle)
      (and (<= 5 dlvl 9) (= :mines branch) (not (tags :minetown))
           has-features?) (add-curlvl-tag :minetown)
      (and (<= 10 dlvl 13) (= :mines branch) (not (tags :end))
           has-features?) (add-curlvl-tag :end))))

(defn initial-branch-id
  "Choose branch-id for a new dlvl reached by stairs."
  [game dlvl]
  ; TODO could check for already found parallel branches and disambiguate
  (or (subbranches (branch-key game))
      (if-not (<= 3 (dlvl-number dlvl) 9) :main)
      (new-branch-id)))

(defn ensure-curlvl
  "If current branch-id + dlvl has no level associated, create a new empty level"
  [{:keys [dlvl] :as game}]
  (log/debug "ensuring curlvl:" dlvl "- branch:" (branch-key game))
  (if-not (get-in game [:dungeon :levels (branch-key game) dlvl])
    (add-level game (new-level dlvl (branch-key game)))
    game))

(defn- floodfill-room [game pos kind]
  (log/debug "room floodfill from:" pos "type:" kind)
  (loop [res game
         closed #{}
         open #{(at-curlvl game pos)}]
    ;(log/debug (count open) open)
    (if-let [x (first open)]
      (recur (update-curlvl-at res x assoc :room kind)
             (conj closed x)
             (if (or (door? x) (= :wall (:feature x)))
               (disj open x)
               (into (disj open x)
                     ; walking triggers more refloods to mark unexplored tiles
                     (->> (neighbors (curlvl res) x)
                          (remove #(or (= \space (:glyph %))
                                       (:dug %)
                                       (= :corridor (:feature %))
                                       (closed %)))))))
      res)))

(defn reflood-room [game pos]
  (let [tile (at-curlvl game pos)]
    (if (and (:room tile) (not (:walked tile)))
      (do (log/debug "room reflood from:" pos "type:" (:room tile))
          (floodfill-room game pos (:room tile)))
      game)))

(defn- closest-roomkeeper
  "Presumes having just entered a room"
  [game]
  (min-by #(distance (:player game) %)
          (filter #(and (= \@ (:glyph %)) ; can also find nurses
                        (= :white (:color %)))
                  (vals (curlvl-monsters game)))))

(def ^:private room-re #"Welcome(?: again)? to(?> [A-Z]\S+)+ ([a-z -]+)!")

(defn room-type [msg]
  ; TODO temples, maybe treasure zoos etc.
  (or (if (.endsWith msg ", welcome to Delphi!\"") :oracle)
      (shop-types (re-first-group room-re msg))))

(defn mark-room [game kind]
  (-> game
      (add-curlvl-tag kind)
      (#(if-let [roomkeeper (and (shops kind) (closest-roomkeeper %))]
        (floodfill-room % roomkeeper kind)
        %))))
