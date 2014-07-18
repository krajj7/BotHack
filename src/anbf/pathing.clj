(ns anbf.pathing
  (:require [clojure.data.priority-map :refer [priority-map-keyfn]]
            [clojure.tools.logging :as log]
            [clojure.set :refer [intersection]]
            [anbf.position :refer :all]
            [anbf.monster :refer :all]
            [anbf.delegator :refer :all]
            [anbf.actions :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.player :refer :all]
            [anbf.util :refer :all]
            [anbf.tile :refer :all]))

(defn base-cost [level dir tile]
  {:pre [(and (some? level) (some? dir) (some? tile))]}
  (let [feature (:feature tile)]
    (cond-> 1
      (nil? feature) (+ 3)
      (= :trap feature) (+ 10) ; TODO trap types
      (diagonal dir) (+ 0.1)
      (not (#{:stairs-up :stairs-down} feature)) (+ 0.1)
      (not (or (:dug tile) (:walked tile))) (+ 0.2)
      (and (not (:walked tile)) (= :floor feature)) (+ 0.5))))

(defn diagonal-walkable? [game {:keys [feature] :as tile}]
  (not= :door-open feature))

(defn- a* [from to move-fn]
  "Extra-cost must always return non-negative values, target tile may not be passable, but will always be included in the path"
  (loop [closed {}
         open (priority-map-keyfn first (position from) [0 0])]
    (if (empty? open) nil
      (let [[node [total dist prev]] (peek open)
            path (conj (closed prev []) node)
            final-path (subvec path 1)
            delta (distance node to)]
        (cond
          (zero? delta) final-path
          (and (= 1 delta)
               (not-any? #(move-fn % to) (neighbors to))) (conj final-path to)
          :else (recur
                  (assoc closed node path)
                  (merge-with
                    (partial min-key first)
                    (pop open)
                    (into {}
                          (for [nbr (remove closed (neighbors node))
                                :let [[cost action] (move-fn node nbr)]
                                :when (some? action)]
                            (let [new-dist (int (+ dist cost))]
                              [nbr [(+ new-dist delta) new-dist node]]))))))))))

(defn- dijkstra [from goal? move-fn]
  (loop [closed {}
         open (priority-map-keyfn first (position from) [0])]
    (if-let [[node [dist prev]] (peek open)]
      (let [path (conj (closed prev []) node)]
        (if (goal? node)
          (subvec path 1)
          (recur (assoc closed node path)
                 (merge-with (partial min-key first)
                             (pop open)
                             (into {}
                                   (for [nbr (remove closed (neighbors node))
                                         :let [[cost action] (move-fn node nbr)]
                                         :when (some? action)]
                                     [nbr [(+ dist cost) node]])))))))))

(defn- unexplored? [tile]
  (and (not (boulder? tile))
       (nil? (:feature tile))))

(defn- narrow?
  "Only works for adjacent diagonals"
  [level from to]
  (not-any? #((complement #{:wall :rock}) (:feature %))
            (intersection (into #{} (straight-neighbors level from))
                          (into #{} (straight-neighbors level to)))))

(defn passable-walking?
  "Only needs Move action, no door opening etc., will path through monsters"
  [game level from to]
  (let [from-tile (at level from)
        to-tile (at level to)]
    (and (walkable? to-tile)
         (or (straight (towards from to))
             (and (diagonal-walkable? game from-tile)
                  (diagonal-walkable? game to-tile)
                  (or (not (narrow? level from-tile to-tile))
                      (not (:thick (:player game)))))))))

(defn- random-move [{:keys [player] :as game} level]
  (some->> (neighbors level player)
           (remove (partial monster-at level))
           (filterv #(or (passable-walking? game level player %)
                         (unexplored? %)))
           (#(if (seq %) % nil))
           rand-nth
           (towards player)
           ->Move))

(defn fidget
  "Move around randomly or wait to make a peaceful move out of the way"
  ([game] (fidget game (curlvl game)))
  ([game level] (fidget game level nil))
  ([{:keys [player] :as game} level target]
   (with-handler priority-top
     (fn [anbf]
       (log/debug "fidgeting")
       (if (some? target)
         (reify AboutToChooseActionHandler
           (about-to-choose [_ new-game]
             (when (:peaceful (curlvl-monster-at new-game target)) ; target did not move
               (log/debug "blocked by peaceful" target)
               (swap! (:game anbf) update-curlvl-at target
                      update-in [:blocked] (fnil inc 0)))))))
     (or (random-move game level)
         (->Search)))))

(defn- safe-from-guards
  "Much more pessimistic than this could be, but just enough to handle the most usual corner-case where the only door in the first room is locked."
  [level]
  (not-any? #(-> % :type :tags :guard) (vals (:monsters level))))

(defn dare-kick? [level tile]
  (and (not ((:tags level) :rogue))
       (or (not ((:tags level) :minetown))
           (safe-from-guards level))
       (not (shop? tile))))

(defn- blocked-door
  "If this is a kickable door blocked from one side, return direction from which to kick it"
  [level tile]
  (if-let [ws (seq (filter walkable? (straight-neighbors level tile)))]
    (let [w (first ws)
          dir (towards tile w)
          o (in-direction level tile (opposite dir))]
      (if (and (= 1 (count ws))
               (some walkable? (intersection
                                 (into #{} (diagonal-neighbors level tile))
                                 (into #{} (straight-neighbors level o)))))
        dir))))

(defn- kickable-door? [level tile opts]
  (and (door? tile)
       (not (:walking opts))
       (dare-kick? level tile)
       (not (item? (:glyph tile) (:color tile))))) ; don't try to break blocked doors

(defn- kick-door [level tile dir]
  (if (= :door-open (:feature tile))
    [8 (->Close dir)]
    [5 (->Kick dir)]))

(defn move
  "Returns [cost Action] for a move, if it is possible"
  ([game level from to]
   (move game from to #{}))
  ([game level from to opts]
   (let [to-tile (at level to)
         from-tile (at level from)
         dir (towards from to-tile)
         monster (monster-at level to)]
     (if-let [step ; TODO digging/levi
              (or (if (or (and (unexplored? to-tile) (not (:explored opts)))
                          (and (passable-walking? game level from to)
                               (not (and (kickable-door? level to-tile opts)
                                         (blocked-door level to-tile)))))
                    (if-not monster
                      [0 (->Move dir)]
                      (if-not (:peaceful monster)
                        [30 (->Move dir)]
                        (if ((fnil <= 0) (:blocked to-tile) 25)
                          [50 (fidget game level to-tile)])))) ; hopefully will move
                  (if (kickable-door? level from-tile opts)
                    (if-let [odir (blocked-door level from-tile)]
                      (if (monster-at level (in-direction from-tile odir))
                        [3 (->Search)]
                        [1 (->Move odir)])))
                  (if (:trapped (:player game))
                    (if-let [step (random-move game level)]
                      [0 step]))
                  (when (and (door? to-tile) (not monster))
                    (or (and (kickable-door? level to-tile opts)
                             (blocked-door level to-tile)
                             (kick-door level to-tile dir))
                        (if (diagonal dir)
                          (if (kickable-door? level to-tile opts)
                            (kick-door level to-tile dir))
                          (if (= :door-closed (:feature to-tile))
                            [3 (->Open dir)]
                            (if (= :door-locked (:feature to-tile))
                              ; TODO unlocking
                              (if (kickable-door? level to-tile opts)
                                (kick-door level to-tile dir))
                              [3 (->Open dir)]))))))]
       (update-in step [0] + (base-cost level dir to-tile))))))

(defrecord Path
  [step ; next Action to perform to move along path
   path ; vector of remaining positions
   target]) ; position of target

; TODO could use builtin pathfinding (_ command) if the path is all explored
(defn- path-step [from move-fn path]
  (if (seq path)
    (nth (move-fn from (nth path 0)) 1)))

(defn- get-a*-path [from to move-fn opts]
  (log/debug "a*")
  (if-let [path (a* from to move-fn)]
    (if (seq path)
      (if (:adjacent opts)
        (->Path (path-step from move-fn path) (pop path) to)
        (and (or (= 1 (count path)) (move-fn (-> path pop peek) to))
             (->Path (path-step from move-fn path) path to)))
      (->Path nil [] to))))

(defn navigate
  "Return shortest Path for given target position or predicate, will use A* or Dijkstra's algorithm as appropriate.
  Supported options:
    :walking - don't use actions except Move (no door opening etc.)
    :adjacent - path to closest adjacent tile instead of the target directly
    :explored - don't path through unknown tiles"
  [{:keys [player] :as game} pos-or-goal-fn & opts]
  [{:pre [(or (fn? pos-or-goal-fn) (set? pos-or-goal-fn) (position pos-or-goal-fn))]}]
  ;(log/debug "navigating" pos-or-goal-fn opts)
  (let [opts (into #{} opts)
        level (curlvl game)
        move-fn #(move game level %1 %2 opts)]
    (if-not (or (set? pos-or-goal-fn) (fn? pos-or-goal-fn))
      (get-a*-path player pos-or-goal-fn move-fn opts)
      (let [goal-fn (if (set? pos-or-goal-fn)
                      (comp (into #{} (map position pos-or-goal-fn))
                            position)
                      #(pos-or-goal-fn (at level %)))]
        (if (:adjacent opts)
          (let [goal-set (if (set? pos-or-goal-fn)
                           (->> pos-or-goal-fn (mapcat neighbors) (into #{}))
                           (->> level :tiles (apply concat) (filter goal-fn)
                                (mapcat neighbors) (into #{})))]
            (case (count goal-set)
              0 nil
              1 (get-a*-path player (first goal-set) move-fn opts)
              (if-let [path (dijkstra player goal-set move-fn)]
                (->Path (path-step player move-fn path) path
                        (->> (or (peek path) player) neighbors
                             (filter goal-fn) first)))))
          ; not searching for adjacent
          (if-let [goal-seq (if (set? pos-or-goal-fn)
                              (->> pos-or-goal-fn (take 2) seq)
                              (->> level :tiles (apply concat) (filter goal-fn)
                                   (take 2) seq))]
            (if (= 2 (count goal-seq))
              (if-let [path (dijkstra player goal-fn move-fn)]
                (->Path (path-step player move-fn path) path (peek path)))
              (get-a*-path player (first goal-seq) move-fn opts))))))))
