(ns anbf.monster
  (:require [anbf.frame :refer :all])
  (:require [clojure.tools.logging :as log]))

(defrecord Monster [glyph color friendly peaceful]) ; TODO expand

; TODO friendly with conflict => not peaceful

(defn monster [glyph color]
  (Monster. glyph color (inverse? color) false))
