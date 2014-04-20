; representation of the bot avatar

(ns anbf.player
  (:require [clojure.tools.logging :as log]))

(defrecord Player
  [nickname
   title
   hp
   maxhp
   pw
   maxpw
   ac
   xp
   xplvl
   hunger
   burden
   state ; stun/conf/hallu/blind/...
   stats
   alignment])
