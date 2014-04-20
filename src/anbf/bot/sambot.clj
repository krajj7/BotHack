; a dumb bot for the samurai class

(ns anbf.bot.sambot
  (:require [anbf.util :refer :all]
            [anbf.delegator :refer :all]
            [anbf.action :refer :all]
            [anbf.player :refer :all]
            [clojure.tools.logging :as log]))

(def ^:private circle-large (cycle [1 4 7 8 9 6 3 2]))

(def ^:private circle-small (cycle [6 2 4 8]))

(defn- circle-mover []
  (let [circle (atom circle-small)]
    (reify ActionHandler
      (choose-action [_ _]
        (->Move (first (swap! circle next)))))))

(defn- pray-if-weak []
  (reify ActionHandler
    (choose-action [_ game]
      (if (weak? (:player game))
        (->Pray)))))

(defn init [anbf]
  (-> anbf
      (register-handler (reify ChooseCharacterHandler
                          (choose-character [this]
                            (deregister-handler anbf this)
                            "nsm")))
      (register-handler (circle-mover))
      (register-handler (pray-if-weak))))
