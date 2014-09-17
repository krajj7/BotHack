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

(defn- gen-branch-id []
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
  ([{:keys [branch-id] :as game}]
   (branch-key game branch-id))
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
  (update-in game [:dungeon :levels (branch-key game) (:dlvl game)]
             reset-monster monster))

(defn update-curlvl-monster
  "Update the monster on current level at given position by applying update-fn to its current value and args.  Throw exception if there is no monster."
  [game pos update-fn & args]
  {:pre [(get-in game [:dungeon :levels (branch-key game)
                       (:dlvl game) :monsters (position pos)])]}
  (apply update-in game [:dungeon :levels (branch-key game)
                         (:dlvl game) :monsters (position pos)]
         update-fn args))

(defn curlvl-monster-at [game pos]
  (-> game curlvl (monster-at pos)))

(defn update-curlvl-at
  "Update the tile on current level at given position by applying update-fn to its current value and args"
  [game pos update-fn & args]
  {:pre [(:dungeon game)]}
  (update-in game [:dungeon :levels (branch-key game) (:dlvl game)]
             #(apply update-at % pos update-fn args)))

(defn update-at-player
  "Update the tile at player's position by applying update-fn to its current value and args"
  [game update-fn & args]
  {:pre [(:dungeon game)]}
  (update-in game [:dungeon :levels (branch-key game) (:dlvl game)]
             #(apply update-at % (:player game) update-fn args)))

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

(def soko1-14 "                                |..^^^^8888...|")
(def soko2-12 "                                |..^^^<|.....|")

(defn- in-soko? [game]
  (and (<= 5 (dlvl-number (:dlvl game)) 9)
       (or (.startsWith (get-in game [:frame :lines 14]) soko1-14)
           (.startsWith (get-in game [:frame :lines 12]) soko2-12))))

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

(def soko2a-16 "                          |...|..8-.8.^^^^^^^^^^^^.|")
(def soko2b-16 "                        |....|..8.8.^^^^^^^^^^^^^^^.|")
(def soko3a-12 "                              |....-8--8-|...<...|")
(def soko3b-9 "                              |..|.8.8.|88.|.....|")
(def soko4a-18 "                          |..8.....|     |-|.....|--") ; BoH variant
(def soko4b-5 "                            |..^^^^^^^^^^^^^^^^^^..|") ; "oR variant

(def soko-recog ; [y trimmed-line :tag]
  [[14 soko1-14 :soko-1b]
   [12 soko2-12 :soko-1a]
   [16 soko2a-16 :soko-2a]
   [16 soko2b-16 :soko-2b]
   [12 soko3a-12 :soko-3a]
   [9 soko3b-9 :soko-3b]
   [18 soko4a-18 :soko-4a]
   [5 soko4b-5 :soko-4b]])

(defn- recognize-soko [game]
  (or (some (fn [[y line tag]]
              (if (.startsWith (get-in game [:frame :lines y]) line)
                tag))
            soko-recog)
      (throw (IllegalStateException. "unrecognized sokoban level!"))))

(defn infer-tags [game]
  (let [level (curlvl game)
        dlvl (dlvl level)
        tags (:tags level)
        branch (branch-key game)
        has-features? (has-features? level)]
    (cond-> game
      (and (= :main branch) (<= 25 dlvl 29)
           (not (some tags #{:medusa :votd :castle}))
           (not-any? #(not (or (= :water (:feature (at level 2 %)))
                               (monster-at level (position 2 %))))
                     (range 8 15))) (add-curlvl-tag :castle)
      (and (= :main branch) (<= 21 dlvl 28)
           (not (tags :medusa))
           (not-any? #(not (or (= :water (:feature (at level 2 %)))
                               (monster-at level (position 2 %))))
                     (range 2 21))) (add-curlvl-tag :medusa :medusa-1)
      (and (= :main branch) (<= 21 dlvl 28)
           (not (tags :medusa))
           (nil? (:feature (at level 5 20)))
           (not-any? #(or (not= :water (:feature (at level 7 %)))
                          (not= :floor (:feature (at level 6 %))))
                     [15 16 17])) (add-curlvl-tag :medusa :medusa-2)
      (and (= :main branch) (<= 25 dlvl 29)
           (not (tags :castle))
           (= (map :feature (map (partial apply at level)
                                 [[12 12] [13 12] [14 12]]))
              '(:floor :water :drawbridge-raised))) (add-curlvl-tag :castle)
      (and (= :sokoban branch)
           (not (some #{:soko-1a :soko-1b :soko-2a :soko-2b
                        :soko-3a :soko-3b :soko-4a :soko-4b}
                      (:tags level)))) ((fn sokotag [game]
                                          (let [tag (recognize-soko game)
                                                res (add-curlvl-tag game tag)]
                                            (if (#{:soko-4a :soko-4b} tag)
                                              (add-curlvl-tag res :end)
                                              res))))
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
      (and (<= 5 dlvl 9) (= :stairs-up (:feature (at level 3 2)))
           (tags :minetown)) (add-curlvl-tag :minetown-grotto)
      (and (<= 10 dlvl 13) (= :mines branch) (not (tags :end))
           has-features?) (add-curlvl-tag :end))))

(defn initial-branch-id
  "Choose branch-id for a new dlvl reached by stairs."
  [game dlvl]
  ; TODO could check for already found parallel branches and disambiguate
  (or (subbranches (branch-key game))
      (if-not (<= 3 (dlvl-number dlvl) 9) :main)
      (gen-branch-id)))

(defn ensure-curlvl
  "If current branch-id + dlvl has no level associated, create a new empty level"
  [{:keys [dlvl] :as game}]
  (log/debug "ensuring curlvl:" dlvl "- branch:" (branch-key game))
  (if-not (get-in game [:dungeon :levels (branch-key game) dlvl])
    (add-level game (new-level dlvl (branch-key game)))
    game))

(defn- shopkeeper-look? [game tile-or-monster]
  (and (not= (position (:player game)) (position tile-or-monster))
       (= \@ (:glyph tile-or-monster))
       (= :white (:color tile-or-monster))))

(defn- room-rectangle [game NW-corner SE-corner kind]
  (log/debug "room rectangle:" NW-corner SE-corner kind)
  (when (< 8 (max (- (:x SE-corner) (:x NW-corner))
                  (- (:y SE-corner) (:y NW-corner))))
    (log/error "spilled room at" (:dlvl game) (branch-key game)))
  (reduce #(update-curlvl-at %1 %2 assoc :room kind)
          game
          (rectangle NW-corner SE-corner)))

(defn- floodfill-room [game pos kind]
  (log/debug "room floodfill from:" pos "type:" kind)
  (let [level (curlvl game)]
    (loop [closed #{}
           NW-corner (at level pos)
           SE-corner (at level pos)
           open (into #{(at level pos)}
                      (if (shopkeeper-look? game (at level pos))
                        (neighbors level pos)))]
      ;(log/debug (count open) open)
      (if-let [x (first open)]
        (recur (conj closed x)
               {:x (min (:x NW-corner) (:x x))
                :y (min (:y NW-corner) (:y x))}
               {:x (max (:x SE-corner) (:x x))
                :y (max (:y SE-corner) (:y x))}
               (if (or (door? x) (= :wall (:feature x)))
                 (disj open x)
                 (into (disj open x)
                       ; walking triggers more refloods to mark unexplored tiles
                       (->> (neighbors level x)
                            (remove #(or (= \space (:glyph %))
                                         (:dug %)
                                         (= :corridor (:feature %))
                                         (closed %)))))))
        (room-rectangle game NW-corner SE-corner kind)))))

(defn reflood-room [game pos]
  (let [tile (at-curlvl game pos)]
    ; shops are lit so shouldn't be necessary
    (if (and (:room tile) (not (shop? tile)) (not (:walked tile)))
      (do (log/debug "room reflood from:" pos "type:" (:room tile))
          (floodfill-room game pos (:room tile)))
      game)))

(defn- closest-roomkeeper
  "Presumes having just entered a room"
  [game]
  (min-by #(distance (:player game) %)
          (for [m (vals (curlvl-monsters game))
                :when (shopkeeper-look? game m)] m)))

(def ^:private room-re #"Welcome(?: again)? to(?> [A-Z]\S+)+ ([a-z -]+)!")

(defn room-type [msg]
  ; TODO temples, maybe treasure zoos etc.
  (or (if (.endsWith msg ", welcome to Delphi!\"") :oracle)
      (shop-types (re-first-group room-re msg))))

(defn mark-room [game kind]
  (log/debug "marking room as" kind)
  (as-> game res
      (add-curlvl-tag res kind)
      (if-let [roomkeeper (and (shops kind) (closest-roomkeeper res))]
        (floodfill-room res roomkeeper kind)
        res)
      (if (and (adjacent? (:last-position game) (:player game)))
        (update-curlvl-at res (:last-position game) assoc :room nil)
        res)))

(defn- match-level
  "Returns true if the level matches the blueprint :dlvl, :branch and :tag (if present)"
  [game level blueprint]
  (and (or (not (:role blueprint))
           true) ; TODO check :role !
       (or (not (:branch blueprint))
           (= (:branch blueprint) (branch-key game level)))
       (or (not (:dlvl blueprint))
           (= (:dlvl blueprint) (:dlvl level)))
       (or (not (:tag blueprint))
           ((:tags level) (:tag blueprint)))))

(defn- match-blueprint [game level]
  (when-let [blueprint (find-first (partial match-level game level) blueprints)]
    (log/debug "applying blueprint, level:" (:dlvl level)
               "; branch:" (branch-key game level) "; tags:" (:tags level))
    (as-> level res
      (assoc res :blueprint blueprint)
      (reduce (fn mark-feature [level [pos feature]]
                (update-at level pos assoc :feature feature))
              res
              (:features blueprint))
      (reduce (fn add-monster [level [pos monster]]
                (reset-monster level (known-monster (:x pos) (:y pos) monster)))
              res
              (:monsters blueprint)))))

(defn level-blueprint [game]
  (let [level (curlvl game)]
    (if-let [new-level (and (not (:blueprint level))
                            (match-blueprint game level))]
      (assoc-in game [:dungeon :levels (branch-key game new-level)
                      (:dlvl new-level)] new-level)
      game)))

(defn diggable-floor? [game level]
  (not (or (#{:quest :sokoban} (branch-key game level))
           (#{:undiggable-floor :end :castle :votd} (:tags level)))))
