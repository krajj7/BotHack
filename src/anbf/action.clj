(ns anbf.action
  (:require [clojure.tools.logging :as log]
            [anbf.util :refer :all]))

(defprotocol Action
  (trigger [this]
           "Returns the string to write to NetHack to perform the action."))

(extend-type anbf.bot.IAction
  Action
  (trigger [this] (.trigger this)))

(def <=> (comp #(Integer/signum %) compare))

(defn towards [from to]
  (get {[1  1] :NW [0  1] :N [-1  1] :NE
        [1  0] :W            [-1  0] :E
        [1 -1] :SW [0 -1] :S [-1 -1] :SE}
    ((juxt #(<=> (:x %1) (:x %2))
           #(<=> (:y %1) (:y %2))) from to) ))

(def directions [nil :SW :S :SE :W nil :E :NW :N :NE])

(def vi-directions
  {:NW \y :N \k :NE \u
   :W  \h        :E \l
   :SW \b :S \j :SE \n})

(defn vi-direction [d]
  "Returns vi-direction for direction keyword or index"
  (vi-directions (get directions d d)))

(defmacro ^:private defaction [action args & impl]
  `(do (defrecord ~action ~args anbf.bot.IAction ~@impl)
       (defn ~(symbol (str \- action)) ~args
         (~(symbol (str action \.)) ~@args))))

(defaction Move [dir]
  (trigger [this]
    (str (or (vi-direction dir)
             (throw (IllegalArgumentException.
                      (str "Invalid direction: " dir)))))))

(defaction Pray []
  (trigger [this] "#pray\n"))

(defaction Search []
  (trigger [this] "s"))

(defn- -withHandler
  ([action handler]
   (-withHandler action priority-default handler))
  ([action priority handler]
   action)) ; TODO assoc user handler, reg on performed, dereg on choose + clojure API

; factory functions for Java bots
(gen-class
  :name anbf.bot.Actions
  :methods [^:static [Move [int] anbf.bot.IAction]
            ^:static [Pray [] anbf.bot.IAction]
            ^:static [withHandler [anbf.bot.IAction Object] anbf.bot.IAction]
            ^:static [withHandler [anbf.bot.IAction int Object]
                      anbf.bot.IAction]])
