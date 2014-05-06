(ns anbf.dungeon
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [anbf.frame :refer :all]
            [anbf.delegator :refer :all]))

(defrecord Tile
  [glyph
   color
   feature ; :floor :wall :fountain :sink :trap ... TODO
   walked
   searched ; no. of times searched
   items ; [Items]
   monster
   engraving]
  anbf.bot.ITile)

(defn- unknown-tile []
  (Tile. \space nil nil false 0 [] nil nil))

(defrecord Level
  [dlvl
   branch-id
   tiles
   monsters
   items]
  anbf.bot.ILevel)

(defmethod print-method Level [level w]
  (.write w (str "#anbf.dungeon.Level"
                 (assoc (.without level :tiles) :tiles "<trimmed>"))))

(defn- initial-tiles []
  (vec (conj (repeat 20 (vec (repeat 80 (unknown-tile)))) nil)))

(defn new-level [dlvl branch-id]
  (Level. dlvl branch-id (initial-tiles) [] []))

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
(defn add-level [dungeon level]
  (assoc-in
    dungeon [:levels]
    (assoc (get (:levels dungeon) (:branch-id level)
                (sorted-map-by (partial dlvl-compare (:branch-id level))))
           (:dlvl level) level)))

(defn new-dungeon []
  (add-level (Dungeon. {} (reduce #(assoc %1 %2 %2) (hash-map) branches)
                       {:earth "Dlvl:1"} :main "Dlvl:1")
             (new-level "Dlvl:1" :main)))

(defn- update-level [level frame]
  ; TODO guess id
  level)

; TODO event
(defn- update-dlvl [dungeon status delegator]
  (assoc dungeon :dlvl (:dlvl status)))

(defn dungeon-handler
  [game delegator]
  (reify
    MapHandler
    (map-drawn [_ frame]
      (swap! game update-in [:dungeon :curlvl] update-level frame))
    BOTLHandler
    (botl [_ status]
      (swap! game update-in [:dungeon] update-dlvl status delegator))))
