; representation of the bot avatar

(ns anbf.player
  (:require [clojure.tools.logging :as log]))

(defrecord Player
  [nickname
   hp
   maxhp
   pw
   maxpw
   ac
   xp
   xplvl
   hunger
   nutrition
   burden
   state ; stun/conf/hallu/blind/...
   stats
   alignment])

(defn new-player []
  (apply ->Player (repeat 14 nil)))

(defn weak?
  "Return true if hunger state is Weak or worse"
  [{:keys [hunger]}]
  (some #(= hunger %) [:weak :fainting]))

(defn update-player [player status delegator]
  ; TODO not just merge, emit events on changes, adjust nutrition by hunger...
  (merge player status))
