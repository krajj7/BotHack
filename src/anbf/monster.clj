(ns anbf.monster
  (:require [clojure.tools.logging :as log]
            [anbf.frame :refer :all]))

(defrecord Monster
  [x y
   seen ; last turn
   glyph
   color
   ; TODO type (+ transfer)
   friendly
   peaceful
   cancelled])

(defn hostile? [monster]
  (and (not (:peaceful monster))
       (not (:friendly monster))))

(defn new-monster [x y seen glyph color]
  ; TODO infer unambiguous type/properties immediately
  (Monster. x y seen glyph color (inverse? color) false false))
