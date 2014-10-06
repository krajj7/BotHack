(ns anbf.bots.simplebot
  "A dumb example bot.  Equivalent to SimpleBot.java"
  (:require [clojure.tools.logging :as log]
            [anbf.anbf :refer :all]
            [anbf.delegator :refer :all]
            [anbf.actions :refer :all]))

(def ^:private circle-small (cycle [:N :E :S :W]))

(defn- circle-mover []
  (let [circle (atom circle-small)]
    (reify ActionHandler
      (choose-action [_ _]
        (->Move (first (swap! circle rest)))))))

(defn- pray-for-food []
  (reify ActionHandler
    (choose-action [_ game]
      (if (fainting? (:player game))
        (->Pray)))))

(defn init [anbf]
  (-> anbf
      (register-handler (reify ChooseCharacterHandler
                          (choose-character [this]
                            (deregister-handler anbf this)
                            "nsm"))) ; choose samurai
      (register-handler 0 (pray-for-food))
      (register-handler 1 (circle-mover))))
