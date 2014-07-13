(ns anbf.monster
  (:require [clojure.tools.logging :as log]
            [anbf.frame :refer :all]
            [anbf.montype :refer :all]))

(defrecord Monster
  [x y
   known ; last turn observed
   glyph
   type ; nil while ambiguous
   color
   awake
   friendly
   peaceful ; nil while undetermined
   cancelled])

(defmethod print-method Monster [m w]
  (.write w (str "#anbf.monster.Monster"
                 (into {} (update-in m [:type]
                                     #(if % (str "<" (:name %) ">")))))))

(defn hostile? [monster]
  (and (not (:peaceful monster))
       (not (:friendly monster))))

(defn new-monster [x y known glyph color]
  (let [type (get-in appearance->monster [glyph color])
        tags (get type :tags)
        peaceful (if (:hostile tags) false (:peaceful tags))]
    (Monster. x y known glyph type (non-inverse color) nil
              (boolean (inverse? color)) peaceful false)))
