(ns anbf.monster
  (:require [clojure.tools.logging :as log]
            [anbf.frame :refer :all]))

(defrecord Monster
  [x y
   known ; last turn observed
   glyph
   color
   awake
   ; TODO type (+ transfer)
   friendly
   peaceful
   cancelled])

(defn hostile? [monster]
  (and (not (:peaceful monster))
       (not (:friendly monster))))

(defn new-monster [x y known glyph color]
  ; TODO infer unambiguous type/properties immediately
  (Monster. x y known glyph (non-inverse color) nil (inverse? color) false false))
