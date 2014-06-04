(ns anbf.monster
  (:require [anbf.frame :refer :all])
  (:require [clojure.tools.logging :as log]))

(defrecord Monster
  [x y
   seen ; last turn
   glyph
   color
   ; TODO type
   friendly
   peaceful
   cancelled])

(defn new-monster [x y seen glyph color]
  ; TODO infer unambiguous type/properties immediately
  (Monster. x y seen glyph color (inverse? color) false false))
