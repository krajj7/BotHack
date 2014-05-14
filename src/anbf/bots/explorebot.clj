; a dumb level-exploring bot

(ns anbf.bots.explorebot
  (:require [clojure.tools.logging :as log]
            [anbf.anbf :refer :all]
            [anbf.player :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.game :refer :all]
            [anbf.delegator :refer :all]
            [anbf.action :refer :all]))

(defn- enemy? [{:keys [monster] :as tile}]
  (and monster (not (:peaceful monster)) (not (:friendly monster))))

(defn- fight []
  (reify ActionHandler
    (choose-action [_ game]
      ; if monster in fov
      (when-let [enemy (first (filter #(and (in-fov? game (:position %))
                                            (enemy? %))
                                      (->> game :dungeon curlvl :tiles
                                           (apply concat))))]
        (println "see enemy at" enemy)
        (Thread/sleep 1000)
        (->Move (towards (-> game :player :position) (:position enemy)))))))


(defn- pray-for-food []
  (reify ActionHandler
    (choose-action [_ game]
      (if (weak? (:player game))
        (->Pray)))))

(def ^:private circle-small (cycle [:N :E :S :W]))
(defn- circle-mover []
  (let [circle (atom circle-small)]
    (reify ActionHandler
      (choose-action [_ _]
        (->Move (first (swap! circle rest)))))))

(defn init [anbf]
  (-> anbf
      (register-handler (reify ChooseCharacterHandler
                          (choose-character [this]
                            (deregister-handler anbf this)
                            "nsm"))) ; choose samurai
      (register-handler 0 (pray-for-food))
      (register-handler 1 (fight))
      (register-handler 2 (circle-mover))))
