(ns anbf.actions
  (:require [clojure.tools.logging :as log]
            [anbf.handlers :refer :all]
            [anbf.action :refer :all]
            [anbf.player :refer :all]
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

(def ^:private feature-re #"^(?:You see|There is)(?: an?)?(?: \w+)* (trap|spiked pit|pit|staircase (?:up|down)|spider web|web|ice|opulent throne|fountain|sink|grave|doorway|squeaky board|open door|broken door) (?:here|below you)\.")

(defn- feature-here [msg]
  (condp re-seq msg
    #"You tear through \w+ web!|You (?:burn|dissolve) \w+ spider web!|You hear a (?:loud|soft) click(?:!|\.)" :floor
    #"There is an altar" :altar
    (case (re-first-group feature-re msg)
      "opulent throne" :throne
      "trap" :trap
      "spider web" :trap
      "squeaky board" :trap
      "web" :trap
      "pit" :trap
      "spiked pit" :trap
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
    (let [old-pos (position (:player @game))
          target (at-curlvl @game (in-direction old-pos dir))]
      (update-on-known-position anbf #(if (not= (position (:player %)) old-pos)
                                        (assoc-in % [:player :trapped] false)
                                        %))
      (if (and (diagonal dir) (item? (:glyph target)))
        (update-on-known-position anbf
          #(if (and (= (position (:player %)) old-pos)
                    (not (curlvl-monster-at % target)))
             (do (log/debug "stuck on diagonal movement => door at" target)
                 (update-curlvl-at % (in-direction old-pos dir)
                                   assoc :feature :door-open))
             %))))
    (reify
      ToplineMessageHandler
      (message [_ msg]
        ; TODO "The XXX gets angry!"
        (or (condp re-seq msg
              #".*: \"Closed for inventory\"" ; TODO possible degradation
              (update-on-known-position
                anbf (fn mark-shop [game]
                       (reduce #(if (door? %2)
                                  (update-curlvl-at %1 %2 assoc :room :shop)
                                  %1) game
                               (neighbors (curlvl game) (at-player game)))))
              #"You crawl to the edge of the pit\.|You disentangle yourself\."
              (swap! game assoc-in [:player :trapped] false)
              #"You fall into \w+ pit!|bear trap closes on your|You stumble into \w+ spider web!$|You are stuck to the web\.$"
              (update-on-known-position anbf assoc-in [:player :trapped] true)
              ; TODO #"You are carrying too much to get through"
              nil)
            (when-not (dizzy? (:player @game))
              (condp re-seq msg
                #"You try to move the boulder, but in vain\."
                (swap! game #(update-curlvl-at
                               % (reduce in-direction (:player %) [dir dir])
                               assoc :feature :rock))
                #"It's a wall\."
                (swap! game #(update-curlvl-at % (in-direction (:player %) dir)
                                               assoc :feature :wall))
                #"Wait!  That's a .*mimic!"
                (swap! game #(update-curlvl-at % (in-direction (:player %) dir)
                                               assoc :feature nil))
                nil))
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

(defn- mark-stair-branch [game tile branch]
  "Mark where we ended up on the new level as leading to the branch we came from.  Pets and followers might have displaced us from the stairs which may not be visible, so just mark the surroundings too, it only matters for the stairs."
  (reduce #(update-curlvl-at %1 %2 assoc :branch-id branch)
          game
          (conj tile (neighbors tile))))

(defn stairs-handler [anbf]
  (let [old-game (-> anbf :game deref)
        old-branch (branch-key old-game)
        old-dlvl (:dlvl old-game)
        old-stairs (at-player old-game)]
    (update-on-known-position anbf
      (fn [{new-branch :branch-id new-dlvl :dlvl :as new-game}]
        (log/debug "asc/desc from" old-dlvl "to" new-dlvl "new-branch is" new-branch)
        (if-let [new-stairs (and (not= old-dlvl new-dlvl)
                                 (stairs? old-stairs)
                                 (not (:branch-id old-stairs))
                                 (at-player new-game))]
          (-> new-game
              (assoc-in [:dungeon :levels old-branch old-dlvl :tiles
                         (dec (:y old-stairs)) (:x old-stairs) :branch-id]
                        new-branch)
              (mark-stair-branch new-stairs old-branch))
          new-game)))
    (reify DlvlChangeHandler
      (dlvl-changed [this old-dlvl new-dlvl]
        ; get the new branch-id from the stairs or create new and mark the stairs
        (swap! (:game anbf)
               #(assoc % :branch-id
                       (get old-stairs :branch-id
                            (initial-branch-id % new-dlvl))))
        (log/debug "choosing branch-id" (:branch-id @(:game anbf))
                   "for dlvl" new-dlvl)))))

(defaction Ascend []
  (handler [_ anbf]
    (stairs-handler anbf))
  (trigger [_] "<"))

(defaction Descend []
  (handler [_ anbf]
    (stairs-handler anbf))
  (trigger [_] ">"))

(defaction Kick [dir]
  ; TODO "The XXX gets angry!"
  (handler [_ {:keys [game] :as anbf}]
    (reify ToplineMessageHandler
      (message [_ msg]
        (condp re-seq msg
          #"Your .* is in no shape for kicking."
          (swap! game #(assoc-in [:player :leg-hurt] true))
          #"You can't move your leg!|There's not enough room to kick down here."
          (swap! game #(assoc-in [:player :trapped] true))
          nil))))
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

#_(defaction Inventory []
  (handler [_ anbf]
    (reify InventoryHandler
      (inventory-list [_ inventory]
        nil))
    nil) ; TODO
  (trigger [_] ("i")))

(defn examine-handler [anbf]
  (reify ActionHandler
    (choose-action [_ game]
      ; TODO if not blind check engulfer
      ; TODO ambiguous monsters
      (when-not (or (blind? (:player game)) (:feature (at-player game)))
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
