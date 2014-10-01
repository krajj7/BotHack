(ns anbf.monster
  (:require [clojure.tools.logging :as log]
            [anbf.util :refer :all]
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
                 (into {} (update m :type #(if % (str "<" (:name %) ">")))))))

(defn hostile? [monster]
  (and (not (:peaceful monster))
       (not (:friendly monster))))

(defn- default-peaceful? [monster-type]
  (if (:hostile (:tags monster-type))
    false
    (:peaceful (:tags monster-type))))

(defn known-monster
  "Create a known monster on known location (like when marking Medusa on Medusa's)"
  [x y type]
  (map->Monster {:x x
                 :y y
                 :glyph (:glyph type)
                 :color (:color type)
                 :known 0
                 :type type
                 :awake false
                 :friendly false
                 :peaceful (default-peaceful? type)
                 :remembered true}))

(defn new-monster
  "Create a monster by appearance"
  [x y known glyph color]
  (let [type (get-in appearance->monster [glyph color])]
    (map->Monster {:x x
                   :y y
                   :known known
                   :glyph glyph
                   :color (non-inverse color)
                   :type type
                   :friendly (boolean (inverse? color))
                   :peaceful (default-peaceful? type)
                   :remembered false})))

(defn unknown-monster [x y turn]
  (new-monster x y turn \I nil))

(defn shopkeeper? [m] (= "shopkeeper" (get-in m [:type :name])))
