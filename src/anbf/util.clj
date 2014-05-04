(ns anbf.util
  (:require [clojure.string :as string]))

(defn enum [cls kw]
  (if kw (Enum/valueOf cls (string/upper-case (name kw)))))

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
