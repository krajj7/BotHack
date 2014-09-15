(ns anbf.itemtype
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [anbf.itemdata :refer :all]
            [anbf.util :refer :all]))

(defmulti kw->itemtype
  "{keyword => some ItemType map factory function}"
  identity)

; TODO gen interface for java
(defmacro ^:private defitemtype
  "Defines a record for the item type and a var with a list of all possible items of the type according to the data map with defaults filled in"
  ([recname varname recfields datamap]
   `(defitemtype ~recname ~varname ~recfields ~datamap {}))
  ([recname varname recfields datamap defaults]
   `(do (defrecord ~recname ~recfields
          anbf.util.Type (typekw [~'_] ~(keyword (string/lower-case recname))))
        (defmethod kw->itemtype
          ~(keyword (string/replace varname #"s$" "")) [~'_]
          ~(symbol (str "map->" recname)))
        (def ~varname
          (map (comp ~(symbol (str "map->" recname))
                     (partial merge ~defaults))
               ~datamap)))))

(defitemtype Spellbook spellbooks
  [name glyph price weight level time ink skill direction emergency]
  spellbook-data
  {:glyph \+
   :weight 50
   :material :paper
   :appearances spellbook-appearances})

(defitemtype Amulet amulets
  [name glyph price weight material edible]
  amulet-data
  {:glyph \"
   :weight 20
   :price 150
   :appearances amulet-appearances})

(defitemtype Weapon weapons
  [name plural glyph price weight sdam ldam to-hit hands material stackable]
  weapon-data
  {:glyph \)})

(defitemtype Wand wands
  [name glyph price weight max-charges zaptype]
  wand-data
  {:glyph \/
   :weight 7
   :appearances wands-appearances})

(defitemtype Gem gems
  [name plural glyph price weight hardness appearance material stackable]
  gem-data
  {:glyph \*
   :weight 1
   :stackable true})

(defitemtype Armor armor
  [name glyph price weight ac mc material subtype]
  armor-data
  {:glyph \[})

(defitemtype Food food
  [name plural glyph price weight nutrition vegan vegetarian material unsafe stackable]
  food-data
  {:glyph \%})

(defitemtype Other others
  [name plural glyph price weight stackable material]
  others-data)

(defitemtype Tool tools
  [name plural glyph price weight subtype material charge stackable]
  tool-data
  {:glyph \(})

(defitemtype Statue statues
  [name glyph price weight subtype material monster]
  statue-data)

(defitemtype Scroll scrolls
  [name plural glyph price weight ink stackable]
  scroll-data
  {:glyph \?
   :weight 5
   :stackable true
   :appearances scroll-appearances
   :material :paper})

(defitemtype Ring rings
  [name glyph price weight chargeable]
  ring-data
  {:weight 3
   :appearances ring-appearances
   :glyph \=})

(defitemtype Potion potions
  [name plural glyph price weight material stackable]
  potion-data
  {:appearances potion-appearances
   :stackable true
   :glyph \!
   :material :glass})

(def item-kinds
  {:spellbook spellbooks
   :amulet amulets
   :weapon weapons
   :wand wands
   :gem gems
   :armor armor
   :food food
   :other others
   :tool tools
   :statue statues
   :scroll scrolls
   :ring rings
   :potion potions})

(def items "all possible item identities" ; TODO custom fruits (bones)
  (apply concat (vals item-kinds)))

(def name->item "{name => ItemType} for all items"
  (into {} (for [{:keys [name] :as i} items] [name i])))

(def plural->singular "{plural => singular} for all stackable item appearances"
  (merge generic-plurals
         (into {}
               (for [{:keys [name plural] :as id} items
                     :when plural]
                 [plural name]))))
