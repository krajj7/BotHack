; representation of the level map

(ns anbf.level
  (:require [clojure.tools.logging :as log]
            [anbf.frame :refer :all]
            [anbf.delegator :refer :all]))

(defrecord Level [player-x player-y])

(defn level-handler
  [game delegator]
  (reify
    MapHandler
    (map-drawn [_ frame]
      (log/debug "TODO parse map"))
    FullFrameHandler
    (full-frame [_ frame]
      (log/debug "TODO update player location in level"))))
