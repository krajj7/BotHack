; a dumb bot for the samurai class

(ns anbf.bots.sambot
  (:require [clojure.tools.logging :as log]
            [anbf.delegator :refer :all]
            [anbf.action :refer :all])
  (:import [anbf.delegator IActionHandler IChooseCharacterHandler]))

(def ^:private circle-large (cycle [1 4 7 8 9 6 3 2]))

(def ^:private circle-small (cycle [6 2 4 8]))

(defn- circle-mover []
  (let [circle (atom circle-small)]
    (reify IActionHandler ; use java interface
      (chooseAction [_ _]
        (->Move (first (swap! circle next)))))))

(defn- pray-for-food []
  (reify ActionHandler ; use protocol directly to test both
    (chooseAction [_ game]
      (if (= :fainting (-> game :player :hunger))
        (->Pray)))))

(defn init [anbf]
  (-> anbf
      (.registerHandler (reify IChooseCharacterHandler
                          (chooseCharacter [this]
                            (.deregisterHandler anbf this)
                            "nsm")))
      (.registerHandler 0 (pray-for-food))
      (.registerHandler 1 (circle-mover))))
