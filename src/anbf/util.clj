(ns anbf.util
  (:require [clojure.string :as string]))

(def vi-directions
  {:NW \y :N \k :NE \u
   :W  \h        :E \l
   :SW \b :S \j :SE \n})

(def priority-default 0)
; bots should not go beyond these (their interface specifies an int priority)
(def priority-top (dec Integer/MIN_VALUE))
(def priority-bottom (inc Integer/MAX_VALUE))

(defn kw->enum [cls kw]
  (some->> kw name string/upper-case (Enum/valueOf cls)))

(defn enum->kw [v]
  (if (or (nil? v) (keyword? v)) v (.getKeyword v)))

(defn ctrl
  "Returns a char representing CTRL+<ch>"
  [ch]
  (char (- (int ch) 96)))

(def esc (str (char 27)))

(def backspace (str (char 8)))

(defn config-get
  "Get a configuration key from the config map or return the default, without a default throw an exception if the key is not present."
  ([config key default]
   (get config key default))
  ([config key]
   (or (get config key)
       (throw (IllegalStateException.
                (str "Configuration missing key: " key))))))

(defn re-first-groups
  "Return the capturing groups of the first match"
  [re text]
  (some-> (re-seq re text) first (subvec 1)))

(defn re-first-group
  "Return the first capturing group of the first match."
  [re text]
  (some-> (re-seq re text) first (nth 1)))

(defn re-any-group
  "Return the first non-nil capturing group of the first match."
  [re text]
  (some-> (re-seq re text) first (subvec 1) ((partial some identity))))

(defn min-by [f coll] (if (seq coll) (apply (partial min-key f) coll)))
(defn first-min-by [f coll] (if (seq coll)
                              (apply (partial min-key f) (reverse coll))))

(defn to-position
  "Sequence of keys to move the cursor from the corner to the given position"
  [pos]
  (apply str (concat (repeat 10 \H) (repeat 4 \K) ; to corner
                     (repeat (dec (:y pos)) \j) (repeat (:x pos) \l))))

(defn find-first [p s] (first (filter p s)))

(defn parse-int [x] (if x (Integer/parseInt x)))
