; a dumb bot for the samurai class

(ns anbf.bot.sambot
  (:require [anbf.util :refer :all]
            [anbf.delegator :refer :all]
            [anbf.action :refer :all]
            [clojure.tools.logging :as log]))

(defn- circle-mover []
  (let [circle (atom (cycle [1 4 7 8 9 6 3 2]))]
    (reify ActionHandler
      (choose-action [this anbf]
        (->Move (first (swap! circle next)))))))

(defn init [anbf]
  (register-handler anbf
                    (reify ChooseCharacterHandler
                      (choose-character [this]
                        (deregister-handler anbf this)   
                        "nsm")))
  (register-handler anbf (circle-mover)))
