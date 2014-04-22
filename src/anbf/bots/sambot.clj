; a dumb bot for the samurai class

(ns anbf.bots.sambot
  (:require [clojure.tools.logging :as log]
            [anbf.delegator :refer :all]
            [anbf.bot :refer :all]
            [anbf.action :refer :all]
            [anbf.player :refer :all]))

(def ^:private circle-large (cycle [1 4 7 8 9 6 3 2]))

(def ^:private circle-small (cycle [6 2 4 8]))

(defn- circle-mover []
  (let [circle (atom circle-small)]
    (reify ActionHandler
      (choose-action [_ _]
        (->Move (first (swap! circle next)))))))

(defn- pray-for-food []
  (reify ActionHandler
    (choose-action [_ game]
      (if (= :fainting (get-in game [:player :hunger]))
        (->Pray)))))

(defn init [anbf]
  (-> anbf
      (register-handler (reify ChooseCharacterHandler
                          (choose-character [this]
                            (deregister-handler anbf this)
                            "nsm")))
      (register-handler (circle-mover))
      (register-handler (pray-for-food))))
