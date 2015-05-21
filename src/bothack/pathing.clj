(ns bothack.pathing
  (:require [clojure.data.priority-map :refer [priority-map-keyfn]]
            [clojure.tools.logging :as log]
            [clojure.set :refer [intersection]]
            [bothack.action :refer :all]
            [bothack.actions :refer :all]
            [bothack.position :refer :all]
            [bothack.monster :refer :all]
            [bothack.delegator :refer :all]
            [bothack.dungeon :refer :all]
            [bothack.item :refer :all]
            [bothack.itemid :refer :all]
            [bothack.level :refer :all]
            [bothack.player :refer :all]
            [bothack.util :refer :all]
            [bothack.tile :refer :all]))

(defn base-cost [level dir tile opts]
  {:pre [(and (some? level) (some? dir) (some? tile))]}
  (cond-> 1
    (and (:prefer-items opts) (not (:new-items tile))) (+ 0.5)
    (and (:prefer-items opts) (:pick opts) (boulder? tile)
         (:new-items tile)) (- 5) ; partially negate digging penalization
    (trap? tile) (+ 15) ; TODO trap types
    (and (some #{:castle :medusa} (:tags level))
         (some pool? (including-origin neighbors level tile))) (+ 5)
    (diagonal dir) (+ 0.1)
    (and (unknown? tile) (not (:seen tile))) (+ 3)
    (not (stairs? tile)) (+ 0.1)
    (not (engravable? tile)) (+ 0.5)
    (cloud? tile) (+ 10)
    (:blocked tile) (+ (* 10 (:blocked tile)))
    (not (or (:dug tile) (:walked tile))) (+ 0.2)
    (and (not (:walked tile)) (floor? tile)) (+ 0.5)))

(defn- a*
  "Move-fn must always return non-negative cost values, target tile may not be
  passable, but will always be included in the path"
  ([from to move-fn] (a* from to move-fn nil))
  ([from to move-fn max-steps]
   (log/debug "a*")
   (loop [closed {}
          open (priority-map-keyfn first (position from) [0 0])]
     (if-not (empty? open)
       (let [[node [total dist prev]] (peek open)
             path (conj (closed prev []) node)
             delta (distance node to)]
         (cond
           (zero? delta) (subvec path 1)
           (and (= 1 delta)
                (not-any? #(move-fn % to)
                          (neighbors to))) (conj (subvec path 1) to)
           (and max-steps (< max-steps (+ delta (count path)))) nil
           :else (recur (assoc closed node path)
                        (merge-with
                          (partial min-key first)
                          (pop open)
                          (into {} (for [nbr (remove closed (neighbors node))
                                         :let [[cost action] (move-fn node nbr)]
                                         :when (some? action)]
                                     (let [new-dist (int (+ dist cost))]
                                       [nbr [(+ new-dist (distance nbr to))
                                             new-dist node]])))))))))))

(defn- dijkstra
  ([from goal? move-fn] (dijkstra from goal? move-fn nil))
  ([from goal? move-fn max-steps]
   (log/debug "dijkstra")
   (loop [closed {}
          open (priority-map-keyfn first (position from) [0])]
     (if-let [[node [dist prev]] (peek open)]
       (let [path (conj (closed prev []) node)]
         (cond
           (goal? node) (subvec path 1)
           (and max-steps (< max-steps (count path))) nil
           :else (recur
                   (assoc closed node path)
                   (merge-with (partial min-key first)
                               (pop open)
                               (into {}
                                   (for [nbr (remove closed (neighbors node))
                                         :let [[cost action] (move-fn node nbr)]
                                         :when (some? action)]
                                     [nbr [(+ dist cost) node]]))))))))))

(defn needs-levi? [tile]
  (#{:pool :lava :ice :hole :trapdoor :cloud} (:feature tile)))

(defn- drop-unpaid [game]
  (if-let [[slot item] (have game :cost)]
    (with-reason "drop unpaid items"
      (->Drop slot (:qty item)))))

(defn fidget
  "Move around randomly or wait to make a peaceful move out of the way"
  ([game] (fidget game (curlvl game)))
  ([game level] (fidget game level nil))
  ([{:keys [player] :as game} level target]
   (with-handler priority-top
     (fn [bh]
       (if target
         (reify AboutToChooseActionHandler
           (about-to-choose [_ new-game]
             (if-let [m (monster-at new-game target)]
               (when (:peaceful m) ; target did not move
                 (log/debug "blocked by peaceful" target)
                 (if-not (and (shopkeeper? m) ; shks may need more patience
                              (shop? (at-player new-game)))
                   (swap! (:game bh) update-at target
                          update :blocked (fnil inc 0)))))))))
     (with-reason "fidgeting to make peacefuls move"
       (or (if-let [shk (and (not= :pay (typekw (:last-action* game)))
                             (find-first
                               (comp shopkeeper? (partial monster-at level))
                               (neighbors level player)))]
             (or (drop-unpaid game) (->Pay shk)))
           (arbitrary-move game level)
           (->Search))))))

(defn safe-from-guards?
  "Only just enough to handle the most usual corner-case where the only door in
  the stair-room of minetown is locked.  Potentially dangerous without
  infravision."
  [level]
  (not-any? guard? (vals (:monsters level))))

(defn dare-destroy? [level tile]
  (or (boulder? tile)
      (and (or (not ((:tags level) :minetown))
               (safe-from-guards? level))
           (not (shop? tile)))))

(defn likely-walkable?
  "Less optimistic about unexplored tiles than walkable?, but still returns
  true for item in an (unknown) wall."
  [level tile]
  (and (walkable? tile)
       (or (if-let [m (monster-at level tile)]
             (not= \; (:glyph m)))
           (:feature tile)
           (item? tile))))

(defn safely-walkable? [level tile]
  (if tile
    (and (likely-walkable? level tile)
         ((not-any-fn? trap? ice? drawbridge-lowered?) tile))))

(defn- blocked-door
  "If this is a kickable door blocked from one side, return direction from
  which to kick it"
  [level pos]
  (if-let [ws (seq (filter (every-pred walkable? (complement trap?))
                           (straight-neighbors level pos)))]
    (let [tile (at level pos)
          w (first ws)
          dir (towards pos w)
          o (in-direction level pos (opposite dir))]
      (if (and (empty? (:items tile)) ; don't try to break blocked doors
               (= 1 (count ws))
               (some (partial likely-walkable? level)
                     (intersection (set (diagonal-neighbors level pos))
                                   (set (straight-neighbors level o)))))
        dir))))

(defn- kickable-door? [level tile opts]
  (and (door? tile)
       (not (:rogue (:tags level)))
       (not (:walking opts))
       (dare-destroy? level tile)
       (not (item? tile)))) ; don't try to break blocked doors

(defn- kick-door [{:keys [player] :as game} level tile dir]
  (if (door-open? tile)
    [8 (with-reason "closing door to kick it"
         (if-not (monster-at level tile)
           (->Close dir)))]
    [(if (:leg-hurt player) 30 6) (kick game dir)]))

(defn can-unlock? [game]
  (has-hands? (:player game)))

(defn- enter-shop [game]
  ; TODO stash rather than drop pick if we have a bag
  (or (if-let [[slot _] (have game #{"pick-axe" "dwarvish mattock"}
                              #{:can-remove})]
        [2 (with-reason "dropping pick to enter shop" (->Drop slot))])
      (if-let [[slot _] (have game "ring of invisibility" #{:can-remove
                                                            :worn})]
        [2 (with-reason "removing invis to enter shop" (->Remove slot))])
      (if-let [[slot _] (have game "cloak of invisibility" #{:can-remove
                                                             :worn})]
        [2 (with-reason "taking off invis to enter shop" (->TakeOff slot))])))

(defn pass-monster [game level to-tile dir monster opts]
  (if (or (:peaceful monster)
          (and (:friendly monster) (diagonal dir) (door? to-tile)))
    (if-not (blocked? to-tile)
      [(if (at-planes? game) 4 50)
       (with-reason "peaceful blocker" monster
         (fidget game level to-tile))]) ; hopefully will move
    (if (:friendly monster)
      (if-not (:walking opts)
        [8 (with-reason "displace friendly" monster
              (->Move dir))])
      (if-not (:no-fight opts)
        [(if (at-planes? game) 2 12)
         (with-reason "pathing through hostiles" monster
           (->Move dir))]))))

(defn move
  "Returns [cost Action] for a move, if it is possible"
  ([game level from to]
   (move game level from to {}))
  ([game level from to opts]
   (let [to-tile (at level to)
         from-tile (at level from)
         dir (towards from to-tile)
         monster (monster-at level to)
         need-levi? (or (and (:levi opts) (#{:air :water} (:branch-id game))
                             (walkable? to-tile))
                        (needs-levi? to-tile))]
     (some-> (or (if (and (passable-walking? game level from-tile to-tile)
                          (or (not (:explored opts))
                              (:new-items to-tile) (:feature to-tile))
                          (not (and (kickable-door? level to-tile opts)
                                    (blocked-door level to-tile))))
                   (if monster
                     (pass-monster game level to-tile dir monster opts)
                     (or (and (shop? to-tile) (not (shop? from-tile))
                              (enter-shop game))
                         (if-not (or (and (:levi opts) need-levi?)
                                     (and (:castle (:tags level))
                                          (= (position 60 12)
                                             (position to-tile)))
                                     (and (polytrap? to-tile)
                                          (not (have-mr? game)))
                                     (and (= :sokoban (branch-key game))
                                          (hole? to-tile))
                                     (and (:no-traps opts) (trap? to-tile)))
                           [0 (->Move dir)])))) ; trapdoors/holes are escapable
                 (if (kickable-door? level from-tile opts)
                   (if-let [odir (blocked-door level from-tile)]
                     (if (monster-at level (in-direction from-tile odir))
                       [3 (with-reason
                            "waiting for monster to move to kick door at my pos"
                            (->Search))]
                       [1 (with-reason "moving to kick blocked door at my pos"
                            (->Move odir))])))
                 (if (and (edge-passable-walking? game level from-tile to-tile)
                          need-levi? (not (boulder? to-tile)))
                   (if-let [[slot item] (:levi opts)]
                     (if-let [[cost move] (if monster
                                            (pass-monster game level to-tile
                                                          dir monster opts)
                                            [1 (->Move dir)])]
                       (if (:worn item)
                         [cost (with-reason "assuming levitation" move)]
                         [(+ 2 cost) (with-reason "need levi for next move"
                                       (make-use game slot))]))))
                 (if (and (door? to-tile)
                          (not (:walking opts))
                          (not (:no-kick opts)))
                   (or (if monster
                         (pass-monster game level to-tile dir monster opts))
                       (if (door-secret? to-tile)
                         [10 (search 10)]) ; TODO stethoscope
                       (and (kickable-door? level to-tile opts)
                            (walkable? from-tile)
                            (blocked-door level to-tile)
                            (->> (partial with-reason
                                          "the door is blocked from one side")
                                 (update (kick-door game level to-tile dir) 1)))
                       (if (diagonal dir)
                         (if (and (kickable-door? level to-tile opts)
                                  (walkable? from-tile))
                           (kick-door game level to-tile dir))
                         (if (door-closed? to-tile)
                           [3 (->Open dir)]
                           (if-let [[slot i] (and (door-locked? to-tile)
                                                  (can-unlock? game)
                                                  (have-key game))]
                             (if (or (dare-destroy? level to-tile)
                                     (key? i)
                                     (not (:minetown (:tags level))))
                               [4 (->Unlock slot dir)])
                             (if (and (kickable-door? level to-tile opts)
                                      (walkable? from-tile))
                               (kick-door game level to-tile dir)))))))
                 (if (and (:pick opts) (diggable? to-tile)
                          (or (boulder? to-tile) (diggable-walls? game level))
                          (dare-destroy? level to-tile))
                   (if monster
                     (pass-monster game level to-tile dir monster opts)
                     (if (or (not (:thick (:player game)))
                             (not (narrow? game level from-tile to-tile)))
                       [8 (dig (:pick opts) dir)]
                       [16 (dig (:pick opts) dir)]))))
             (update 0 + (base-cost level dir to-tile opts))))))

(defrecord Path
  [step ; next Action to perform to move along path
   path ; vector of remaining positions
   target] ; position of target
  bothack.actions.Navigation$IPath
  (step [p] (:step p))
  (path [p] (:path p))
  (target [p] (:target p)))

(defn autonavigable? [game level opts [from to]]
  (and (not (shop? from))
       ((not-any-fn? shop? trap? unknown?) to)
       (edge-passable-walking? game level from to)
       (not (and (:castle (:tags level)) (door? from)))
       (or (safely-walkable? level to)
           (and ((some-fn pool? ice?) to)
                (some-> opts :levi val :worn)))))

(defn- autonav-target [game from level path opts]
  (if (and (not (:no-autonav opts))
           (not (shop? (at level from)))
           (not (pick? (wielded-item game)))
           (not= :autotravel (some-> game :last-action typekw)))
    (let [path-tiles (map (partial at level) path)
          steps (->> path-tiles
                     (interleave (conj path-tiles (at level from)))
                     (partition 2))
          autonavigable (->> steps (take-while
                                     (partial autonavigable? game level opts))
                             (mapv secondv))
          target (some-> (peek autonavigable) position)]
      (if (and (not (and (:autonav-stuck game) (= (:last-autonav game) target)))
               (more-than? 3 autonavigable)
               (not-any? (partial monster-at level) (neighbors from)))
        target))))

(defn- path-step [game level from move-fn path opts]
  (if-let [start (firstv path)]
    (some-> (or (if (:trapped (:player game))
                  (with-reason "untrapping self"
                    (untrap-move game level)))
                (if-let [target (and (not (:no-autonav opts))
                                     (not (weak? (:player game)))
                                     (autonav-target game from level
                                                     path opts))]
                  (->Autotravel target))
                (secondv (move-fn from start)))
            (assoc :path path))))

(defn- get-a*-path [game level from to move-fn opts max-steps]
  (if-let [path (a* from to move-fn max-steps)]
    (if (seq path)
      (if (:adjacent opts)
        (->Path (path-step game level from move-fn path opts) (pop path) to)
        (if-let [step (and (or (= 1 (count path))
                               (move-fn (-> path pop peek) to))
                           (path-step game level from move-fn path opts))]
          (->Path step path to)))
      (->Path nil [] to))))

(defn navopts
  ([s] (navopts s nil))
  ([s steps]
   (cond-> (zipmap (map #(.getKeyword %) s) (repeat true))
     steps (assoc :max-steps steps :max-delta steps))))

(defn navigate
  "Return shortest Path for given target position or predicate (a set of
  positions or any fn that takes a tile and returns boolean), will use A* or
  Dijkstra's algorithm as appropriate.

  Supported options (a map or a set if all vals of the map would be true):
    :walking - don't use actions except Move (no door opening etc.)
    :adjacent - path to closest adjacent tile instead of the target directly
    :explored - don't path through unknown tiles
    :max-steps <num> - don't navigate further than given number of steps
    :no-traps - more trap avoidance
    :no-dig - don't use the pickaxe or mattock
    :no-kick - don't kick down doors
    :no-levitation - when navigating deliberately into a hole/trapdoor
    :prefer-items - walk over unknown items preferably (useful for exploration but possibly dangerous when low on health - items could be corpses on a dangerous trap)
    :no-autonav - don't use _ autotravel (when fighting monsters)
    :no-fight - don't path through hostile monsters"
  ([game pos-or-goal-fn]
   (navigate game pos-or-goal-fn {}))
  ([{:keys [player] :as game} pos-or-goal-fn
    {:keys [max-steps walking adjacent no-dig no-levitation] :as opts}]
   {:pre [((some-fn ifn? position) pos-or-goal-fn)
          ((some-fn map? set? nil?) opts)]}
   (log/debug "navigating" pos-or-goal-fn opts)
   (let [level (curlvl game)
         branch (branch-key game level)
         levi (and (not no-levitation)
                   (not walking)
                   (not= :sokoban branch)
                   (or (have-levi-on game)
                       (have-levi game)))
         pick (and (not walking)
                   (not no-dig)
                   (not= :sokoban branch)
                   (have-pick game))
         opts (cond-> opts
                (set? opts) (zipmap (repeat true))
                levi (assoc :levi levi)
                pick (assoc :pick pick))
         move-fn #(move game level %1 %2 opts)]
     ; code below decides whether to run dijkstra (multiple goals) or A* (single goal) or nothing (no goal tile on the level)
     (if (or (not (ifn? pos-or-goal-fn)) (map? pos-or-goal-fn))
       (get-a*-path game level player pos-or-goal-fn move-fn opts max-steps)
       (let [goal-fn (if (set? pos-or-goal-fn)
                       (comp (set (map position pos-or-goal-fn)) position)
                       #(pos-or-goal-fn (at level %)))]
         (if adjacent
           (let [goal-set (if (set? pos-or-goal-fn)
                            (->> pos-or-goal-fn (mapcat neighbors) set)
                            (->> (tile-seq level) (filter goal-fn)
                                 (mapcat neighbors) set))]
             (case (count goal-set)
               0 nil
               1 (get-a*-path game level player (first goal-set)
                              move-fn opts max-steps)
               (if-let [path (dijkstra player goal-set move-fn max-steps)]
                 (->Path (path-step game level player move-fn path opts) path
                         (->> (or (peek path) player)
                              neighbors
                              (find-first goal-fn))))))
           ; not searching for adjacent
           (if-let [goal-seq (seq (if (set? pos-or-goal-fn)
                                    pos-or-goal-fn
                                    (filter goal-fn (tile-seq level))))]
             (if (more-than? 1 goal-seq)
               (if-let [path (dijkstra player goal-fn move-fn max-steps)]
                 (->Path (path-step game level player move-fn path opts) path
                         (or (peek path) (position player))))
               (get-a*-path game level player (first goal-seq) move-fn opts
                            max-steps)))))))))

(defn- isolated? [level tile]
  (every? (every-pred blank? unknown?) (neighbors level tile)))

(defn- probably-dug? [level tile]
  (or (dug? tile)
      (and (boulder? tile)
           (more-than? 2 (filter (some-fn boulder? corridor?)
                                 (straight-neighbors level tile))))))

(defn- explorable-tile? [level tile]
  (and (not (probably-dug? level tile))
       (or (and (unknown? tile) (not (blank? tile)))
           (:new-items tile)
           (and (not (:walked tile))
                ((some-fn grave? throne? sink? altar? fountain?) tile))
           (and (or (walkable? tile) (door? tile) (needs-levi? tile))
                (some (some-fn lava? pool? boulder? trap?
                               (partial safely-walkable? level))
                      (neighbors level tile))
                (some (not-any-fn? :seen boulder? blocked?)
                      (neighbors level tile))))
       (not (isolated? level tile))))

(defn dead-end? [level tile]
  (and (likely-walkable? level tile)
       (not (trap? tile))
       (not (:dug tile))
       ; isolated diagonal corridors - probably dug:
       (not (some #(and (or (and (= \* (:glyph %)) (nil? (:color %)))
                            (and (seq (:items %))
                                 (every? rocks? (:items %)))
                            (corridor? %) (boulder? %))
                        (not-any? (partial likely-walkable? level)
                                  (straight-neighbors level %)))
                  (diagonal-neighbors level tile)))
       (not (in-maze-corridor? level tile))
       (let [snbr (straight-neighbors level tile)]
         (and (or (some walkable? snbr)
                  (not-any? walkable? (neighbors level tile)))
              (> 2 (count (remove (some-fn rock? wall?) snbr)))))))

(defn- has-dead-ends? [game level]
  (and (not-any? #{:bigroom :juiblex :sanctum} (:tags level))
       (not (in-gehennom? game))
       (or (not (subbranches (branch-key game level)))
           (:minetown (:tags level)))))

(defn- search-dead-end
  [game num-search]
  (let [level (curlvl game)
        tile (at level (:player game))]
    (when (and (has-dead-ends? game level)
               (< (:searched tile) num-search)
               (dead-end? level tile))
      (with-reason "searching dead end" (search 10)))))

(defn- pushable-through [game level from to]
  (and (or ((some-fn walkable? pool? lava?) to)
           (and (not (boulder? to))
                (unknown? to))) ; try to push via unexplored tiles
       (or (straight (towards from to))
           (and (not= :sokoban (branch-key game))
                (not (door-open? from))
                (not (door-open? to))))))

(defn- pushable-from [game level pos]
  (if-let [tile (and (not= :earth (branch-key game))
                     (at level pos))]
    (if (or (not (needs-levi? tile)) (ice? tile))
      (seq (filter #(if (boulder? %)
                      (let [dir (towards pos %)
                            dest (in-direction level % dir)]
                        (and dest
                             (or (straight dir)
                                 (not= :sokoban (branch-key game level)))
                             (not (monster-at level dest))
                             (edge-passable-walking? game level tile %)
                             (pushable-through game level % dest))))
                   (neighbors level pos))))))

(defn- blocking-boulder? [level tile]
  (and (boulder? tile)
       (or (explorable-tile? level tile)
           ((not-any-fn? corridor? floor? ice?) tile)
           (not (every? (partial likely-walkable? level)
                        (neighbors level tile))))))

(defn- unblocked-boulder? [game level tile]
  (some #(and (safely-walkable? level (in-direction level tile
                                                    (towards % tile)))
              (not (monster-at level %))
              (pushable-through game level tile %))
        (neighbors level tile)))

(defn- push-boulders [{:keys [player] :as game} level]
  (if (some #(and (blocking-boulder? level %)
                  (unblocked-boulder? game level %)) (tile-seq level))
    (if-let [path (navigate game #(pushable-from game level %))]
      (with-reason "going to push a boulder"
        (or (:step path)
            (->> (pushable-from game level player) first
                 (towards player) ->Move (without-levitation game)))))
    (log/debug "no boulders to push")))

(defn- recheck-dead-ends [{:keys [player] :as game} level howmuch]
  (if (has-dead-ends? game level)
    (if-let [p (navigate game #(and (< (searched level %) howmuch)
                                    (dead-end? level %)))]
      (with-reason "re-checking dead ends" (or (:step p) (search 10))))))

(defn- searchable-position? [pos]
  (and (< 2 (:y pos) 20)
       (< 1 (:x pos) 78)))

(defn- wall-end? [level tile]
  (and (wall? tile)
       (< 0 (:x tile) 80)
       (< 0 (:y tile) 22)
       (not
         (or (and (wall? (at level (dec (:x tile)) (:y tile)))
                  (wall? (at level (inc (:x tile)) (:y tile))))
             (and (wall? (at level (:x tile) (dec (:y tile))))
                  (wall? (at level (:x tile) (inc (:y tile)))))))))

(defn- searchable-wall? [level howmuch tile]
  (and (searchable-position? tile)
       (wall? tile)
       (< (:searched tile) howmuch)
       (cond (= :wiztower (:branch-id level)) (not (wiztower-inner-boundary
                                                     (position tile)))
             (:sanctum (:tags level)) (->> (straight-neighbors level tile)
                                           (filter firetrap?) count (= 1))
             :else (not (shop? tile)))
       (not (wall-end? level tile))
       (or (<= 45 howmuch)
           (->> (straight-neighbors level tile) (remove :seen) seq))))

(defn- search-walls [game level howmuch]
  (let [searchable? (partial searchable-wall? level howmuch)]
    (if-let [p (navigate game searchable? #{:adjacent})]
      (with-reason "searching walls"
        (or (:step p) (search 10))))))

(defn- search-corridors [game level howmuch]
  (if-let [p (navigate game (fn searchable? [tile]
                              (and ((some-fn corridor? door?) tile)
                                   (searchable-position? tile)
                                   (< (searched level tile) howmuch))))]
    (with-reason "searching corridors"
      (or (:step p) (search 10)))))

(defn- searchable-extremity [level y xs howmuch]
  (if-let [tile (->> xs (map #(at level % y))
                     (find-first (partial likely-walkable? level)))]
    (if (and (floor? tile)
             (< (:searched tile) howmuch)
             (not= \- (:glyph (at level (update tile :x dec))))
             (not= \- (:glyph (at level (update tile :x inc))))
             (not (shop? tile)))
      tile)))

(defn unexplored-columns [game level]
  (if (and (#{:mines :main} (branch-key game level))
           (not (in-gehennom? game))
           (not (:castle (:tags level))))
    (remove (fn column-explored? [x]
              (some :feature (for [y (range 2 19)]
                               (at level x y))))
            [17 20 40 60 63])))

(defn unexplored-column
  "Look for a column of unexplored tiles on segments of the screen."
  [game level]
  (first (unexplored-columns game level)))

(defn- corridor-extremities [level init-cols howmuch]
  (loop [cols init-cols]
    (if-let [x (first cols)]
      (if (not-any? (not-any-fn? unknown? corridor? rock?) (column level x))
        (if-let [corridors (seq (filter corridor? (column level x)))]
          (filter #(< (:searched %) howmuch) corridors)
          (recur (rest cols)))))))

(defn- unsearched-extremities
  "Returns a set of tiles that are facing a large blank vertical space on the
  map – good candidates for searching."
  [game level howmuch]
  (if-let [col (unexplored-column game level)]
    (-> #{}
      (into (for [y (range 1 21)]
                  (searchable-extremity level y (range col 80) howmuch)))
      (into (for [y (range 1 21)]
                  (searchable-extremity level y (range col -1 -1) howmuch)))
      (into (corridor-extremities level (range col -1 -1) howmuch))
      (into (corridor-extremities level (range col 80) howmuch))
      (disj nil))))

(defn at-level? [game level]
  (and (= (:dlvl game) (:dlvl level))
       (= (branch-key game) (branch-key game level))))

(declare explore explore-step search-level)

(defn- curlvl-exploration [game]
  (or (explore-step game)
      (->> (tile-seq (curlvl game)) (map :searched) (reduce + 1))))

(defn exploration-index
  "Measures how much the level was explored/searched.  Zero means obviously not
  fully explored, larger number means more searching was done."
  ([game] (or (let [n (some-> game :explore-cache deref)]
                (if (number? n) n))
              0))
  ([game branch tag-or-dlvl]
   (if-let [level (get-level game branch tag-or-dlvl)]
     (if (at-level? game level)
       (exploration-index game)
       (get level :explored 0))
     0)))

(defn reset-exploration
  "Performance optimization - to know if the level is explored or not we need
  to run expensive navigation, might as well cache the result"
  [bh]
  (let [loc (atom nil)
        save (atom false)]
    (reify
      DlvlChangeHandler
      (dlvl-changed [_ _ _]
        (reset! save true))
      ActionChosenHandler
      (action-chosen [_ action]
        (if-not (#{:call :name :discoveries :inventory :look :farlook}
                         (typekw action))
          (when-let [f (:explore-cache @(:game bh))]
            (if @save
              (swap! (:game bh)
                     #(assoc-in % [:dungeon :levels (branch-key % (@loc 0))
                                   (@loc 1) :explored] (exploration-index %))))
            (swap! (:game bh) assoc :explore-cache nil)
            (future-cancel f))))
      AboutToChooseActionHandler
      (about-to-choose [_ game]
        (reset! loc [(:branch-id game) (:dlvl game)])
        (swap! (:game bh) assoc :explore-cache
               (future (curlvl-exploration game)))))))

(defn- search-extremities
  "Search near where there are large blank spaces on the map"
  [game level howmuch]
  (if (has-dead-ends? game level)
    (if-let [goals (unsearched-extremities game level howmuch)]
      (if-let [p (navigate game goals)]
        (with-reason "searching extremity" (or (:target p) "here")
          (or (:step p)
              (search 10)))))))

(defn go-down
  "Go through a hole or trapdoor or dig down if possible"
  [game level]
  (if-let [{:keys [step]} (navigate game (some-fn trapdoor? hole?)
                                    {:max-steps 15 :walking true})]
    (with-reason "going to a trapdoor/hole"
      (or step (descend game)))
    (if-let [pick (and (diggable-floor? level)
                       (have-pick game))]
      (if-let [{:keys [step]}
               (or (navigate game #(and (#{:pit :floor} (:feature %))
                                        (not-any? pool? (neighbors level %))
                                        (some wall?
                                              (diagonal-neighbors level %))
                                        (->> (straight-neighbors level %)
                                             (filter wall?)
                                             (more-than? 1))))
                   (navigate game #(and (#{:pit :floor :corridor} (:feature %))
                                        (not-any? pool? (neighbors level %))
                                        (->> (straight-neighbors level %)
                                             (filter
                                               (partial safely-walkable? level))
                                             (more-than? 2)))))]
        (or (with-reason "finding somewhere to dig down" step)
            (with-reason "digging down"
              (without-levitation game
                (dig pick :>)))))
      (if-let [[slot item] (and (diggable-floor? (curlvl game))
                                (#{:floor :corridor}
                                          (:feature (at-player game)))
                                (have game "wand of digging" #{:bagged}))]
        (or (unbag game slot item)
            (->ZapWandAt slot :>))))))

(defn seek-portal [game]
  (let [level (curlvl game)]
    (with-reason "seeking portal"
      (or (if-let [{:keys [step]} (navigate game portal?)]
            (or step (with-reason "sitting on a portal"
                       (without-levitation game (->Sit)))))
          (if (= :air (branch-key game))
            (with-reason "seeking :air portal"
              (:step (navigate game #(and ((not-any-fn? :walked cloud?) %)
                                          (< 44 (:x %)))))))
          (if (= :water (branch-key game))
            (with-reason "seeking :water portal"
              (letfn [(portal-spot? [tile]
                        (and (< 250 (- (:turn game)
                                       (or (:walked tile) 0)))
                             (< 3 (:y tile) 17)
                             (if (< 49 (mod (:turn game) 100))
                               (< 40 (:x tile) 74)
                               (< 5 (:x tile) 40))))]
                (or (:step (navigate game #(and (not= \} (:glyph %))
                                                (portal-spot? %))))
                    (:step (navigate game portal-spot?))))))
          (if (> (dlvl game) 35)
            (if-not (:walked (at level fake-wiztower-portal))
              (with-reason "seeking fake wiztower portal"
                (:step (navigate game fake-wiztower-portal))))
            (with-reason "stepping everywhere to find portal"
              (:step (navigate game #(and (likely-walkable? level %)
                                          (not (isolated? level %))
                                          (not (cloud? %))
                                          (not (corridor? %))
                                          (not (:walked %))
                                          (not (door? %)))))))
          (explore game)
          (search-level game)))))

(defn unstuck [game]
  (with-reason "unstuck"
    (or (if-let [[slot item] (have game "scroll of teleportation" #{:bagged})]
          (or (unbag game slot item)
              (->Read slot)))
        (if-let [[slot item] (have game "wand of teleportation" #{:bagged})]
          (or (unbag game slot item)
              (->ZapWandAt slot :.)))
        (go-down game (curlvl game)))))

(defn search-level
  ([game] (or (search-level game 3)
              (unstuck game)
              (search-level game 6)
              (throw (IllegalStateException. "stuck :-("))))
  ([game max-iter]
   (with-reason "searching - max-iter =" max-iter
     (let [level (curlvl game)]
       (loop [mul 1]
         (or (log/debug "search iteration" mul)
             (if (and (= 1 mul) (or (not= :sokoban (branch-key game))
                                    (not-any? (some-fn hole? pit?)
                                              (tile-seq (curlvl game)))))
               (push-boulders game level))
             (if (= :water (branch-key game)) (seek-portal game))
             (if (and (< 43 (dlvl game)) (not (:sanctum (curlvl-tags game))))
               (with-reason "no stairs, possibly :main :end"
                 (:step (navigate game #(and (< 5 (:x %) 75) (< 6 (:y %) 17)
                                             (not (:walked %))
                                             (not (wall? %)))))))
             (recheck-dead-ends game level (min (* mul 30) 50))
             (search-extremities game level (* mul 20))
             ; TODO dig towards unexplored-column
             (if (> mul 1) (search-corridors game level (* mul 5)))
             (search-walls game level (* mul 15))
             (if-not (> mul (dec max-iter))
               (recur (inc mul)))))))))

(defn seek
  "Like explore but also searches and always tries to return an action until
  the target is found

   options: same as navigate and the following:
     :no-explore - directly skips to searching"
  ([game smth]
   (seek game smth {}))
  ([game smth opts]
   (if-let [{:keys [step]} (navigate game smth opts)]
     (with-reason "seek going directly" step)
     (with-reason "seeking"
       (or (and (not (:no-explore opts)) (explore game))
           (and (:go-down opts) (with-reason "can't find downstairs"
                                  (go-down game (curlvl game))))
           (search-level game))))))

(defn- switch-dlvl [game new-dlvl]
  (with-reason "switching within branch to" new-dlvl
    (if-not (= (:dlvl game) new-dlvl)
      (or (if (= "Home 1" (:dlvl game)) ; may need to chat with quest leader first
            (let [level (curlvl game)
                  leader (-> level :blueprint :leader)]
              (if (and leader (not-any? :walked
                                        (straight-neighbors level leader)))
                (with-reason
                  "trying to seek out quest leader at" leader "before descending"
                  (seek game (set (straight-neighbors level leader))
                        {:explored true})))))
          (let [branch (branch-key game)
                level (curlvl game)
                [stairs action] (if (pos? (dlvl-compare (:dlvl game) new-dlvl))
                                  [:stairs-up (->Ascend)]
                                  [(if (:castle (:tags level))
                                     :trapdoor
                                     :stairs-down) (descend game)])
                step (or (if (and (= :stairs-down stairs)
                                  (or (:medusa (:tags level))
                                      (below-medusa? game))
                                  (not (below-castle? game)))
                           (with-reason "dive" (go-down game level)))
                         (with-reason "looking for the" stairs
                           (seek game #(and (has-feature? % stairs)
                                            (if-let [b (branch-key game %)]
                                              (or (= b branch)
                                                  (not (branches b)))
                                              true))
                                 (if (= :stairs-down stairs)
                                   {:go-down true}))))]
            (or step action))))))

(defn- escape-branch [game]
  (let [levels (get-branch game)
        branch (branch-key game)
        dlvl (:dlvl game)
        [stairs stair-action] (if (upwards? branch)
                                [:stairs-down (descend game)]
                                [:stairs-up (->Ascend)])]
    (with-reason "escaping subbranch" branch
      (or (if (or (and (= dlvl (first (keys levels)))
                       (portal-branches branch))
                  (planes branch))
            (seek-portal game))
          (with-reason "seeking stairs"
            (seek game #(has-feature? % stairs)))
          (with-reason "using stairs"
            stair-action)))))

(defn- least-explored [game branch dlvls]
  {:pre [(seq dlvls) (#{:main :mines} branch)]}
  (let [curdlvl (if (= branch (branch-key game))
                  (:dlvl (curlvl game)))]
    (first-min-by #(let [res (exploration-index game branch %)]
                     (if (and (= % curdlvl) (pos? res))
                       (max 1 (- res 1000)) ; add threshold not to switch levels too often
                       res))
                  dlvls)))

(defn- possibly-oracle? [game dlvl]
  (if-let [level (get-level game :main dlvl)]
    (not-any? corridor?
              (for [y [7 8 14 15]
                    x (range 34 45)]
                (at level x y)))
    true))

(defn- possibly-wiztower? [game dlvl]
  (if-let [level (get-level game :main dlvl)]
    (if (not-any? #{:wiztower-level :orcus :asmodeus :juiblex :baalzebub}
                  (:tags level))
      (->> fake-wiztower-water (map (partial at level))
           (remove (some-fn unknown? pool?)) (less-than? 5)))
    true))

(defn visited?
  ([game branch]
   (get-branch game branch))
  ([game branch dlvl-or-tag]
   (get-level game branch dlvl-or-tag)))

(defn- first-unvisited
  "First unvisited dlvl from given range in :main"
  [game dlvls]
  (->> dlvls (remove (partial visited? game :main)) first))

(defn- vlad-range [game]
  (if-let [vlad (dlvl-from-tag game :main :votd 9)]
    (dlvl-range :main vlad 5)
    (dlvl-range :main "Dlvl:34" 9)))

(defn- double-stairs? [game stairs? dlvl]
  (->> (get-level game :main dlvl) tile-seq (filter stairs?) (more-than? 1)))

(defn- dlvl-candidate
  "Return good Dlvl to search for given branch/tagged level"
  ([game branch]
   (or (branch-entry game branch) ; known
       (case branch ; likely dlvl choices
         :wiztower (->> (get-branch game :main) vals
                        (filter (fn unexplored-tower? [level]
                                  (and (:fake-wiztower (:tags level))
                                       ((complement :walked)
                                        (at level fake-wiztower-portal)))))
                        (map :dlvl) first)
         :vlad (find-first (partial double-stairs? game stairs-up?)
                           (vlad-range game))
         :sokoban (or (find-first (partial double-stairs? game stairs-up?)
                                  (dlvl-range :main "Dlvl:6" 5))
                      (if-let [oracle (get-dlvl game :main :oracle)]
                        (next-dlvl :main oracle))
                      (dlvl-candidate game :main :oracle))
         :quest (first-unvisited game (dlvl-range :main "Dlvl:11" 8))
         :mines (find-first (partial double-stairs? game stairs-down?)
                            (dlvl-range :main "Dlvl:2" 3))
         nil)
       (->> (case branch ; fallback - explore entire range
              :wiztower (filter (partial possibly-wiztower? game)
                                (or (if-let [end (get-dlvl game :main :end)]
                                      (as-> end res
                                        (change-dlvl #(- % 4) res)
                                        (dlvl-range :main res 4)))
                                    (if-let [gh (get-dlvl game :main :gehennom)]
                                      (as-> gh res
                                        (change-dlvl (partial + 15) res)
                                        (dlvl-range :main res 8)))
                                    (dlvl-range :main "Dlvl:40" 12)))
              :vlad (vlad-range game)
              :mines (dlvl-range :main "Dlvl:2" 3)
              (dlvl-range :main))
            (least-explored game :main))))
  ([game branch tag]
   (or (if (subbranches tag)
         (dlvl-candidate game tag))
       (get-dlvl game branch tag)
       (if-not (get-branch game branch)
         (dlvl-candidate game branch))
       (if (#{:end :votd :gehennom :castle} tag)
         (->> (get-branch game branch) keys last (next-dlvl branch)))
       (case tag
         :rogue (->> (dlvl-range :main "Dlvl:15" 4) (first-unvisited game))
         :medusa (->> (dlvl-range :main "Dlvl:21" 8)
                      (take-while
                        (partial not= (get-dlvl game :main :castle)))
                      (#(or (first-unvisited game %)
                            (least-explored game :main %))))
         nil)
       (->> (case tag
              :oracle (filter (partial possibly-oracle? game)
                              (dlvl-range :main "Dlvl:5" 5))
              :minetown (dlvl-range :mines
                                    (dlvl-from-entrance game :mines 3) 2)
              (dlvl-range branch))
            (least-explored game branch)))))

(defn- enter-branch [game branch]
  ;(log/debug "entering branch" branch)
  (if (not= :main (branch-key game))
    (or (explore game) (search-level game)) ; unknown subbranch
    (let [branch (branch-key game branch)
          [stairs stair-action] (if (or (upwards? branch) (planes branch))
                                  [:stairs-up (->Ascend)]
                                  [:stairs-down (descend game)])
          new-dlvl (dlvl-candidate game branch)]
      (with-reason "trying to enter" branch "from" new-dlvl
        (or (switch-dlvl game new-dlvl)
            (if (portal-branches branch)
              (seek-portal game))
            (seek game #(and (has-feature? % stairs)
                             (not= :main (branch-key game %))))
            stair-action)))))

(defn seek-branch
  [game new-branch-id]
  (let [new-branch (branch-key game new-branch-id)
        level (curlvl game)
        branch (branch-key game level)
        dlvl (:dlvl level)]
    (with-reason "seeking branch" new-branch-id "(" new-branch ")"
      (if (not= branch new-branch)
        (if (subbranches branch)
          (escape-branch game)
          (enter-branch game new-branch))))))

(defn seek-level
  "Navigate to a branch or dlvl/tagged level within one, neither needs to be
  found previously."
  [game new-branch-id tag-or-dlvl]
  (with-reason "seeking level" new-branch-id tag-or-dlvl
    (let [level (curlvl game)
          branch (branch-key game level)
          new-branch (branch-key game new-branch-id)
          new-level (get-level game new-branch tag-or-dlvl)
          dlvl (:dlvl level)]
      (if (= branch new-branch)
        (if new-level
          (switch-dlvl game (:dlvl new-level))
          (if (keyword? tag-or-dlvl)
            (or (switch-dlvl game (dlvl-candidate game new-branch tag-or-dlvl))
                (if (subbranches tag-or-dlvl)
                  (seek-branch game tag-or-dlvl))
                (with-reason "exploring dlvl candidate"
                  (explore game))
                (with-reason "searching dlvl candidate"
                  (search-level game)))
            (switch-dlvl game tag-or-dlvl)))
        (seek-branch game new-branch)))))

(defn explored?
  ([game]
   (pos? (exploration-index game)))
  ([game branch]
   (explored? game branch :end))
  ([game branch tag-or-dlvl]
   (pos? (exploration-index game branch tag-or-dlvl))))

(defn- shallower-unexplored
  ([game branch]
   (shallower-unexplored game :main (or (if (= :main (branch-key game branch))
                                          :end)
                                        (some->> (get-dlvl game :main branch)
                                                 (next-dlvl :main))
                                        branch)))
  ([game branch tag-or-dlvl]
   (let [branch (branch-key game branch)
         start (or (if (and (= :main branch) (not (have-levi game)))
                     (cond (below-castle? game) (get-dlvl game :main :castle)
                           (below-medusa? game) (get-dlvl game :main :medusa)))
                   (some->> (get-branch game branch) first key))
         dlvl (or (get-dlvl game branch tag-or-dlvl)
                  (if (= branch (branch-key game))
                    (next-dlvl branch (:dlvl game))
                    (some->> (get-branch game branch) last key next-dlvl)))]
     (if (and start dlvl (neg? (dlvl-compare branch start dlvl)))
       (->> start
            (iterate (partial next-dlvl branch))
            (take-while (partial not= dlvl))
            (remove (partial explored? game branch))
            first)))))

(defn explore-level [game branch tag-or-dlvl]
  (if-not (explored? game branch tag-or-dlvl)
    (or (seek-level game branch tag-or-dlvl)
        (explore game))))

(defn- center-explored? [level]
  (some :feature (for [y (range 5 15)]
                   (at level 40 y))))

(defn- search-limit [game level]
  (cond (and (:minetown (:tags level)) (center-explored? level)) 1
        (= :mines (branch-key game)) 10
        (more-than? 1 (unexplored-columns game level)) 2
        :else 1))

(defn- explore-step [{:keys [player] :as game}]
  (let [level (curlvl game)
        branch (branch-key game level)
        player-tile (at-player game)]
    (or (search-dead-end game 20)
        (if (and (:sanctum (:tags level))
                 (not (:walked (at-curlvl game 20 10))))
          (with-reason "searching sanctum"
            (seek game {:x 20 :y 10} {:no-explore true})))
        (if-let [path (navigate game (partial explorable-tile? level)
                                #{:prefer-items})]
          (with-reason "exploring" (at level (:target path))
            (:step path)))
        ; TODO search for shops if heard but not found
        (if (and (unexplored-column game level)
                 (not (and (:medusa (:tags level)) (not (have-levi game)))))
          (with-reason "level not explored enough, searching"
            (search-level game (search-limit game level))))
        (if-let [bldrs (and (not= :sokoban branch)
                            (filter #(and (boulder? %)
                                          (explorable-tile? level %))
                               (tile-seq level)))]
          (if-let [path (navigate game #(and (some (partial adjacent? %) bldrs)
                                             (pushable-from game level %)))]
            (with-reason "going to push an explorable boulder"
              (or (:step path)
                  (->> (pushable-from game level player) first
                       (towards player) ->Move (without-levitation game))))))
        (if (and (= :wiztower branch)
                 (:end (curlvl-tags game))
                 (unknown? (at-curlvl game 40 11)))
          (with-reason "searching wiztower top"
            (seek game {:x 40 :y 11} {:no-explore true})))
        (log/debug "nothing to explore"))))

(defn explore
  ([game]
   (if-not (pos? (exploration-index game))
     (with-reason "using cached exploration step"
       (if (:explore-cache game)
         @(:explore-cache game)
         (explore-step game)))))
  ([game branch]
   (explore game branch :end false))
  ([game branch tag-or-dlvl]
   (explore game branch tag-or-dlvl false))
  ([game branch tag-or-dlvl exclusive?]
   (with-reason "exploring" branch "until" tag-or-dlvl
     (or (if-let [l (and (not= :main (branch-key game branch))
                         (shallower-unexplored game branch))]
           (with-reason "first exploring main until branch entrance"
             (explore-level game :main l)))
         (if-let [l (shallower-unexplored game branch tag-or-dlvl)]
           (with-reason "first exploring previous levels of branch"
             (explore-level game branch l)))
         (if-not (or exclusive? (explored? game branch tag-or-dlvl))
           (with-reason "reaching exploration target"
             (explore-level game branch tag-or-dlvl)))
         (log/debug "all explored")))))

(defn visit
  ([game branch]
   (if-not (visited? game branch)
     (seek-branch game branch)))
  ([game branch tag-or-level]
   (if-not (visited? game branch tag-or-level)
     (seek-level game branch tag-or-level))))

(defn nav-targets [coll]
  (set (map position coll)))

(defn branch-map
  "{ dlvl of entrance in :main => :branch } for 'standard' branches"
  [game]
  (->> (for [branch [:mines :sokoban :quest :wiztower :vlad]
             :let [entry (branch-entry game branch)]
             :when entry]
         [entry branch])
       (into {})))

(defn- neighbor-levels [game level bmap opts]
  (let [branch (branch-key game level)
        dlvl (:dlvl level)
        nbr-branch (or (bmap dlvl) :main)]
      (for [nbr [(if-not (and (not (have-levi game))
                              (or (and (:medusa (:tags level))
                                       (below-medusa? game))
                                  (and (:castle (:tags level))
                                       (below-castle? game))))
                   (get-level game branch (prev-dlvl branch dlvl))) ; upstairs
                 (if (and (not= :main branch) ; escape branch
                          (= dlvl (ffirst (get-branch game branch))))
                   (get-level game :main (branch-entry game branch)))
                 (if-let [b (and (= :main branch) (not (:up opts))
                                 (bmap dlvl))] ; enter branch
                   (some->> b (get-branch game) first val))
                 (if-not (:up opts) ; downstairs
                   (get-level game branch (next-dlvl branch dlvl)))]
            :when nbr]
        nbr)))

(defn level-seq
  "Lazy seq of levels by unit distance from current (doesn't consider level layouts)"
  ([game] (level-seq game {}))
  ([game opts]
   (let [bmap (branch-map game)
         levid (juxt :dlvl :branch-id)]
     ((fn explore [open closed]
        (lazy-seq
          (if-let [nbr (peek open)]
            (let [nbrs (neighbor-levels game nbr bmap opts)]
              (cons nbr
                    (explore (into (pop open) (remove (comp closed levid) nbrs))
                             (into closed (map levid nbrs))))))))
      (into (clojure.lang.PersistentQueue/EMPTY)
            (neighbor-levels game (curlvl game) bmap opts))
      (->> (neighbor-levels game (curlvl game) bmap opts)
           (map levid) (into #{(levid game)}))))))

(defn seek-tile
  "Options:
  :up - only go up, never to subbranches
  :max-delta - limit search depth"
  ([game goal?]
   (seek-tile game goal? {}))
  ([game goal? opts]
   (log/debug "seek tile" goal?)
   (with-reason "seeking tile" goal?
     (or (:step (navigate game goal? opts))
         (as-> (level-seq game opts) res
           (if (:max-delta opts)
             (take (:max-delta opts) res)
             res)
           (find-first (comp (partial some goal?) tile-seq) res)
           (if res (seek-level game (:branch-id res) (:dlvl res))))))))

(defn seek-feature [game feature]
  (with-reason "seeking feature" feature
    (seek-tile game #(has-feature? % feature))))

(defn entering-shop? [game]
  (some->> (:last-path game) firstv (at-curlvl game) shop?))

