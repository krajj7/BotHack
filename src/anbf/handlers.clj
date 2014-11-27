(ns anbf.handlers
  (:require [clojure.tools.logging :as log]
            [anbf.util :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.delegator :refer :all]))

(defn register-handler
  [anbf & args]
  (send (:delegator anbf) #(apply register % args))
  anbf)

(defn deregister-handler
  [anbf handler]
  (send (:delegator anbf) deregister handler)
  anbf)

(defn replace-handler
  [anbf handler-old handler-new]
  (send (:delegator anbf) switch handler-old handler-new)
  anbf)

(defn update-before-action
  "Before the next action is chosen call (apply swap! game f args).  This happens when game state is updated and player position is known."
  [anbf f & args]
  {:pre [(:game anbf)]}
  (register-handler anbf priority-top
    (reify AboutToChooseActionHandler
      (about-to-choose [this _]
        (apply swap! (:game anbf) f args)
        (deregister-handler anbf this)))))

(defn update-on-known-position
  "When player position on map is known call (apply swap! game f args).  Game state may not be fully updated yet for the turn."
  [anbf f & args]
  {:pre [(:game anbf)]}
  (register-handler anbf priority-top
    (reify
      AboutToChooseActionHandler ; handler might have been registered too late to receive know-position this turn
      (about-to-choose [this _]
        (apply swap! (:game anbf) f args)
        (deregister-handler anbf this))
      KnowPositionHandler
      (know-position [this _]
        (apply swap! (:game anbf) f args)
        (deregister-handler anbf this)))))

(defn update-at-player-when-known
  "Update the tile at player's next known position by applying update-fn to its current value and args"
  [anbf update-fn & args]
  (update-on-known-position anbf #(apply update-at-player % update-fn args)))
