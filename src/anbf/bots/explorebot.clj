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
            [anbf.action :refer :all]))

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

(defn travel [game to]
  (reify ActionHandler
    (choose-action [this game]
      (if-let [p (path-walking game to)]
        (if-let [n (first p)]
          (if (walkable? (at (-> game :dungeon curlvl) n))
            (do (Thread/sleep 50) (->Move (towards (-> game :player) n))) ; XXX
            (log/debug "reached unwalkable target"))
          (log/debug "reached travel target"))
        (do (log/debug "target not reachable")
            (Thread/sleep 200)))))) ; XXX

(defn- explorable-tiles [level]
  (reduce into #{}
          (map #(if (not (:seen %))
                  (filter walkable? (neighbors level %)))
               (apply concat (:tiles level)))))

(defn- explore []
  (let [current (ref nil)]
    (reify ActionHandler
      (choose-action [_ game]
        (dosync
          (or (some-> @current (choose-action game))
              (loop [t (sort-by #(distance (-> game :player) %)
                                (explorable-tiles (curlvl (:dungeon game))))]
                (when-let [target (first t)]
                  (log/debug "chose exploration target " target)
                  (or (some-> (ref-set current (travel game target))
                              (choose-action game))
                      (recur (rest t)))))
              (ref-set current nil)
              (log/debug "nothing to explore")))))))

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
                  (or (some-> (ref-set current (travel game target))
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
      (register-handler 6 (random-travel))
      (register-handler 10 (search))))
