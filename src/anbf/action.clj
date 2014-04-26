(ns anbf.action
  (:require [clojure.tools.logging :as log]))

(defprotocol Action
  (trigger [this]
           "Returns the string to write to NetHack to perform the action."))

(def vi-directions
  {7 \y 8 \k 9 \u
   4 \h      6 \l
   1 \b 2 \j 3 \n})

(defmacro ^:private defaction [action args & impl]
  `(do (defrecord ~action ~args ~@impl)
       (defn ~(symbol (str \- action)) ~args
         (~(symbol (str action \.)) ~@args))))

(defaction Move [dir]
  Action
  (trigger [this]
    (or (vi-directions dir)
        (throw (IllegalArgumentException.
                 (str "Invalid direction: " dir))))))

(defaction Pray []
  Action
  (trigger [this]
    "#pray\n"))

(gen-class :name anbf.action.Actions
           :methods [^:static [Move [int] anbf.action.Action]
                     ^:static [Pray [] anbf.action.Action]])
