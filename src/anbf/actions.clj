(ns anbf.actions
  (:require [clojure.tools.logging :as log]
            [anbf.handlers :refer :all]
            [anbf.action :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.tile :refer :all]
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

(def ^:private feature-re #"^There is(?: an?)?(?: \w+)* (trap|staircase (?:up|down)|spider web|web|ice|opulent throne|fountain|sink|grave|doorway|open door|broken door) here\.")

(defn- feature-here [msg]
  ; TODO bear trap?
  (if (.startsWith msg "There is an altar")
    :altar
    (case (re-first-group feature-re msg)
      "opulent throne" :throne
      "trap" :trap
      "spider web" :trap
      "web" :trap
      "ice" :ice
      "doorway" :door-open
      "open door" :door-open
      "broken door" :floor
      "staircase up" :stairs-up
      "staircase down" :stairs-down
      "fountain" :fountain
      "sink" :sink
      "grave" :grave
      nil)))

; TODO change branch-id on special levelport (quest/ludios)
(defaction Move [dir]
  (handler [_ {:keys [game] :as anbf}]
    ; TODO "The XXX gets angry!"
    (reify
      ToplineMessageHandler
      (message [_ msg]
        ; TODO if not conf/stun
        (or (if (= msg "It's a wall.")
              (swap! game #(update-curlvl-at % (in-direction (:player %) dir)
                                             assoc :feature :wall)))
            (if (re-seq #"Wait!  That's a .*mimic!" msg)
              (swap! game #(update-curlvl-at % (in-direction (:player %) dir)
                                             assoc :feature nil)))
            (if-let [feature (feature-here msg)]
              (update-on-known-position
                anbf #(update-curlvl-at % (:player %)
                                        assoc :feature feature)))))
      ReallyAttackHandler
      (really-attack [_ _]
        (swap! game #(update-curlvl-monster % (in-direction (:player %) dir)
                                            assoc :peaceful true))
        nil)))
  (trigger [_]
    (str (or (vi-directions (enum->kw dir))
             (throw (IllegalArgumentException.
                      (str "Invalid direction: " dir)))))))

(defaction Pray []
  ; TODO mark for timeout est.
  (handler [_ _])
  (trigger [_] "#pray\n"))

(defn- update-searched [{:keys [player] :as game}]
  (reduce #(update-curlvl-at %1 %2 update-in [:searched] inc) game
          (conj (neighbors player) player)))

(defaction Search []
  ; TODO "What are you looking for?  The exit?" => engulfed
  (handler [_ {:keys [game] :as anbf}]
    (swap! game update-searched) nil)
  (trigger [_] "s"))

(defn stairs-handler [anbf]
  (let [old-game (-> anbf :game deref)
        old-branch (branch-key old-game)
        old-dlvl (:dlvl old-game)
        old-stairs (at-player old-game)]
    (update-on-known-position anbf
      (fn [{new-branch :branch-id new-dlvl :dlvl :as new-game}]
        (log/debug "asc/desc from" old-dlvl "to" new-dlvl "new-branch is" new-branch)
        (let [new-stairs (at-player new-game)]
          (if (and (stairs? old-stairs) (not= old-dlvl new-dlvl))
            (cond-> new-game
              (not (:leads-to old-stairs))
              (assoc-in [:dungeon :levels old-branch old-dlvl :tiles
                         (dec (:y old-stairs)) (:x old-stairs) :leads-to]
                        new-branch)
              (not (:leads-to new-stairs))
              (update-curlvl-at new-stairs
                                into {:leads-to old-branch
                                      :feature (opposite-stairs
                                                 (:feature old-stairs))}))
            new-game))))
    (reify BOTLHandler
      (botl [this status]
        (when-let [new-dlvl (and (not= old-dlvl (:dlvl status))
                                 (:dlvl status))]
          ; get the new branch-id from the stairs or create new and mark the stairs
          (swap! (:game anbf)
                 #(assoc % :branch-id
                         (get old-stairs :leads-to
                              (initial-branch-id % new-dlvl))))
          (log/debug "choosing branch-id" (:branch-id @(:game anbf)) "for new dlvl " new-dlvl))))))

; TODO handle branches like Descend
(defaction Ascend []
  (handler [_ anbf]
    (stairs-handler anbf))
  (trigger [_] "<"))

(defaction Descend []
  (handler [_ anbf]
    (stairs-handler anbf))
  (trigger [_] ">"))

; TODO "The XXX gets angry!"
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

(defn- look-feature [msg]
  (if (re-seq #"^(?:Things that (?:are|you feel) here:|You (?:see|feel))" msg)
    :floor
    (feature-here msg)))

(defaction Look []
  (handler [_ {:keys [game] :as anbf}]
    (reify ToplineMessageHandler
      (message [_ text]
        (if-let [feature (look-feature text)]
          (swap! game #(update-curlvl-at % (:player %)
                                         assoc :feature feature))))))
  (trigger [this] ":"))

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

(defn examine-handler [anbf]
  (reify ActionHandler
    (choose-action [_ game]
      ; TODO if not blind check engulfer
      ; TODO ambiguous monsters
      (when-not (:feature (at-player game))
        (log/debug "examining tile")
        (->Look)))))

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
