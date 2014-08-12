(ns anbf.monster
  (:require [clojure.tools.logging :as log]
            [anbf.frame :refer :all]
            [anbf.montype :refer :all]))

(defrecord Monster
  [x y
   known ; last turn observed
   glyph
   color
   type ; nil while ambiguous
   awake
   friendly
   peaceful ; nil while undetermined
   remembered]) ; not currently known

(defmethod print-method Monster [m w]
  (.write w (str "#anbf.monster.Monster"
                 (into {} (update-in m [:type]
                                     #(if % (str "<" (:name %) ">")))))))

(defn hostile? [monster]
  (and (not (:peaceful monster))
       (not (:friendly monster))))

(defn known-monster
  "Create a known monster on known location (like when marking Medusa on Medusa's)"
  [x y type]
  (Monster. x y nil (:glyph type) (:color type) type false false
            (if (:hostile (:tags type)) false (:peaceful (:tags type))) true))

(defn new-monster
  "Create a monster by appearance"
  [x y known glyph color]
  (let [type (get-in appearance->monster [glyph color])
        tags (:tags type)
        peaceful (if (:hostile tags) false (:peaceful tags))]
    (Monster. x y known glyph (non-inverse color) type nil
              (boolean (inverse? color)) peaceful false)))
