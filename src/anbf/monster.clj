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
                 :first-known 0
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
                   :first-known known
                   :glyph glyph
                   :color (non-inverse color)
                   :type type
                   :friendly (boolean (inverse? color))
                   :peaceful (default-peaceful? type)
                   :remembered false})))

(defn unknown-monster [x y turn]
  (new-monster x y turn \I nil))

(defn typename [m] (get-in m [:type :name]))

(defn shopkeeper? [m] (= "shopkeeper" (typename m)))

(defn high-priest? [m] (= "high priest" (typename m)))

(defn demon-lord? [m] (and (every? #{:demon :prince} (get-in m [:type :tags]))))

(defn oracle? [m] (= "Oracle" (typename m)))

(defn unique? [m] (get-in m [:type :gen-flags :unique]))

(defn priest? [m] (some-> (typename m) (.contains "priest")))

(defn unicorn? [m] (some-> (typename m) (.contains " unicorn")))

(defn mimic? [m] (some-> (typename m) (.contains " mimic")))

(defn werecreature? [m] (get-in m [:type :tags :were]))

(defn drowner? [m] (some #(= :wrap (:damage-type %)) (:attacks (:type m))))

(defn covetous? [m] (some #{:covetous :wants-arti :wants-amulet :wants-book}
                          (:tags (:type m))))

(defn steals? [m]
  (or (covetous? m)
      (some #(#{:steal-amulet :steal-items} (:damage-type %))
            (:attacks (:type m)))))

(defn ignores-e? [m] (get-in m [:type :resistances :elbereth]))

(defn sees-invisible? [m] (get-in m [:type :tags :see-invis]))

(defn follower? [m] (get-in m [:type :tags :follows]))

(defmacro ^:private def-tag-pred [kw]
  `(defn ~(symbol (str (subs (str kw) 1) \?)) [~'monster]
     (get-in ~'monster [:type :tags ~kw])))

#_(pprint (macroexpand-1 '(def-tag-pred :mindless)))

(defmacro ^:private def-tag-preds
  "defines item type predicates: food? armor? tool? etc."
  []
  `(do ~@(for [kw [:mindless :undead :sessile :guard :human :nasty :rider
                   :strong :infravisible]]
           `(def-tag-pred ~kw))))

(def-tag-preds)

(defn passive? [monster]
  (every? #(= :passive (:type %)) (:attacks (:type monster))))

(defn corrosive?
  "Corrodes weapon passively when hit?"
  [monster]
  (some #(and (= :passive (:type %))
              (#{:corrode :acid} (:damage-type %)))
        (:attacks (:type monster))))
