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
  (if-let [m (first (re-seq re text))]
    (if (vector? m)
      (subvec m 1))))

(defn re-first-group
  "Return the first capturing group of the first match or whole first match if no groups"
  [re text]
  (if-let [m (first (re-seq re text))]
    (if (vector? m)
      (nth m 1)
      m)))

(defn re-any-group
  "Return the first non-nil capturing group of the first match."
  [re text]
  (if-let [m (first (re-seq re text))]
    (if (vector? m)
      (some identity (subvec m 1)))))

(defn max-by [f coll]
  (if (seq coll)
    (apply (partial max-key f) coll)))

(defn min-by [f coll]
  (if (seq coll)
    (apply (partial min-key f) coll)))

(defn first-min-by [f coll]
  (if (seq coll)
    (apply (partial min-key f) (reverse coll))))

(defn to-position
  "Sequence of keys to move the cursor from the corner to the given position"
  [pos]
  (string/join (concat (repeat 10 \H) (repeat 4 \K) ; to corner
                       (repeat (dec (:y pos)) \j) (repeat (:x pos) \l))))

(defn find-first [p s] (first (filter p s)))

(defn parse-int [x]
  (if x
    (Integer/parseInt x)))

(defprotocol Type (typekw [this]))

(extend-type nil
  Type
  (typekw [_] nil))

(defn random-nth [coll]
  (if (seq coll)
    (rand-nth coll)))

(defn more-than?
  "Does coll have more than n elements?"
  [n coll]
  (->> coll (drop n) seq))

(defn less-than?
  "Does coll have less than n elements?"
  [n coll]
  (->> coll (take n) count (not= n)))

(defn update [m k f & args]
  (apply update-in m [k] f args))

(defn not-any-fn? [& fns]
  (complement (apply some-fn fns)))

(defn str->kw [s]
  (if s (keyword (string/lower-case s))))

(defn select-some
  "Like select-keys but only selects keys with non-nil values"
  [m ks]
  (select-keys m (filter #(some? (% m)) ks)))

(defn indexed [coll]
  (map vector (range) coll))

(defmacro condp-all
  "Like condp but doesn't short-circuit (evaluates all matching clauses incl. the default)"
  [pred expr & clauses]
  (let [gpred (gensym "pred__")
        gexpr (gensym "expr__")
        emit (fn emit [pred expr args]
               (let [[[a b c :as clause] more]
                       (split-at (if (= :>> (second args)) 3 2) args)
                       n (count clause)]
                 (cond
                  (= 0 n) `(throw (IllegalArgumentException. (str "No matching clause: " ~expr)))
                  (= 1 n) a
                  (= 2 n) `(do (if (~pred ~a ~expr)
                                 ~b)
                               ~(emit pred expr more))
                  :else `(do (if-let [p# (~pred ~a ~expr)]
                               (~c p#))
                             ~(emit pred expr more)))))]
    `(let [~gpred ~pred
           ~gexpr ~expr]
       ~(emit gpred gexpr clauses))))
