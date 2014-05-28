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

(defn- enemy? [{:keys [monster] :as tile}]
  (and monster (not (:peaceful monster)) (not (:friendly monster))))

(defn- fight []
  (reify ActionHandler
    (choose-action [_ game]
      ; TODO can get stuck if immobile monster gets out of LOS momentarily while going to it, needs monster tracking...
      ; TODO nearest
      (when-let [enemy (first (filter #(and (in-fov? game %) (enemy? %))
                                      (->> game :dungeon curlvl :tiles
                                           (apply concat))))]
        (println "see enemy at" enemy)
        (Thread/sleep 200) ; XXX
        (if-let [n (first (path-walking game enemy))]
          (->Move (towards (-> game :player) n))
          (println "cannot path" enemy))))))

(defn- explorable-tile? [level tile]
  (and (or (walkable? tile) (door? tile)) ; XXX locked shops?
       (some #(-> (at level %) :seen not) (neighbors level tile))))

(defn- explore []
  (let [current (ref nil)]
    (reify ActionHandler
      (choose-action [_ game]
        (dosync
          (or (some-> @current (choose-action game))
              ; TODO if reached corridor dead-end (max 1 straight-adjacent walkable), search for a bit
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
