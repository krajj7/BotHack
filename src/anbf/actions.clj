(ns anbf.actions
  (:require [clojure.tools.logging :as log]
            [anbf.action :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.position :refer :all]
            [anbf.delegator :refer :all]
            [anbf.util :refer :all]))

(defmacro ^:private defaction [action args & impl]
  `(do (defrecord ~action ~args anbf.bot.IAction ~@impl)
       (defn ~(symbol (str \- action)) ~args
         (~(symbol (str action \.)) ~@args))))

(def vi-directions
  {:NW \y :N \k :NE \u
   :W  \h        :E \l
   :SW \b :S \j :SE \n})

(defaction Move [dir]
  (handler [_ {:keys [game] :as anbf}]
    ; TODO door on failed diagonal, "The XXX gets angry!"
    (reify ReallyAttackHandler
      (really-attack [_ _]
        (swap! game #(update-curlvl-monster % (in-direction (:player %) dir)
                                            assoc :peaceful true))
        nil)))
  (trigger [_]
    (str (or (vi-directions (enum->kw dir))
             (throw (IllegalArgumentException.
                      (str "Invalid direction: " dir)))))))

(defaction Pray []
  (handler [_ _])
  (trigger [_] "#pray\n"))

(defn- update-searched [{:keys [player] :as game}]
  (reduce #(update-curlvl-at %1 %2 update-in [:searched] inc) game
          (conj (neighbors player) player)))

; TODO separate handler and game-update-fn for actions?
(defaction Search []
  (handler [_ {:keys [game] :as anbf}]
    (swap! game update-searched) nil)
  (trigger [_] "s"))

(defaction Ascend []
  (handler [_ _])
  (trigger [_] "<"))

(defaction Descend []
  (handler [_ _])
  (trigger [_] ">"))

; TODO "As you kick the door, it crashes open!" => :floor
(defaction Kick [dir]
  (handler [_ _])
  (trigger [_] (str (ctrl \d) (vi-directions (enum->kw dir)))))

(defaction Close [dir]
  (handler [_ {:keys [game] :as anbf}]
    (reify ToplineMessageHandler
      (message [_ text]
        (let [door (in-direction (:player @game) dir)]
          (case text
            "This door is already closed." (swap! game update-curlvl-at door
                                                  assoc :feature :door-closed)
            "You see no door there." (swap! game update-curlvl-at door
                                            assoc :feature nil)
            nil)))))
  (trigger [this] (str \c (vi-directions (enum->kw dir)))))

(defaction Open [dir]
  (handler [_ {:keys [game] :as anbf}]
    (reify ToplineMessageHandler
      (message [_ text]
        (let [door (in-direction (:player @game) dir)]
          (case text
            "This door is locked." (swap! game update-curlvl-at door
                                          assoc :feature :door-locked)
            "This door is already open." (swap! game update-curlvl-at door
                                                assoc :feature :door-open)
            "The door opens." (swap! game update-curlvl-at door
                                     assoc :feature :door-open)
            "You see no door there." (swap! game update-curlvl-at door
                                            assoc :feature nil)
            nil)))))
  (trigger [_] (str \o (vi-directions (enum->kw dir)))))

(defn- -withHandler
  ([action handler]
   (-withHandler action priority-default handler))
  ([action priority handler]
   action)) ; TODO assoc user handler, reg on performed, dereg on choose + clojure API

; factory functions for Java bots ; TODO the rest
(gen-class
  :name anbf.bot.Actions
  :methods [^:static [Move [anbf.bot.Direction] anbf.bot.IAction]
            ^:static [Pray [] anbf.bot.IAction]
            ^:static [withHandler [anbf.bot.IAction Object] anbf.bot.IAction]
            ^:static [withHandler [anbf.bot.IAction int Object]
                      anbf.bot.IAction]])
