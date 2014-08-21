(ns anbf.item
  (:require [anbf.util :refer :all]))

(defrecord Item [label])

(defn slot-item
  "Turns a string 'h - an octagonal amulet (being worn)' or [char String] pair into a [char Item] pair"
  ([s]
   (if-let [[slot item] (re-first-groups #"\s*(.)  ?[-+#] (.*)\s*$" s)]
     (slot-item (.charAt slot 0) item)))
  ([chr item]
   [chr (->Item item)]))
