(ns anbf.monster
  (:require [anbf.frame :refer :all])
  (:require [clojure.tools.logging :as log]))

(defrecord Monster [glyph color friendly]) ; TODO expand

(defn monster [glyph color]
  (Monster. glyph color (inverse? color)))
