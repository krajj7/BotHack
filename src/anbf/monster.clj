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
   cancelled
   remembered]) ; not currently known

(defmethod print-method Monster [m w]
  (.write w (str "#anbf.monster.Monster"
                 (into {} (update-in m [:type]
                                     #(if % (str "<" (:name %) ">")))))))

(defn hostile? [monster]
  (and (not (:peaceful monster))
       (not (:friendly monster))))

(defn new-monster [x y known glyph color]
  (let [type (get-in appearance->monster [glyph color])
        tags (:tags type)
        peaceful (if (:hostile tags) false (:peaceful tags))]
    (Monster. x y known glyph (non-inverse color) type nil
              (boolean (inverse? color)) peaceful false false)))
