(ns anbf.action
  (:require [flatland.ordered.set :refer [ordered-set]]
            [clojure.tools.logging :as log]))

(defprotocol Action
  (perform [this anbf]
           "Returns the string to write to NetHack to perform the action."))

(def vi-directions
  {1 \b
   2 \j
   3 \n
   4 \h
   6 \l
   7 \y
   8 \k
   9 \u})

(defrecord Move [dir]
  Action
  (perform [this anbf]
    (str (or (vi-directions dir) 
             (throw (IllegalArgumentException.
                      (str "Invalid direction: " dir)))))))
