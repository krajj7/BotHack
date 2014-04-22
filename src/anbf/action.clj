(ns anbf.action
  (:require [flatland.ordered.set :refer [ordered-set]]
            [clojure.tools.logging :as log]))

(defprotocol Action
  (perform [this anbf]
           "Returns the string to write to NetHack to perform the action."))

(def vi-directions
  {7 \y 8 \k 9 \u
   4 \h      6 \l
   1 \b 2 \j 3 \n})

(defrecord Move [dir]
  Action
  (perform [this anbf]
    (or (vi-directions dir)
        (throw (IllegalArgumentException.
                 (str "Invalid direction: " dir))))))

(defrecord Pray []
  Action
  (perform [this anbf]
    "#pray\n"))

; TODO gen-class typed static factory functions for Java
