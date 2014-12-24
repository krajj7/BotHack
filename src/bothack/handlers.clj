(ns bothack.handlers
  (:require [clojure.tools.logging :as log]
            [bothack.util :refer :all]
            [bothack.dungeon :refer :all]
            [bothack.delegator :refer :all]))

(defn register-handler
  [bh & args]
  (send (:delegator bh) #(apply register % args))
  bh)

(defn deregister-handler
  [bh handler]
  (send (:delegator bh) deregister handler)
  bh)

(defn replace-handler
  [bh handler-old handler-new]
  (send (:delegator bh) switch handler-old handler-new)
  bh)

(defn update-before-action
  "Before the next action is chosen call (apply swap! game f args).  This happens when game state is updated and player position is known."
  [bh f & args]
  {:pre [(:game bh)]}
  (register-handler bh priority-top
    (reify AboutToChooseActionHandler
      (about-to-choose [this _]
        (apply swap! (:game bh) f args)
        (deregister-handler bh this)))))

(defn update-on-known-position
  "When player position on map is known call (apply swap! game f args).  Game state may not be fully updated yet for the turn."
  [bh f & args]
  {:pre [(:game bh)]}
  (register-handler bh priority-top
    (reify
      AboutToChooseActionHandler ; handler might have been registered too late to receive know-position this turn
      (about-to-choose [this _]
        (apply swap! (:game bh) f args)
        (deregister-handler bh this))
      KnowPositionHandler
      (know-position [this _]
        (apply swap! (:game bh) f args)
        (deregister-handler bh this)))))

(defn update-at-player-when-known
  "Update the tile at player's next known position by applying update-fn to its current value and args"
  [bh update-fn & args]
  (update-on-known-position bh #(apply update-at-player % update-fn args)))
