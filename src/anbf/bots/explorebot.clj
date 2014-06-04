; a dumb level-exploring bot

(ns anbf.bots.explorebot
  (:require [clojure.tools.logging :as log]
            [anbf.anbf :refer :all]
            [anbf.player :refer :all]
            [anbf.pathing :refer :all]
            [anbf.position :refer :all]
            [anbf.game :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.delegator :refer :all]
            [anbf.actions :refer :all]))

(defn- enemy? [monster]
  (and (not (:peaceful monster))
       (not (:friendly monster))))

(defn- enemies [level]
  (filter enemy? (-> level :monsters vals)))

(defn- fight []
  (reify ActionHandler
    (choose-action [_ game]
      (loop [enemies (sort-by #(distance (:player game) %)
                              (filter #(and (> 3 (- (:turn game) (:seen %)))
                                            (> 10 (distance (:player game) %)))
                                      (-> game :dungeon curlvl enemies)))]
        (when-let [enemy (first enemies)]
          (log/debug "targetting enemy" enemy)
          (Thread/sleep 100) ; XXX
          (if-let [n (first (path-walking game enemy))]
            (->Move (towards (-> game :player) n))
            (do (log/debug "cannot path" enemy)
                (recur (rest enemies)))))))))

(defn- explorable-tile? [level tile]
  (and (or (walkable? tile) (door? tile)) ; XXX locked shops?
       (some #(not (:seen %)) (neighbors level tile))))

(defn- dead-end? [level tile]
  (and (walkable? tile)
       (> 2 (count (remove #(or (door? %)
                                (#{:rock :wall} (:feature %))
                                (diagonal? tile %))
                           (neighbors level tile))))))

(defn- search-dead-end ; TODO not in the mines
  [{:keys [player dungeon] :as game} num-search]
  (let [level (curlvl dungeon)
        tile (at level player)]
    (when (and (dead-end? level tile)
               (< (:searched tile) num-search))
      (log/debug "searching dead end")
      (->Search))))

(defn- explore []
  (let [current (ref nil)]
    (reify ActionHandler
      (choose-action [_ game]
        (dosync
          (or (some-> @current (choose-action game))
              (search-dead-end game 15)
              (when-let [t (peek (nearest-travelling
                                   game
                                   (partial explorable-tile?
                                            (curlvl (:dungeon game)))))]
                (log/debug "chose exploration target " t)
                (Thread/sleep 100) ; XXX
                (some-> (ref-set current (travel t))
                        (choose-action game)))
              (ref-set current nil)
              (log/debug "nothing to explore")))))))

(defn- descend []
  (reify ActionHandler
    (choose-action [_ game]
      (if-let [path (nearest-travelling game #(= :stairs-down (:feature %)))]
        (if-let [t (peek path)]
          (choose-action (travel t) game)
          (->Descend))
        (log/debug "cannot find stairs")))))

(defn- random-travel []
  (let [current (ref nil)]
    (reify ActionHandler
      (choose-action [_ game]
        (dosync
          (or (some-> @current (choose-action game))
              (loop []
                (let [target (some->> game :dungeon curlvl :tiles (apply concat)
                                      (filterv walkable?) rand-nth)]
                  (log/debug "chose random tile " target)
                  (or (some-> (ref-set current (travel target))
                              (choose-action game))
                      (recur))))))))))

; TODO mozna by se dalo zobecnit na nejaky travel-along behavior, parametrizovany funkci na vyber tilu a akci/chovanim k provedeni na nem

(defn- search []
  (reify ActionHandler
    (choose-action [_ _]
      ; TODO look for dead ends, then walls
      ; TODO push boulders
      (->Search))))

(defn- pray-for-food []
  (reify ActionHandler
    (choose-action [_ game]
      (if (weak? (:player game))
        (->Pray)))))

(defn init [anbf]
  (-> anbf
      (register-handler (reify ChooseCharacterHandler
                          (choose-character [this]
                            (deregister-handler anbf this)
                            "nsm"))) ; choose samurai
      (register-handler -10 (pray-for-food))
      (register-handler -5 (fight))
      (register-handler 5 (explore))
      (register-handler 6 (descend))
      (register-handler 7 (random-travel))
      (register-handler 10 (search))))
