(ns anbf.util
  (:require [clojure.string :as string]))

(def priority-default 0)
; bots should not go beyond these (their interface specifies an int priority)
(def priority-top (dec Integer/MIN_VALUE))
(def priority-bottom (inc Integer/MAX_VALUE))

(defn kw->enum [cls kw]
  (if kw (Enum/valueOf cls (string/upper-case (name kw)))))

(defn enum->kw [v]
  (if (or (not v) (keyword? v)) v (keyword (.name v))))

(defn ctrl
  "Returns a char representing CTRL+<ch>"
  [ch]
  (char (- (int ch) 96)))

(def esc (str (char 27)))

(def backspace (str (char 8)))

(defrecord Position [x y]
  anbf.bot.IPosition
  (x [this] (:x this))
  (y [this] (:y this)))

(defn config-get
  "Get a configuration key from the config map or return the default, without a default throw an exception if the key is not present."
  ([config key default]
   (get config key default))
  ([config key]
   (or (get config key)
       (throw (IllegalStateException.
                (str "Configuration missing key: " key))))))
