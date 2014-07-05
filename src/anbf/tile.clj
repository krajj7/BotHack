(ns anbf.tile
  (:require [clojure.tools.logging :as log]
            [anbf.position :refer [neighbors]]
            [anbf.util :refer :all]))

(defrecord Tile
  [x y
   glyph
   color
   feature ; :rock :floor :wall :stairs-up :stairs-down :corridor :altar :water :trap :door-open :door-closed :door-locked :sink :fountain :grave :throne :bars :tree :drawbridge :lava :ice :underwater
   seen
   walked
   dug
   searched ; no. of times searched TODO
   items ; [Items]
   engraving
   room]
  anbf.bot.ITile)

(defn initial-tile [x y]
  (Tile. x y \space nil nil false nil false 0 [] nil nil))

(defn monster?
  ([glyph color] ; works better on rogue level and for worm tails
   (or (and (= \~ glyph) (= :brown color))
       (and (monster? glyph) (or (some? color) (not= \: glyph)))))
  ([glyph]
   (or (and (Character/isLetterOrDigit glyph) (not= \0 glyph))
       (#{\& \@ \' \; \:} glyph))))

(defn item?
  ([glyph color] ; works better on rogue level
   (or (item? glyph) (and (= \: glyph) (nil? color))))
  ([glyph]
   (#{\" \) \[ \! \? \/ \= \+ \* \( \` \0 \$ \% \,} glyph)))

(defn boulder? [tile]
  (and (= (:glyph tile) \0) (nil? (:color tile))))

(defn door? [tile]
  (#{:door-open :door-closed :door-locked} (:feature tile)))

(defn stairs? [tile]
  (#{:stairs-up :stairs-down} (:feature tile)))

(defn opposite-stairs [feature]
  {:pre [(#{:stairs-up :stairs-down} feature)]}
  (if (= :stairs-up feature)
    :stairs-down
    :stairs-up))

(defn walkable? [tile]
  (and (not (boulder? tile))
       (or (and (item? (:glyph tile) (:color tile)) (nil? (:feature tile)))
           (#{:ice :floor :air :altar :door-open :sink :fountain :trap :corridor
              :throne :grave :stairs-up :stairs-down} (:feature tile)))))

(defn transparent?
  "For unexplored tiles just a guess"
  [{:keys [feature monster items] :as tile}]
  (boolean (and (not (boulder? tile))
                (not (#{:rock :wall :tree :door-closed :cloud} feature))
                (or feature monster (seq items)))))

(defn searched [level tile]
  "How many times the tile has been searched directly (not by searching a neighbor)"
  (apply min (map :searched (neighbors level tile))))

(defn walkable-by [{:keys [feature] :as tile} glyph]
  ; full inferred monster type could be made available here but shouldn't be necessary
  (cond-> tile
    (and (not (#{\X \P} glyph))
         (door? tile)) (assoc-in [:feature] :door-open)
    (and (not= \X glyph)
         (#{:rock :wall :tree} feature)) (assoc-in [:feature] :corridor)))

(defn- infer-feature [current new-glyph new-color]
  (case new-glyph
    \space current ; TODO :air
    \. (if (= new-color :cyan) :ice :floor)
    \< :stairs-up
    \> :stairs-down
    \\ (if (= new-color :yellow) :throne :grave)
    \{ (if (nil? new-color) :sink :fountain)
    \} :TODO ; TODO :bars :tree :drawbridge :lava :underwater
    \# :corridor ; TODO :cloud
    \_ :altar
    \~ :water
    \^ :trap
    \] :door-closed
    \| (if (= new-color :brown) :door-open :wall)
    \- (if (= new-color :brown) :door-open :wall)
    (log/error "unrecognized feature" new-glyph new-color "was" current)))

; they might not have actually been seen but there's usually not much to see in walls/water
(defn- mark-seen-features [tile]
  (if (#{:wall :door-closed :water} (:feature tile))
    (assoc tile :seen true)
    tile))

(defn- update-feature-with-item [tile]
  ; if items appeared in rock/wall we should check it out
  (if (and (empty? (:items tile)) (#{:rock :wall} (:feature tile)))
    (assoc tile :feature nil)
    tile))

; TODO handle properly, mark new unknown items, track disappeared items
(defn- update-items [tile new-glyph new-color]
  (if-let [item (item? new-glyph new-color)]
    (-> tile
        update-feature-with-item
        (assoc :items [item]))
    (if (or (= \space new-glyph) (monster? new-glyph new-color))
      tile
      (assoc tile :items []))))

(defn- update-feature [tile new-glyph new-color]
  (cond (monster? new-glyph new-color) (walkable-by tile new-glyph)
        (item? new-glyph new-color) tile
        :else (update-in tile [:feature]
                         infer-feature new-glyph new-color)))

(defn- mark-dug-tile [new-tile old-tile]
  (if (and (#{:wall :rock} (:feature old-tile))
           (#{:corridor :floor} (:feature new-tile)))
    (assoc new-tile :dug true)
    new-tile))

(defn parse-tile [tile new-glyph new-color]
  (if (or (not= new-color (:color tile)) (not= new-glyph (:glyph tile)))
    (-> tile
        (update-items new-glyph new-color)
        (update-feature new-glyph new-color)
        (mark-dug-tile tile)
        (assoc :glyph new-glyph :color new-color)
        mark-seen-features)
    tile))

(def shop-types
  {"general store" :general
   "used armor dealership" :armor
   "second-hand bookstore" :book
   "liquor emporium" :potion
   "antique weapons outlet" :weapons
   "delicatessen" :food
   "jewelers" :gem
   "quality apparel and accessories" :wand
   "hardware store" :tool
   "rare books" :book
   "lighting store" :light})

(def shops (apply hash-set :shop (vals shop-types)))

(defn shop? [tile]
  (shops (:room tile)))
