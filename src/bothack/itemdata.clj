(ns bothack.itemdata
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [bothack.montype :refer [monster-types]]))

(def scroll-appearances
  (map (partial str "scroll labeled ")
       ["ZELGO MER" "JUYED AWK YACC" "NR 9" "XIXAXA XOXAXA XUXAXA"
        "PRATYAVAYAH" "DAIYEN FOOELS" "LEP GEX VEN ZEA" "PRIRUTSENIE"
        "ELBIB YLOH" "TEMOV" "VERR YED HORRE" "VENZAR BORGAVVE" "THARR"
        "YUM YUM" "KERNOD WEL" "ELAM EBOW" "DUAM XNAHT" "KIRJE"
        "ANDOVA BEGARIN" "VE FORBRYDERNE" "HACKEM MUCHE" "VELOX NEB"
        "READ ME" "FOOBIE BLETCH" "GARVEN DEH"]))

(def potion-appearances
  (map (partial format "%s potion")
       ["ruby" "pink" "orange" "yellow" "emerald" "cyan" "magenta" "purple-red"
        "puce" "milky" "swirly" "bubbly" "smoky" "cloudy" "effervescent"
        "black" "golden" "brown" "fizzy" "dark" "white" "murky" "dark green"
        "sky blue" "brilliant blue"]))

(def generic-plurals "{plural => singular} for non-identified stackable item appearances"
  (into {} (concat
             (for [s scroll-appearances]
               [(string/replace s #"^scroll" "scrolls") s])
             (map #(vector (str % \s) %)
                  (concat
                    ; weapons
                    ["stout spear" "runed arrow" "runed dagger" "runed spear"
                     "crude arrow" "crude dagger" "crude spear" "throwing spear"
                     "throwing star" "bamboo arrow"]
                    ; gems
                    ["yellowish brown gem" "gray stone"]
                    (map (partial format "%s gem")
                         ["white" "red" "blue" "orange" "black" "green" "yellow"
                          "violet"])
                    ; food
                    ["tin" "egg"]
                    ; tools
                    ["candle"]
                    ; scroll
                    ["stamped scroll" "unlabeled scroll"]
                    ; potion
                    ["clear potion"]
                    potion-appearances)))))

(def amulet-appearances
  (map (partial format "%s amulet")
       ["circular" "spherical" "oval" "triangular" "pyramidal" "square"
        "concave" "hexagonal" "octagonal"]))

(def amulet-data
  [{:name "Amulet of Yendor"
    :price 30000
    :artifact true
    :fullname "The Amulet of Yendor"
    :appearances ["Amulet of Yendor"]
    :safe true
    :material :mithril}
   {:name "Eye of the Aethiopica"
    :price 4000
    :edible 1
    :artifact true
    :fullname "The Eye of the Aethiopica"
    :base "amulet of ESP"
    :material :iron}
   {:name "cheap plastic imitation of the Amulet of Yendor"
    :price 0
    :safe true
    :appearances ["Amulet of Yendor"]
    :material :plastic}
   {:name "amulet of change"
    :edible 1
    :safe true
    :auto-id true
    :material :iron}
   {:name "amulet of ESP"
    :edible 1
    :safe true
    :material :iron}
   {:name "amulet of life saving"
    :safe true
    :material :iron}
   {:name "amulet of magical breathing"
    :safe true
    :edible 1
    :material :iron}
   {:name "amulet of reflection"
    :safe true
    :material :iron}
   {:name "amulet of restful sleep"
    :edible 1
    :material :iron}
   {:name "amulet of strangulation"
    :edible 1
    :auto-id true
    :material :iron}
   {:name "amulet of unchanging"
    :edible 1
    :safe true
    :material :iron}
   {:name "amulet versus poison"
    :safe true
    :edible 1
    :material :iron}])

(def spellbook-appearances
  (map (partial format "%s spellbook")
       ["parchment" "vellum" "ragged" "mottled" "stained" "cloth" "leather"
        "white" "pink" "red" "orange" "yellow" "velvet" "turquoise" "cyan"
        "indigo" "magenta" "purple" "violet" "tan" "plaid" "gray" "wrinkled"
        "dusty" "bronze" "copper" "silver" "gold" "glittering" "shining" "dull"
        "thin" "thick" "dog eared" "light green" "dark green" "light blue"
        "dark blue" "light brown" "dark brown"]))

(def spellbook-data
  [{:name "Book of the Dead"
    :artifact true
    :price 10000
    :weight 20
    :level 7
    :time 0
    :ink 0
    :fullname "The Book of the Dead"
    :appearances ["papyrus spellbook"]
    :emergency 0}
   {:name "spellbook of blank paper"
    :price 0
    :level 0
    :time 0
    :ink 0
    :appearances ["plain spellbook"]
    :emergency 0}
   {:name "spellbook of force bolt"
    :price 100
    :level 1
    :time 2
    :ink 10
    :emergency 0
    :skill :attack
    :direction :beam}
   {:name "spellbook of drain life"
    :price 200
    :level 2
    :time 2
    :ink 20
    :emergency 0
    :skill :attack
    :direction :beam}
   {:name "spellbook of magic missile"
    :price 200
    :level 2
    :time 2
    :ink 20
    :role :wizard
    :emergency 0
    :skill :attack
    :direction :ray}
   {:name "spellbook of cone of cold"
    :price 400
    :level 4
    :time 21
    :ink 40
    :role :valkyrie
    :emergency 0
    :skill :attack
    :direction {
        :unskilled :ray
        :basic :ray
        :skilled :distant
        :expert :distant}}
   {:name "spellbook of fireball"
    :price 400
    :level 4
    :time 12
    :ink 40
    :emergency 0
    :skill :attack
    :direction {
        :unskilled :ray
        :basic :ray
        :skilled :distant
        :expert :distant}}
   {:name "spellbook of finger of death"
    :price 700
    :level 7
    :time 80
    :ink 70
    :emergency 0
    :skill :attack
    :direction :ray}
   {:name "spellbook of healing"
    :price 100
    :level 1
    :time 2
    :ink 10
    :emergency 1
    :skill :healing
    :direction :beam}
   {:name "spellbook of cure blindness"
    :price 200
    :level 2
    :time 2
    :ink 20
    :emergency 1
    :skill :healing
    :direction :nodir}
   {:name "spellbook of cure sickness"
    :price 300
    :level 3
    :time 6
    :ink 30
    :role :healer
    :emergency 1
    :skill :healing
    :direction :nodir}
   {:name "spellbook of extra healing"
    :price 300
    :level 3
    :time 10
    :ink 30
    :emergency 1
    :skill :healing
    :direction :beam}
   {:name "spellbook of stone to flesh"
    :price 300
    :level 3
    :time 2
    :ink 30
    :emergency 0
    :skill :healing
    :direction :beam}
   {:name "spellbook of restore ability"
    :price 400
    :level 4
    :time 15
    :ink 40
    :role :monk
    :emergency 1
    :skill :healing
    :direction :nodir}
   {:name "spellbook of detect monsters"
    :price 100
    :level 1
    :time 1
    :ink 10
    :emergency 0
    :skill :divination
    :direction :nodir}
   {:name "spellbook of light"
    :price 100
    :level 1
    :time 1
    :ink 10
    :emergency 0
    :skill :divination
    :direction :nodir}
   {:name "spellbook of detect food"
    :price 200
    :level 2
    :time 3
    :ink 20
    :emergency 0
    :skill :divination
    :direction :nodir}
   {:name "spellbook of clairvoyance"
    :price 300
    :level 3
    :time 6
    :ink 30
    :role :samurai
    :emergency 0
    :skill :divination
    :direction :nodir}
   {:name "spellbook of detect unseen"
    :price 300
    :level 3
    :time 8
    :ink 30
    :emergency 0
    :skill :divination
    :direction :nodir}
   {:name "spellbook of identify"
    :price 300
    :level 3
    :time 12
    :ink 30
    :emergency 0
    :skill :divination
    :direction :nodir}
   {:name "spellbook of detect treasure"
    :price 400
    :level 4
    :time 15
    :ink 40
    :role :rogue
    :emergency 0
    :skill :divination
    :direction :nodir}
   {:name "spellbook of magic mapping"
    :price 500
    :level 5
    :time 35
    :ink 50
    :role :archeologist
    :emergency 0
    :skill :divination
    :direction :nodir}
   {:name "spellbook of sleep"
    :price 100
    :level 1
    :time 1
    :ink 10
    :emergency 0
    :skill :enchantment
    :direction :ray}
   {:name "spellbook of confuse monster"
    :price 200
    :level 2
    :time 2
    :ink 20
    :emergency 0
    :skill :enchantment
    :direction :nodir}
   {:name "spellbook of slow monster"
    :price 200
    :level 2
    :time 2
    :ink 20
    :emergency 0
    :skill :enchantment
    :direction :beam}
   {:name "spellbook of cause fear"
    :price 300
    :level 3
    :time 6
    :ink 30
    :emergency 0
    :skill :enchantment
    :direction :nodir}
   {:name "spellbook of charm monster"
    :price 300
    :level 3
    :time 6
    :ink 30
    :role :tourist
    :emergency 0
    :skill :enchantment
    :direction :nodir}
   {:name "spellbook of protection"
    :price 100
    :level 1
    :time 3
    :ink 10
    :emergency 0
    :skill :clerical
    :direction :nodir}
   {:name "spellbook of create monster"
    :price 200
    :level 2
    :time 3
    :ink 20
    :emergency 0
    :skill :clerical
    :direction :nodir}
   {:name "spellbook of remove curse"
    :price 300
    :level 3
    :time 10
    :ink 30
    :role :priest
    :emergency 1
    :skill :clerical
    :direction :nodir}
   {:name "spellbook of create familiar"
    :price 600
    :level 6
    :time 42
    :ink 60
    :emergency 0
    :skill :clerical
    :direction :nodir}
   {:name "spellbook of turn undead"
    :price 600
    :level 6
    :time 48
    :ink 60
    :role :knight
    :emergency 0
    :skill :clerical
    :direction :beam}
   {:name "spellbook of jumping"
    :price 100
    :level 1
    :time 3
    :ink 10
    :emergency 0
    :skill :escape
    :direction "jump"}
   {:name "spellbook of haste self"
    :price 300
    :level 3
    :time 8
    :ink 30
    :role :barbarian
    :emergency 0
    :skill :escape
    :direction :nodir}
   {:name "spellbook of invisibility"
    :price 400
    :level 4
    :time 15
    :ink 40
    :role :ranger
    :emergency 0
    :skill :escape
    :direction :nodir}
   {:name "spellbook of levitation"
    :price 400
    :level 4
    :time 12
    :ink 40
    :emergency 0
    :skill :escape
    :direction :nodir}
   {:name "spellbook of teleport away"
    :price 600
    :level 6
    :time 36
    :ink 60
    :emergency 0
    :skill :escape
    :direction :beam}
   {:name "spellbook of knock"
    :price 100
    :level 1
    :time 1
    :ink 10
    :emergency 0
    :skill :matter
    :direction :beam}
   {:name "spellbook of wizard lock"
    :price 200
    :level 2
    :time 3
    :ink 20
    :emergency 0
    :skill :matter
    :direction :beam}
   {:name "spellbook of dig"
    :price 500
    :level 5
    :time 30
    :ink 50
    :role :caveman
    :emergency 0
    :skill :matter
    :direction :ray}
   {:name "spellbook of polymorph"
    :price 600
    :level 6
    :time 48
    :ink 60
    :emergency 0
    :skill :matter
    :direction :beam}
   {:name "spellbook of cancellation"
    :price 700
    :level 7
    :time 64
    :ink 70
    :emergency 0
    :skill :matter
    :direction :beam}])

(def weapon-data
  [{:name "Cleaver"
    :artifact true
    :base "battle-axe"
    :sdam "d8+d6+d4"
    :ldam "2d6+2d4"
    :to-hit "d3"
    :hands 2
    :weight 120
    :price 1500
    :material :iron}
   {:name "Demonbane"
    :artifact true
    :base "long sword"
    :sdam "d8"
    :ldam "d12"
    :to-hit "d5"
    :hands 1
    :weight 40
    :price 2500
    :material :iron}
   {:name "Dragonbane"
    :artifact true
    :base "broadsword"
    :sdam "2d4"
    :ldam "d6+1"
    :to-hit "d5"
    :hands 1
    :weight 40
    :price 500
    :material :iron}
   {:name "Excalibur"
    :artifact true
    :base "long sword"
    :sdam "d8+d10"
    :ldam "d12+d10"
    :to-hit "d5"
    :hands 1
    :weight 40
    :price 4000
    :material :iron}
   {:name "Fire Brand"
    :artifact true
    :base "long sword"
    :sdam "d8"
    :ldam "d12"
    :to-hit "d5"
    :hands 1
    :weight 40
    :price 3000
    :material :iron}
   {:name "Frost Brand"
    :artifact true
    :base "long sword"
    :sdam "d8"
    :ldam "d12"
    :to-hit "d5"
    :hands 1
    :weight 40
    :price 3000
    :material :iron}
   {:name "Giantslayer"
    :artifact true
    :base "long sword"
    :sdam "d8"
    :ldam "d12"
    :to-hit "d5"
    :hands 1
    :weight 40
    :price 200
    :material :iron}
   {:name "Grayswandir"
    :artifact true
    :base "silver saber"
    :sdam "d8"
    :ldam "d8"
    :to-hit "d5"
    :hands 1
    :weight 40
    :price 8000
    :material :silver}
   {:name "Grimtooth"
    :artifact true
    :base "orcish dagger"
    :sdam "d6+d3"
    :ldam "d6+d3"
    :to-hit "d2+2"
    :hands 1
    :weight 10
    :price 300
    :material :iron}
   {:name "Longbow of Diana"
    :artifact true
    :base "bow"
    :sdam "d2"
    :ldam "d2"
    :to-hit "d5"
    :hands 1
    :weight 30
    :price 4000
    :fullname "The Longbow of Diana"
    :material :wood}
   {:name "Magicbane"
    :artifact true
    :base "athame"
    :sdam "2d4"
    :ldam "d4+d3"
    :to-hit "d5+2"
    :hands 1
    :weight 10
    :price 3500
    :material :iron}
   {:name "Mjollnir"
    :artifact true
    :base "war hammer"
    :sdam "d4+1"
    :ldam "d4"
    :to-hit "d5"
    :hands 1
    :weight 50
    :price 4000
    :material :iron}
   {:name "Ogresmasher"
    :artifact true
    :base "war hammer"
    :sdam "d4+1"
    :ldam "d4"
    :to-hit "d5"
    :hands 1
    :weight 50
    :price 200
    :material :iron}
   {:name "Orcrist"
    :artifact true
    :base "elven broadsword"
    :sdam "d6+d4"
    :ldam "d6+1"
    :to-hit "d5"
    :hands 1
    :weight 70
    :price 2000
    :material :wood}
   {:name "Sceptre of Might"
    :artifact true
    :base "mace"
    :sdam "d6+1"
    :ldam "d6"
    :to-hit "d5"
    :hands 1
    :weight 30
    :price 2500
    :fullname "The Sceptre of Might"
    :material :iron}
   {:name "Snickersnee"
    :artifact true
    :base "katana"
    :sdam "d10+d8"
    :ldam "d12+d8"
    :to-hit 1
    :hands 1
    :weight 40
    :price 1200
    :material :iron}
   {:name "Staff of Aesculapius"
    :artifact true
    :base "quarterstaff"
    :sdam "d6"
    :ldam "d6"
    :to-hit 0
    :hands 2
    :weight 40
    :price 5000
    :fullname "The Staff of Aesculapius"
    :material :wood}
   {:name "Sting"
    :artifact true
    :base "elven dagger"
    :sdam "d5"
    :ldam "d3"
    :to-hit "d5+2"
    :hands 1
    :weight 10
    :price 800
    :material :wood}
   {:name "Stormbringer"
    :artifact true
    :base "runesword"
    :sdam "2d4+d2"
    :ldam "d6+d2+1"
    :to-hit "d5"
    :hands 1
    :weight 40
    :price 8000
    :material :iron}
   {:name "Sunsword"
    :artifact true
    :base "long sword"
    :sdam "d8"
    :ldam "d12"
    :to-hit "d5"
    :hands 1
    :weight 40
    :price 1500
    :material :iron}
   {:name "Tsurugi of Muramasa"
    :artifact true
    :base "tsurugi"
    :sdam "d16+d8"
    :ldam "2d8+2d6"
    :to-hit 2
    :hands 2
    :weight 60
    :price 4500
    :fullname "The Tsurugi of Muramasa"
    :material :metal}
   {:name "Trollsbane"
    :artifact true
    :base "morning star"
    :sdam "2d4"
    :ldam "d6+1"
    :to-hit "d5"
    :hands 1
    :weight 120
    :price 200
    :material :iron}
   {:name "Vorpal Blade"
    :artifact true
    :base "long sword"
    :sdam "d8+1"
    :ldam "d12+1"
    :to-hit "d5"
    :hands 1
    :weight 40
    :price 4000
    :material :iron}
   {:name "Werebane"
    :artifact true
    :base "silver saber"
    :sdam "d8"
    :ldam "d8"
    :to-hit "d2"
    :hands 1
    :weight 40
    :price 1500
    :material :silver}
   {:name "aklys"
    :sdam "d6"
    :ldam "d3"
    :to-hit 0
    :hands 1
    :weight 15
    :price 4
    :material :iron
    :appearances ["thonged club"]
    :plural "aklyses"}
   {:name "arrow"
    :sdam "d6"
    :ldam "d6"
    :to-hit 0
    :hands 1
    :weight 1
    :price 2
    :material :iron
    :plural "arrows"
    :stackable 1}
   {:name "athame"
    :sdam "d4"
    :ldam "d3"
    :to-hit 2
    :hands 1
    :weight 10
    :price 4
    :material :iron
    :plural "athames"
    :stackable 1}
   {:name "axe"
    :sdam "d6"
    :ldam "d4"
    :to-hit 0
    :hands 1
    :weight 60
    :price 8
    :material :iron
    :plural "axes"}
   {:name "bardiche"
    :sdam "2d4"
    :ldam "3d4"
    :to-hit 0
    :hands 2
    :weight 120
    :price 7
    :material :iron
    :appearances ["long poleaxe"]
    :plural "bardiches"}
   {:name "battle-axe"
    :sdam "d8+d4"
    :ldam "d6+2d4"
    :to-hit 0
    :hands 2
    :weight 120
    :price 40
    :material :iron
    :appearances ["double-headed axe"]
    :plural "battle-axes"}
   {:name "bec de corbin"
    :sdam "d8"
    :ldam "d6"
    :to-hit 0
    :hands 2
    :weight 100
    :price 8
    :material :iron
    :appearances ["beaked polearm"]
    :plural "bec de corbins"}
   {:name "bill-guisarme"
    :sdam "2d4"
    :ldam "d10"
    :to-hit 0
    :hands 2
    :weight 120
    :price 7
    :material :iron
    :appearances ["hooked polearm"]
    :plural "bill-guisarmes"}
   {:name "boomerang"
    :sdam "d9"
    :ldam "d9"
    :to-hit 0
    :hands 1
    :weight 5
    :price 20
    :material :wood
    :plural "boomerangs"
    :stackable 1}
   {:name "bow"
    :sdam "d2"
    :ldam "d2"
    :to-hit 0
    :hands 1
    :weight 30
    :price 60
    :material :wood
    :plural "bows"}
   {:name "broadsword"
    :sdam "2d4"
    :ldam "d6+1"
    :to-hit 0
    :hands 1
    :weight 70
    :price 10
    :material :iron
    :plural "broadswords"}
   {:name "bullwhip"
    :sdam "d2"
    :ldam 1
    :to-hit 0
    :hands 1
    :weight 20
    :price 4
    :material :leather
    :plural "bullwhips"}
   {:name "club"
    :sdam "d6"
    :ldam "d3"
    :to-hit 0
    :hands 1
    :weight 30
    :price 3
    :material :wood
    :plural "clubs"}
   {:name "crossbow"
    :sdam "d2"
    :ldam "d2"
    :to-hit 0
    :hands 1
    :weight 50
    :price 40
    :material :wood
    :plural "crossbows"}
   {:name "crossbow bolt"
    :sdam "d4+1"
    :ldam "d6+1"
    :to-hit 0
    :hands 1
    :weight 1
    :price 2
    :material :iron
    :plural "crossbow bolts"
    :stackable 1}
   {:name "crysknife"
    :sdam "d10"
    :ldam "d10"
    :to-hit 3
    :hands 1
    :weight 20
    :price 100
    :material :mineral
    :plural "crysknives"}
   {:name "dagger"
    :sdam "d4"
    :ldam "d3"
    :to-hit 2
    :hands 1
    :weight 10
    :price 4
    :material :iron
    :plural "daggers"
    :stackable 1}
   {:name "dart"
    :sdam "d3"
    :ldam "d2"
    :to-hit 0
    :hands 1
    :weight 1
    :price 2
    :material :iron
    :plural "darts"
    :stackable 1}
   {:name "dwarvish mattock"
    :sdam "d12"
    :ldam "d8+2d6"
    :to-hit "-1"
    :hands 2
    :weight 120
    :price 50
    :material :iron
    :appearances ["broad pick"]
    :plural "dwarvish mattocks"}
   {:name "dwarvish short sword"
    :sdam "d7"
    :ldam "d8"
    :to-hit 0
    :hands 1
    :weight 30
    :price 10
    :material :iron
    :appearances ["broad short sword"]
    :plural "dwarvish short swords"}
   {:name "dwarvish spear"
    :sdam "d8"
    :ldam "d8"
    :to-hit 0
    :hands 1
    :weight 35
    :price 3
    :material :iron
    :appearances ["stout spear"]
    :plural "dwarvish spears"
    :stackable 1}
   {:name "elven arrow"
    :sdam "d7"
    :ldam "d6"
    :to-hit 0
    :hands 1
    :weight 1
    :price 2
    :material :wood
    :appearances ["runed arrow"]
    :plural "elven arrows"
    :stackable 1}
   {:name "elven bow"
    :sdam "d2"
    :ldam "d2"
    :to-hit 0
    :hands 1
    :weight 30
    :price 60
    :material :wood
    :appearances ["runed bow"]
    :plural "elven bows"}
   {:name "elven broadsword"
    :sdam "d6+d4"
    :ldam "d6+1"
    :to-hit 0
    :hands 1
    :weight 70
    :price 10
    :material :wood
    :appearances ["runed broadsword"]
    :plural "elven broadswords"}
   {:name "elven dagger"
    :sdam "d5"
    :ldam "d3"
    :to-hit 2
    :hands 1
    :weight 10
    :price 4
    :material :wood
    :appearances ["runed dagger"]
    :plural "elven daggers"
    :stackable 1}
   {:name "elven short sword"
    :sdam "d8"
    :ldam "d8"
    :to-hit 0
    :hands 1
    :weight 30
    :price 10
    :material :wood
    :appearances ["runed short sword"]
    :plural "elven short swords"}
   {:name "elven spear"
    :sdam "d7"
    :ldam "d8"
    :to-hit 0
    :hands 1
    :weight 30
    :price 3
    :material :wood
    :appearances ["runed spear"]
    :plural "elven spears"
    :stackable 1}
   {:name "fauchard"
    :sdam "d6"
    :ldam "d8"
    :to-hit 0
    :hands 2
    :weight 60
    :price 5
    :material :iron
    :appearances ["pole sickle"]
    :plural "fauchards"}
   {:name "flail"
    :sdam "d6+1"
    :ldam "2d4"
    :to-hit 0
    :hands 1
    :weight 15
    :price 4
    :material :iron
    :plural "flails"}
   {:name "flintstone"
    :sdam "d6"
    :ldam "d6"
    :to-hit 0
    :hands 1
    :weight 10
    :price 1
    :material :mineral
    :plural "flintstones"
    :stackable 1}
   {:name "glaive"
    :sdam "d6"
    :ldam "d10"
    :to-hit 0
    :hands 2
    :weight 75
    :price 6
    :material :iron
    :appearances ["single-edged polearm"]
    :plural "glaives"}
   {:name "guisarme"
    :sdam "2d4"
    :ldam "d8"
    :to-hit 0
    :hands 2
    :weight 80
    :price 5
    :material :iron
    :appearances ["pruning hook"]
    :plural "guisarmes"}
   {:name "halberd"
    :sdam "d10"
    :ldam "2d6"
    :to-hit 0
    :hands 2
    :weight 150
    :price 10
    :material :iron
    :appearances ["angled poleaxe"]
    :plural "halberds"}
   {:name "javelin"
    :sdam "d6"
    :ldam "d6"
    :to-hit 0
    :hands 1
    :weight 20
    :price 3
    :material :iron
    :appearances ["throwing spear"]
    :plural "javelins"
    :stackable 1}
   {:name "katana"
    :sdam "d10"
    :ldam "d12"
    :to-hit 1
    :hands 1
    :weight 40
    :price 80
    :material :iron
    :appearances ["samurai sword"]
    :plural "katanas"}
   {:name "knife"
    :sdam "d3"
    :ldam "d2"
    :to-hit 0
    :hands 1
    :weight 5
    :price 4
    :material :iron
    :plural "knives"
    :stackable 1}
   {:name "lance"
    :sdam "d6"
    :ldam "d8"
    :to-hit 0
    :hands 1
    :weight 180
    :price 10
    :material :iron
    :plural "lances"}
   {:name "long sword"
    :sdam "d8"
    :ldam "d12"
    :to-hit 0
    :hands 1
    :weight 40
    :price 15
    :material :iron
    :plural "long swords"}
   {:name "lucern hammer"
    :sdam "2d4"
    :ldam "d6"
    :to-hit 0
    :hands 2
    :weight 150
    :price 7
    :material :iron
    :appearances ["pronged polearm"]
    :plural "lucern hammers"}
   {:name "mace"
    :sdam "d6+1"
    :ldam "d6"
    :to-hit 0
    :hands 1
    :weight 30
    :price 5
    :material :iron
    :plural "maces"}
   {:name "morning star"
    :sdam "2d4"
    :ldam "d6+1"
    :to-hit 0
    :hands 1
    :weight 120
    :price 10
    :material :iron
    :plural "morning stars"}
   {:name "orcish arrow"
    :sdam "d5"
    :ldam "d6"
    :to-hit 0
    :hands 1
    :weight 1
    :price 2
    :material :iron
    :appearances ["crude arrow"]
    :plural "orcish arrows"
    :stackable 1}
   {:name "orcish bow"
    :sdam "d2"
    :ldam "d2"
    :to-hit 0
    :hands 1
    :weight 30
    :price 60
    :material :wood
    :appearances ["crude bow"]
    :plural "orcish bows"}
   {:name "orcish dagger"
    :sdam "d3"
    :ldam "d3"
    :to-hit 2
    :hands 1
    :weight 10
    :price 4
    :material :iron
    :appearances ["crude dagger"]
    :plural "orcish daggers"
    :stackable 1}
   {:name "orcish short sword"
    :sdam "d5"
    :ldam "d8"
    :to-hit 0
    :hands 1
    :weight 30
    :price 10
    :material :iron
    :appearances ["crude short sword"]
    :plural "orcish short swords"}
   {:name "orcish spear"
    :sdam "d5"
    :ldam "d8"
    :to-hit 0
    :hands 1
    :weight 30
    :price 3
    :material :iron
    :appearances ["crude spear"]
    :plural "orcish spears"
    :stackable 1}
   {:name "partisan"
    :sdam "d6"
    :ldam "d6+1"
    :to-hit 0
    :hands 2
    :weight 80
    :price 10
    :material :iron
    :appearances ["vulgar polearm"]
    :plural "partisans"}
   {:name "quarterstaff"
    :sdam "d6"
    :ldam "d6"
    :to-hit 0
    :hands 2
    :weight 40
    :price 5
    :material :wood
    :appearances ["staff"]
    :plural "quarterstaves"}
   {:name "ranseur"
    :sdam "2d4"
    :ldam "2d4"
    :to-hit 0
    :hands 2
    :weight 50
    :price 6
    :material :iron
    :appearances ["hilted polearm"]
    :plural "ranseurs"}
   {:name "rubber hose"
    :sdam "d4"
    :ldam "d3"
    :to-hit 0
    :hands 1
    :weight 20
    :price 3
    :material :plastic
    :plural "rubber hoses"}
   {:name "runesword"
    :sdam "2d4"
    :ldam "d6+1"
    :to-hit 0
    :hands 1
    :weight 40
    :price 300
    :material :iron
    :appearances ["runed broadsword"]
    :plural "runeswords"}
   {:name "scalpel"
    :sdam "d3"
    :ldam "d3"
    :to-hit 2
    :hands 1
    :weight 5
    :price 6
    :material :metal
    :plural "scalpels"
    :stackable 1}
   {:name "scimitar"
    :sdam "d8"
    :ldam "d8"
    :to-hit 0
    :hands 1
    :weight 40
    :price 15
    :material :iron
    :appearances ["curved sword"]
    :plural "scimitars"}
   {:name "short sword"
    :sdam "d6"
    :ldam "d8"
    :to-hit 0
    :hands 1
    :weight 30
    :price 10
    :material :iron
    :plural "short swords"}
   {:name "shuriken"
    :sdam "d8"
    :ldam "d6"
    :to-hit 2
    :hands 1
    :weight 1
    :price 5
    :material :iron
    :appearances ["throwing star"]
    :plural "shuriken"
    :stackable 1}
   {:name "silver arrow"
    :sdam "d6"
    :ldam "d6"
    :to-hit 0
    :hands 1
    :weight 1
    :price 5
    :material :silver
    :plural "silver arrows"
    :stackable 1}
   {:name "silver dagger"
    :sdam "d4"
    :ldam "d3"
    :to-hit 2
    :hands 1
    :weight 12
    :price 40
    :material :silver
    :plural "silver daggers"
    :stackable 1}
   {:name "silver saber"
    :sdam "d8"
    :ldam "d8"
    :to-hit 0
    :hands 1
    :weight 40
    :price 75
    :material :silver
    :plural "silver sabers"}
   {:name "silver spear"
    :sdam "d6"
    :ldam "d8"
    :to-hit 0
    :hands 1
    :weight 36
    :price 40
    :material :silver
    :plural "silver spears"
    :stackable 1}
   {:name "sling"
    :sdam "d2"
    :ldam "d2"
    :to-hit 0
    :hands 1
    :weight 3
    :price 20
    :material :leather
    :plural "slings"}
   {:name "spear"
    :sdam "d6"
    :ldam "d8"
    :to-hit 0
    :hands 1
    :weight 30
    :price 3
    :material :iron
    :plural "spears"
    :stackable 1}
   {:name "spetum"
    :sdam "d6+1"
    :ldam "2d6"
    :to-hit 0
    :hands 2
    :weight 50
    :price 5
    :material :iron
    :appearances ["forked polearm"]
    :plural "spetums"}
   {:name "stiletto"
    :sdam "d3"
    :ldam "d2"
    :to-hit 0
    :hands 1
    :weight 5
    :price 4
    :material :iron
    :plural "stilettos"
    :stackable 1}
   {:name "trident"
    :sdam "d6+1"
    :ldam "3d4"
    :to-hit 0
    :hands 1
    :weight 25
    :price 5
    :material :iron
    :plural "tridents"}
   {:name "tsurugi"
    :sdam "d16"
    :ldam "d8+2d6"
    :to-hit 2
    :hands 2
    :weight 60
    :price 500
    :material :metal
    :appearances ["long samurai sword"]
    :plural "tsurugis"}
   {:name "two-handed sword"
    :sdam "d12"
    :ldam "3d6"
    :to-hit 0
    :hands 2
    :weight 150
    :price 50
    :material :iron
    :plural "two-handed swords"}
   {:name "voulge"
    :sdam "2d4"
    :ldam "2d4"
    :to-hit 0
    :hands 2
    :weight 125
    :price 5
    :material :iron
    :appearances ["pole cleaver"]
    :plural "voulges"}
   {:name "war hammer"
    :sdam "d4+1"
    :ldam "d4"
    :to-hit 0
    :hands 1
    :weight 50
    :price 5
    :material :iron
    :plural "war hammers"}
   {:name "worm tooth"
    :sdam "d2"
    :ldam "d2"
    :to-hit 0
    :hands 1
    :weight 20
    :price 2
    :material "none"
    :plural "worm teeth"}
   {:name "ya"
    :sdam "d7"
    :ldam "d7"
    :to-hit 1
    :hands 1
    :weight 1
    :price 4
    :material :metal
    :appearances ["bamboo arrow"]
    :plural "ya"
    :stackable 1}
   {:name "yumi"
    :sdam "d2"
    :ldam "d2"
    :to-hit 0
    :hands 1
    :weight 30
    :price 60
    :material :wood
    :appearances ["long bow"]
    :plural "yumis"}])

(def wand-data
  [{:name "wand of light"
    :price 100
    :max-charges 15
    :autoid true
    :engrave :id
    :target false
    :zaptype :nodir}
   {:name "wand of nothing"
    :price 100
    :max-charges 15
    :engrave :nothing
    :target true
    :zaptype :beam}
   {:name "wand of digging"
    :price 150
    :max-charges 8
    :autoid true
    :engrave :id
    :target true
    :zaptype :ray}
   {:name "wand of enlightenment"
    :price 150
    :max-charges 15
    :engrave :id
    :autoid true
    :target false
    :zaptype :nodir}
   {:name "wand of locking"
    :price 150
    :max-charges 8
    :target true
    :engrave :nothing
    :zaptype :beam}
   {:name "wand of magic missile"
    :price 150
    :max-charges 8
    :target true
    :engrave :bullet
    :autoid true
    :zaptype :ray}
   {:name "wand of make invisible"
    :price 150
    :max-charges 8
    :engrave :vanish
    :target true
    :zaptype :beam}
   {:name "wand of opening"
    :price 150
    :max-charges 8
    :engrave :nothing
    :target true
    :zaptype :beam}
   {:name "wand of probing"
    :price 150
    :max-charges 8
    :engrave :nothing
    :target true
    :zaptype :beam}
   {:name "wand of secret door detection"
    :price 150
    :max-charges 15
    :engrave :nothing
    :target false
    :zaptype :nodir}
   {:name "wand of slow monster"
    :price 150
    :max-charges 8
    :engrave :slow
    :target true
    :zaptype :beam}
   {:name "wand of speed monster"
    :price 150
    :max-charges 8
    :engrave :speed
    :target true
    :zaptype :beam}
   {:name "wand of striking"
    :price 150
    :max-charges 8
    :engrave :fights
    :target true
    :zaptype :beam}
   {:name "wand of undead turning"
    :price 150
    :max-charges 8
    :engrave :nothing
    :target true
    :zaptype :beam}
   {:name "wand of cold"
    :price 175
    :max-charges 8
    :engrave :ice
    :autoid true
    :target true
    :zaptype :ray}
   {:name "wand of fire"
    :price 175
    :max-charges 8
    :target true
    :autoid true
    :engrave :id
    :zaptype :ray}
   {:name "wand of lightning"
    :price 175
    :max-charges 8
    :autoid true
    :target true
    :engrave :id
    :zaptype :ray}
   {:name "wand of sleep"
    :price 175
    :max-charges 8
    :autoid true
    :target true
    :engrave :stop
    :zaptype :ray}
   {:name "wand of cancellation"
    :price 200
    :max-charges 8
    :engrave :vanish
    :safe true
    :target true
    :zaptype :beam}
   {:name "wand of create monster"
    :price 200
    :max-charges 15
    :engrave :id
    :autoid false
    :target false
    :zaptype :nodir}
   {:name "wand of polymorph"
    :price 200
    :max-charges 8
    :target true
    :engrave :change
    :zaptype :beam}
   {:name "wand of teleportation"
    :price 200
    :max-charges 8
    :target true
    :engrave :vanish
    :zaptype :beam}
   {:name "wand of death"
    :price 500
    :max-charges 8
    :target true
    :autoid true
    :engrave :stop
    :zaptype :ray}
   {:name "wand of wishing"
    :price 500
    :max-charges 3
    :target false
    :autoid true
    :engrave :id
    :zaptype :nodir}])

(def wands-appearances
  (map (partial format "%s wand")
       ["glass" "balsa" "crystal" "maple" "pine" "oak" "ebony" "marble" "tin"
        "brass" "copper" "silver" "platinum" "iridium" "zinc" "aluminum"
        "uranium" "iron" "steel" "hexagonal" "short" "runed" "long" "curved"
        "forked" "spiked" "jeweled"]))

(def stone-gems
  [{:name "Heart of Ahriman"
    :artifact true
    :base "luckstone"
    :price 2500
    :weight 10
    :hardness :soft
    :fullname "The Heart of Ahriman"
    :appearances ["gray stone"]
    :material :mineral}
   {:name "luckstone"
    :price 60
    :weight 10
    :hardness :soft
    :appearances ["gray stone"]
    :plural "luckstones"
    :material :mineral}
   {:name "touchstone"
    :price 45
    :weight 10
    :hardness :soft
    :appearances ["gray stone"]
    :plural "touchstones"
    :material :mineral}
   {:name "flint stone"
    :price 1
    :weight 10
    :hardness :soft
    :appearances ["gray stone"]
    :plural "flint stones"
    :material :mineral}
   {:name "loadstone"
    :price 1
    :weight 500
    :hardness :soft
    :appearances ["gray stone"]
    :plural "loadstones"
    :material :mineral}])

(def gem-gems
  [{:name "dilithium crystal"
    :price 4500
    :hardness :soft
    :appearances ["white gem"]
    :plural "dilithium crystals"
    :material :gemstone}
   {:name "diamond"
    :price 4000
    :hardness :hard
    :appearances ["white gem"]
    :plural "diamonds"
    :material :gemstone}
   {:name "ruby"
    :price 3500
    :hardness :hard
    :appearances ["red gem"]
    :plural "rubies"
    :material :gemstone}
   {:name "jacinth stone"
    :price 3250
    :hardness :hard
    :appearances ["orange gem"]
    :plural "jacinth stones"
    :material :gemstone}
   {:name "sapphire"
    :price 3000
    :hardness :hard
    :appearances ["blue gem"]
    :plural "sapphires"
    :material :gemstone}
   {:name "black opal"
    :price 2500
    :hardness :hard
    :appearances ["black gem"]
    :plural "black opals"
    :material :gemstone}
   {:name "emerald"
    :price 2500
    :hardness :hard
    :appearances ["green gem"]
    :plural "emeralds"
    :material :gemstone}
   {:name "turquoise stone"
    :price 2000
    :hardness :soft
    :appearances ["green gem" "blue gem"]
    :plural "turquoise stones"
    :material :gemstone}
   {:name "aquamarine stone"
    :price 1500
    :hardness :hard
    :appearances ["green gem" "blue gem"]
    :plural "aquamarine stones"
    :material :gemstone}
   {:name "citrine stone"
    :price 1500
    :hardness :soft
    :appearances ["yellow gem"]
    :plural "citrine stones"
    :material :gemstone}
   {:name "amber stone"
    :price 1000
    :hardness :soft
    :appearances ["yellowish brown gem"]
    :plural "amber stones"
    :material :gemstone}
   {:name "topaz stone"
    :price 900
    :hardness :hard
    :appearances ["yellowish brown gem"]
    :plural "topaz stones"
    :material :gemstone}
   {:name "jet stone"
    :price 850
    :hardness :soft
    :appearances ["black gem"]
    :plural "jet stones"
    :material :gemstone}
   {:name "opal"
    :price 800
    :hardness :soft
    :appearances ["white gem"]
    :plural "opals"
    :material :gemstone}
   {:name "chrysoberyl stone"
    :price 700
    :hardness :soft
    :appearances ["yellow gem"]
    :plural "chrysoberyl stones"
    :material :gemstone}
   {:name "garnet stone"
    :price 700
    :hardness :soft
    :appearances ["red gem"]
    :plural "garnet stones"
    :material :gemstone}
   {:name "amethyst stone"
    :price 600
    :hardness :soft
    :appearances ["violet gem"]
    :plural "amethyst stones"
    :material :gemstone}
   {:name "jasper stone"
    :price 500
    :hardness :soft
    :appearances ["red gem"]
    :plural "jasper stones"
    :material :gemstone}
   {:name "fluorite stone"
    :price 400
    :hardness :soft
    :appearances ["green gem" "blue gem" "white gem" "violet gem"]
    :plural "fluorite stones"
    :material :gemstone}
   {:name "jade stone"
    :price 300
    :hardness :soft
    :appearances ["green gem"]
    :plural "jade stones"
    :material :gemstone}
   {:name "agate stone"
    :price 200
    :hardness :soft
    :appearances ["orange gem"]
    :plural "agate stones"
    :material :gemstone}
   {:name "obsidian stone"
    :price 200
    :hardness :soft
    :appearances ["black gem"]
    :plural "obsidian stones"
    :material :gemstone}
   {:name "worthless piece of black glass"
    :price 0
    :hardness :soft
    :appearances ["black gem"]
    :plural "worthless pieces of black glass"
    :material :glass}
   {:name "worthless piece of blue glass"
    :price 0
    :hardness :soft
    :appearances ["blue gem"]
    :plural "worthless pieces of blue glass"
    :material :glass}
   {:name "worthless piece of green glass"
    :price 0
    :hardness :soft
    :appearances ["green gem"]
    :plural "worthless pieces of green glass"
    :material :glass}
   {:name "worthless piece of orange glass"
    :price 0
    :hardness :soft
    :appearances ["orange gem"]
    :plural "worthless pieces of orange glass"
    :material :glass}
   {:name "worthless piece of red glass"
    :price 0
    :hardness :soft
    :appearances ["red gem"]
    :plural "worthless pieces of red glass"
    :material :glass}
   {:name "worthless piece of violet glass"
    :price 0
    :hardness :soft
    :appearances ["violet gem"]
    :plural "worthless pieces of violet glass"
    :material :glass}
   {:name "worthless piece of white glass"
    :price 0
    :hardness :soft
    :appearances ["white gem"]
    :plural "worthless pieces of white glass"
    :material :glass}
   {:name "worthless piece of yellow glass"
    :price 0
    :hardness :soft
    :appearances ["yellow gem"]
    :plural "worthless pieces of yellow glass"
    :material :glass}
   {:name "worthless piece of yellowish brown glass"
    :price 0
    :hardness :soft
    :appearances ["yellowish brown gem"]
    :plural "worthless pieces of yellowish brown glass"
    :material :glass}])

(def gem-data
  (concat
    stone-gems
    gem-gems
    [{:name "rock"
      :price 0
      :weight 10
      :hardness :soft
      :appearances ["rock"]
      :plural "rocks"
      :material :mineral}]))

(def cloaks ["tattered cape" "opera cloak" "ornamental cope" "piece of cloth"])
(def helmets ["plumed helmet" "etched helmet" "crested helmet" "visored helmet"])
(def gloves ["old gloves" "padded gloves" "riding gloves" "fencing gloves"])
(def boots ["combat boots" "jungle boots" "hiking boots" "mud boots" "buckled boots" "riding boots" "snow boots"])
(def armor-appearances (concat cloaks helmets gloves boots))
(def armor-data
  [{:name "Mitre of Holiness"
    :artifact true
    :base "helm of brilliance"
    :price 2000
    :weight 50
    :ac 1
    :mc 0
    :fullname "The Mitre of Holiness"
    :material :iron
    :safe true
    :subtype :helmet}
   {:name "Hawaiian shirt"
    :price 3
    :weight 5
    :ac 0
    :mc 0
    :material :cloth
    :safe true
    :subtype :shirt}
   {:name "T-shirt"
    :price 2
    :weight 5
    :ac 0
    :mc 0
    :material :cloth
    :safe true
    :subtype :shirt}
   {:name "leather jacket"
    :price 10
    :weight 30
    :ac 1
    :mc 0
    :material :leather
    :safe true
    :subtype :suit}
   {:name "leather armor"
    :price 5
    :weight 150
    :ac 2
    :mc 0
    :material :leather
    :safe true
    :subtype :suit}
   {:name "orcish ring mail"
    :price 80
    :weight 250
    :ac 2
    :material :iron
    :mc 1
    :appearances ["crude ring mail"]
    :safe true
    :subtype :suit}
   {:name "studded leather armor"
    :price 15
    :weight 200
    :ac 3
    :material :leather
    :mc 1
    :safe true
    :subtype :suit}
   {:name "ring mail"
    :price 100
    :weight 250
    :ac 3
    :mc 0
    :safe true
    :material :iron
    :subtype :suit}
   {:name "scale mail"
    :price 45
    :weight 250
    :ac 4
    :mc 0
    :material :iron
    :safe true
    :subtype :suit}
   {:name "orcish chain mail"
    :price 75
    :weight 300
    :ac 4
    :material :iron
    :safe true
    :mc 1
    :appearances ["crude chain mail"]
    :subtype :suit}
   {:name "chain mail"
    :price 75
    :weight 300
    :ac 5
    :material :iron
    :safe true
    :mc 1
    :subtype :suit}
   {:name "elven mithril-coat"
    :price 240
    :weight 150
    :ac 5
    :material :mithril
    :safe true
    :mc 3
    :subtype :suit}
   {:name "splint mail"
    :price 80
    :weight 400
    :ac 6
    :material :iron
    :safe true
    :mc 1
    :subtype :suit}
   {:name "banded mail"
    :price 90
    :weight 350
    :ac 6
    :mc 0
    :material :iron
    :safe true
    :subtype :suit}
   {:name "dwarvish mithril-coat"
    :price 240
    :weight 150
    :ac 6
    :material :mithril
    :safe true
    :mc 3
    :subtype :suit}
   {:name "bronze plate mail"
    :price 400
    :weight 450
    :ac 6
    :mc 0
    :material :copper
    :safe true
    :subtype :suit}
   {:name "plate mail"
    :price 600
    :weight 450
    :ac 7
    :material :iron
    :safe true
    :mc 2
    :subtype :suit}
   {:name "crystal plate mail"
    :price 820
    :weight 450
    :ac 7
    :material :glass
    :mc 2
    :safe true
    :subtype :suit}
   {:name "red dragon scales"
    :price 500
    :weight 40
    :ac 3
    :mc 0
    :material :dragon-hide
    :safe true
    :subtype :suit}
   {:name "white dragon scales"
    :price 500
    :weight 40
    :ac 3
    :mc 0
    :material :dragon-hide
    :safe true
    :subtype :suit}
   {:name "orange dragon scales"
    :price 500
    :weight 40
    :ac 3
    :mc 0
    :material :dragon-hide
    :safe true
    :subtype :suit}
   {:name "blue dragon scales"
    :price 500
    :weight 40
    :ac 3
    :mc 0
    :material :dragon-hide
    :safe true
    :subtype :suit}
   {:name "green dragon scales"
    :price 500
    :weight 40
    :ac 3
    :mc 0
    :material :dragon-hide
    :safe true
    :subtype :suit}
   {:name "yellow dragon scales"
    :price 500
    :weight 40
    :ac 3
    :mc 0
    :material :dragon-hide
    :safe true
    :subtype :suit}
   {:name "black dragon scales"
    :price 700
    :weight 40
    :ac 3
    :mc 0
    :material :dragon-hide
    :safe true
    :subtype :suit}
   {:name "silver dragon scales"
    :price 700
    :weight 40
    :ac 3
    :mc 0
    :material :dragon-hide
    :safe true
    :subtype :suit}
   {:name "gray dragon scales"
    :price 700
    :weight 40
    :ac 3
    :mc 0
    :safe true
    :material :dragon-hide
    :subtype :suit}
   {:name "red dragon scale mail"
    :price 900
    :weight 40
    :ac 9
    :mc 0
    :safe true
    :material :dragon-hide
    :subtype :suit}
   {:name "white dragon scale mail"
    :price 900
    :weight 40
    :ac 9
    :mc 0
    :safe true
    :material :dragon-hide
    :subtype :suit}
   {:name "orange dragon scale mail"
    :price 900
    :weight 40
    :ac 9
    :mc 0
    :safe true
    :material :dragon-hide
    :subtype :suit}
   {:name "blue dragon scale mail"
    :price 900
    :weight 40
    :ac 9
    :mc 0
    :safe true
    :material :dragon-hide
    :subtype :suit}
   {:name "green dragon scale mail"
    :price 900
    :weight 40
    :ac 9
    :mc 0
    :safe true
    :material :dragon-hide
    :subtype :suit}
   {:name "yellow dragon scale mail"
    :price 900
    :weight 40
    :ac 9
    :safe true
    :mc 0
    :material :dragon-hide
    :subtype :suit}
   {:name "black dragon scale mail"
    :price 1200
    :weight 40
    :ac 9
    :mc 0
    :safe true
    :material :dragon-hide
    :subtype :suit}
   {:name "silver dragon scale mail"
    :price 1200
    :weight 40
    :ac 9
    :mc 0
    :safe true
    :material :dragon-hide
    :subtype :suit}
   {:name "gray dragon scale mail"
    :price 1200
    :weight 40
    :ac 9
    :mc 0
    :safe true
    :material :dragon-hide
    :subtype :suit}
   {:name "mummy wrapping"
    :price 2
    :weight 3
    :ac 0
    :material :cloth
    :safe true
    :mc 1
    :subtype :cloak}
   {:name "orcish cloak"
    :price 40
    :weight 10
    :ac 0
    :safe true
    :material :cloth
    :mc 2
    :appearances ["coarse mantelet"]
    :subtype :cloak}
   {:name "dwarvish cloak"
    :price 50
    :weight 10
    :ac 0
    :material :cloth
    :mc 2
    :safe true
    :appearances ["hooded cloak"]
    :subtype :cloak}
   {:name "leather cloak"
    :price 40
    :weight 15
    :ac 1
    :material :leather
    :safe true
    :mc 1
    :subtype :cloak}
   {:name "oilskin cloak"
    :price 50
    :weight 10
    :ac 1
    :safe true
    :material :cloth
    :mc 3
    :appearances ["slippery cloak"]
    :subtype :cloak}
   {:name "alchemy smock"
    :price 50
    :weight 10
    :ac 1
    :safe true
    :material :cloth
    :mc 1
    :appearances ["apron"]
    :subtype :cloak}
   {:name "elven cloak"
    :price 60
    :weight 10
    :ac 1
    :material :cloth
    :safe true
    :mc 3
    :appearances ["faded pall"]
    :subtype :cloak}
   {:name "robe"
    :price 50
    :weight 15
    :ac 2
    :material :cloth
    :safe true
    :mc 3
    :subtype :cloak}
   {:name "cloak of displacement"
    :price 50
    :weight 10
    :ac 1
    :material :cloth
    :safe true
    :mc 2
    :appearances cloaks
    :subtype :cloak}
   {:name "cloak of invisibility"
    :price 60
    :weight 10
    :ac 1
    :material :cloth
    :safe true
    :mc 2
    :appearances cloaks
    :subtype :cloak}
   {:name "cloak of magic resistance"
    :price 60
    :weight 10
    :ac 1
    :material :cloth
    :safe true
    :mc 3
    :appearances cloaks
    :subtype :cloak}
   {:name "cloak of protection"
    :autoid true
    :price 50
    :weight 10
    :ac 3
    :material :cloth
    :safe true
    :mc 3
    :appearances cloaks
    :subtype :cloak}
   {:name "fedora"
    :price 1
    :weight 3
    :ac 0
    :mc 0
    :safe true
    :material :cloth
    :subtype :helmet}
   {:name "dunce cap"
    :price 1
    :weight 4
    :ac 0
    :mc 0
    :material :cloth
    :appearances ["conical hat"]
    :subtype :helmet}
   {:name "cornuthaum"
    :price 80
    :weight 4
    :ac 0
    :material :cloth
    :safe true
    :mc 2
    :appearances ["conical hat"]
    :subtype :helmet}
   {:name "dented pot"
    :price 8
    :weight 10
    :ac 1
    :mc 0
    :safe true
    :material :iron
    :subtype :helmet}
   {:name "elven leather helm"
    :price 8
    :weight 3
    :ac 1
    :mc 0
    :material :leather
    :safe true
    :appearances ["leather hat"]
    :subtype :helmet}
   {:name "orcish helm"
    :price 10
    :weight 30
    :ac 1
    :mc 0
    :material :iron
    :safe true
    :appearances ["iron skull cap"]
    :subtype :helmet}
   {:name "dwarvish iron helm"
    :price 20
    :weight 40
    :ac 2
    :mc 0
    :material :iron
    :safe true
    :appearances ["hard hat"]
    :subtype :helmet}
   {:name "helmet"
    :price 10
    :weight 30
    :ac 1
    :mc 0
    :safe true
    :material :iron
    :appearances helmets
    :subtype :helmet}
   {:name "helm of brilliance"
    :price 50
    :weight 50
    :ac 1
    :mc 0
    :material :iron
    :safe true
    :appearances helmets
    :subtype :helmet}
   {:name "helm of opposite alignment"
    :price 50
    :weight 50
    :ac 1
    :mc 0
    :material :iron
    :appearances helmets
    :subtype :helmet}
   {:name "helm of telepathy"
    :price 50
    :weight 50
    :ac 1
    :mc 0
    :safe true
    :material :iron
    :appearances helmets
    :subtype :helmet}
   {:name "leather gloves"
    :price 8
    :weight 10
    :ac 1
    :mc 0
    :material :leather
    :safe true
    :appearances gloves
    :subtype :gloves}
   {:name "gauntlets of dexterity"
    :price 50
    :weight 10
    :ac 1
    :mc 0
    :material :leather
    :safe true
    :appearances gloves
    :subtype :gloves}
   {:name "gauntlets of fumbling"
    :price 50
    :weight 10
    :ac 1
    :mc 0
    :material :leather
    :appearances gloves
    :subtype :gloves}
   {:name "gauntlets of power"
    :price 50
    :weight 30
    :ac 1
    :mc 0
    :material :iron
    :safe true
    :appearances gloves
    :subtype :gloves}
   {:name "small shield"
    :price 3
    :weight 30
    :ac 1
    :mc 0
    :safe true
    :material :wood
    :subtype :shield}
   {:name "orcish shield"
    :price 7
    :weight 50
    :ac 1
    :mc 0
    :material :iron
    :safe true
    :appearances ["red-eyed shield"]
    :subtype :shield}
   {:name "Uruk-hai shield"
    :price 7
    :weight 50
    :ac 1
    :mc 0
    :safe true
    :material :iron
    :appearances ["white-handed shield"]
    :subtype :shield}
   {:name "elven shield"
    :price 7
    :weight 40
    :ac 2
    :mc 0
    :material :wood
    :safe true
    :appearances ["blue and green shield"]
    :subtype :shield}
   {:name "dwarvish roundshield"
    :price 10
    :weight 100
    :ac 2
    :mc 0
    :material :iron
    :safe true
    :appearances ["large round shield"]
    :subtype :shield}
   {:name "large shield"
    :price 10
    :weight 100
    :ac 2
    :mc 0
    :safe true
    :material :iron
    :subtype :shield}
   {:name "shield of reflection"
    :price 50
    :weight 50
    :ac 2
    :mc 0
    :material :silver
    :safe true
    :appearances ["polished silver shield" "smooth shield"]
    :subtype :shield}
   {:name "low boots"
    :price 8
    :weight 10
    :ac 1
    :mc 0
    :material :leather
    :safe true
    :appearances ["walking shoes"]
    :subtype :boots}
   {:name "high boots"
    :price 12
    :weight 20
    :ac 2
    :mc 0
    :material :leather
    :safe true
    :appearances ["jackboots"]
    :subtype :boots}
   {:name "iron shoes"
    :price 16
    :weight 50
    :ac 2
    :mc 0
    :material :iron
    :safe true
    :appearances ["hard shoes"]
    :subtype :boots}
   {:name "elven boots"
    :price 8
    :weight 15
    :ac 1
    :mc 0
    :material :leather
    :safe true
    :appearances boots
    :subtype :boots}
   {:name "kicking boots"
    :price 8
    :weight 15
    :ac 1
    :mc 0
    :material :iron
    :safe true
    :appearances boots
    :subtype :boots}
   {:name "fumble boots"
    :price 30
    :weight 20
    :ac 1
    :mc 0
    :material :leather
    :appearances boots
    :subtype :boots}
   {:name "levitation boots"
    :price 30
    :weight 15
    :ac 1
    :mc 0
    :safe true
    :material :leather
    :appearances boots
    :subtype :boots}
   {:name "jumping boots"
    :price 50
    :weight 20
    :ac 1
    :mc 0
    :safe true
    :material :leather
    :appearances boots
    :subtype :boots}
   {:name "speed boots"
    :price 50
    :weight 20
    :ac 1
    :mc 0
    :safe true
    :material :leather
    :appearances boots
    :subtype :boots}
   {:name "water walking boots"
    :price 50
    :weight 20
    :ac 1
    :mc 0
    :safe true
    :material :leather
    :appearances boots
    :subtype :boots}])

(defn- vegetarian-corpse? [{:keys [name glyph] :as monster}]
  (and (#{\b \j \F \v \y \E \' \X \P} glyph)
       (not (#{"stalker" "leather golem" "flesh golem" "black pudding"} name))))

(defn- corpse [{:keys [tags] :as m} base]
  (let [corpse (str base " corpse")]
    {:name corpse
     :nutrition (:nutrition m)
     :plural (str corpse \s)
     :weight (:weight m)
     :stackable true
     :material :flesh
     :subtype :corpse
     :price 5
     :permanent (#{"lichen" "lizard"} base)
     :poisonous (:poisonous tags)
     :resistances (:resistances-conferred m)
     :monster m}))

(defn- tin [{:keys [tags] :as m} base]
  (let [tin (str "tin of " base (if-not (vegetarian-corpse? m) " meat"))]
    {:name tin
     :plural (string/replace tin #"^tin" "tins")
     :appearances ["tin"]
     :weight 10
     :stackable true
     :resistances (:resistances-conferred m)
     :material :metal
     :price 5
     :monster m}))

(defn- egg [{:keys [tags] :as m} base]
  (let [egg (str base " egg")]
    {:name egg
     :plural (str egg \s)
     :nutrition 80
     :appearances ["egg"]
     :weight 1
     :stackable 1
     :material :flesh
     :price 9
     :monster m}))

(def monster-foods "corpse, tin and egg items for monster types"
  (for [{:keys [tags gen-flags name] :as m} monster-types
        :when (not (:no-corpse gen-flags))
        :let [base (if (:unique gen-flags)
                     (str (if-not (:proper-name tags) "the ")
                          name
                          (if (re-seq #"s$" name) \' "'s"))
                     name)]
        food (as-> [] res
               (conj res (corpse m base))
               (conj res (tin m base))
               (if (:oviparous tags)
                 (conj res (egg m base))
                 res))]
    food))

(def figurines
  (for [{:keys [tags gen-flags name] :as m} monster-types
        :when (not (or (:unique gen-flags)
                       (:human tags)))]
    {:name (str "figurine of "
                (if (re-seq #"^[aeiouAEIOU]" name) "an " "a ")
                name)
     :price 80
     :charge 0
     :subtype :figurine
     :material :mineral
     :monster m}))

(def tool-data
  (concat
    figurines
    [{:name "Bell of Opening"
      :artifact true
      :price 5000
      :weight 10
      :charge 3
      :fullname "The Bell of Opening"
      :appearances ["silver bell"]
      :subtype :instrument
      :material :silver
      :tonal 0}
     {:name "Candelabrum of Invocation"
      :artifact true
      :price 5000
      :weight 10
      :charge 0
      :fullname "The Candelabrum of Invocation"
      :appearances ["candelabrum"]
      :subtype :candelabrum
      :material :gold}
     {:name "Eyes of the Overworld"
      :artifact true
      :base "lenses"
      :price 80
      :weight 3
      :charge 0
      :fullname "The Eyes of the Overworld"
      :subtype :accessory
      :material :glass}
     {:name "Magic Mirror of Merlin"
      :artifact true
      :base "mirror"
      :price 10
      :weight 13
      :charge 0
      :fullname "The Magic Mirror of Merlin"
      :material :glass}
     {:name "Master Key of Thievery"
      :artifact true
      :base "skeleton key"
      :price 10
      :weight 3
      :charge 0
      :fullname "The Master Key of Thievery"
      :subtype :key
      :material :iron}
     {:name "Orb of Detection"
      :artifact true
      :base "crystal ball"
      :price 60
      :weight 150
      :charge 5
      :fullname "The Orb of Detection"
      :material :glass}
     {:name "Orb of Fate"
      :artifact true
      :base "crystal ball"
      :price 60
      :weight 150
      :charge 5
      :fullname "The Orb of Fate"
      :material :glass}
     {:name "Platinum Yendorian Express Card"
      :artifact true
      :base "credit card"
      :price 10
      :weight 1
      :charge 0
      :fullname "The Platinum Yendorian Express Card"
      :subtype :key
      :material :plastic}
     {:name "large box"
      :price 8
      :weight 350
      :charge 0
      :subtype :container
      :material :wood}
     {:name "chest"
      :price 16
      :weight 600
      :charge 0
      :subtype :container
      :material :wood}
     {:name "ice box"
      :price 42
      :weight 900
      :charge 0
      :subtype :container
      :material :plastic}
     {:name "sack"
      :price 2
      :weight 15
      :charge 0
      :appearances ["bag"]
      :subtype :container
      :material :cloth}
     {:name "bag of holding"
      :price 100
      :weight 15
      :charge 0
      :appearances ["bag"]
      :subtype :container
      :material :cloth}
     {:name "bag of tricks"
      :price 100
      :weight 15
      :charge 20
      :subtype :container
      :appearances ["bag"]
      :material :cloth}
     {:name "oilskin sack"
      :price 100
      :weight 15
      :charge 0
      :appearances ["bag"]
      :subtype :container
      :material :cloth}
     {:name "credit card"
      :price 10
      :weight 1
      :charge 0
      :subtype :key
      :material :plastic}
     {:name "lock pick"
      :price 20
      :weight 4
      :charge 0
      :subtype :key
      :material :iron}
     {:name "skeleton key"
      :price 10
      :weight 3
      :charge 0
      :appearances ["key"]
      :subtype :key
      :material :iron}
     {:name "tallow candle"
      :price 10
      :weight 2
      :charge 0
      :appearances ["candle"]
      :plural "tallow candles"
      :subtype :light
      :stackable 1
      :material :wax}
     {:name "wax candle"
      :price 20
      :weight 2
      :charge 0
      :appearances ["candle"]
      :plural "wax candles"
      :subtype :light
      :stackable 1
      :material :wax}
     {:name "brass lantern"
      :price 12
      :weight 30
      :charge 1499
      :safe true
      :subtype :light
      :material :copper}
     {:name "oil lamp"
      :price 10
      :weight 20
      :charge 1499
      :safe true
      :appearances ["lamp"]
      :subtype :light
      :material :copper}
     {:name "magic lamp"
      :price 50
      :weight 20
      :charge 0
      :safe true
      :appearances ["lamp"]
      :subtype :light
      :material :copper}
     {:name "tin whistle"
      :price 10
      :weight 3
      :charge 0
      :safe true
      :appearances ["whistle"]
      :subtype :instrument
      :tonal 0
      :material :metal}
     {:name "magic whistle"
      :price 10
      :weight 3
      :charge 0
      :safe true
      :appearances ["whistle"]
      :subtype :instrument
      :tonal 0
      :material :metal}
     {:name "bugle"
      :price 15
      :weight 10
      :charge 0
      :subtype :instrument
      :tonal 1
      :material :copper}
     {:name "wooden flute"
      :price 12
      :weight 5
      :charge 0
      :appearances ["flute"]
      :subtype :instrument
      :tonal 1
      :material :wood}
     {:name "magic flute"
      :price 36
      :weight 5
      :charge 8
      :appearances ["flute"]
      :subtype :instrument
      :tonal 1
      :material :wood}
     {:name "tooled horn"
      :price 15
      :weight 18
      :charge 0
      :appearances ["horn"]
      :subtype :instrument
      :tonal 1
      :material :bone}
     {:name "frost horn"
      :price 50
      :weight 18
      :charge 8
      :appearances ["horn"]
      :subtype :instrument
      :tonal 1
      :material :bone}
     {:name "fire horn"
      :price 50
      :weight 18
      :charge 8
      :appearances ["horn"]
      :subtype :instrument
      :tonal 1
      :material :bone}
     {:name "horn of plenty"
      :price 50
      :weight 18
      :charge 20
      :appearances ["horn"]
      :subtype :instrument
      :tonal 0
      :material :bone}
     {:name "leather drum"
      :price 25
      :weight 25
      :charge 0
      :appearances ["drum"]
      :subtype :instrument
      :tonal 0
      :material :leather}
     {:name "drum of earthquake"
      :price 25
      :weight 25
      :charge 8
      :appearances ["drum"]
      :subtype :instrument
      :tonal 0
      :material :leather}
     {:name "wooden harp"
      :price 50
      :weight 30
      :charge 0
      :appearances ["harp"]
      :subtype :instrument
      :tonal 1
      :material :wood}
     {:name "magic harp"
      :price 50
      :weight 30
      :charge 8
      :appearances ["harp"]
      :subtype :instrument
      :tonal 1
      :material :wood}
     {:name "bell"
      :price 50
      :weight 30
      :charge 0
      :subtype :instrument
      :tonal 0
      :material :copper}
     {:name "beartrap"
      :price 60
      :weight 200
      :charge 0
      :subtype :trap
      :material :iron}
     {:name "land mine"
      :price 180
      :weight 300
      :charge 0
      :appearances ["land mine"]
      :subtype :trap
      :material :iron}
     {:name "pick-axe"
      :sdam "d6"
      :ldam "d3"
      :to-hit 0
      :hands 1
      :price 50
      :weight 100
      :charge nil
      :safe true
      :subtype :weapon
      :material :iron}
     {:name "grappling hook"
      :sdam "d2"
      :ldam "d6"
      :to-hit 0
      :hands 1
      :price 50
      :weight 30
      :charge nil
      :appearances ["iron hook"]
      :subtype :weapon
      :material :iron}
     {:name "unicorn horn"
      :sdam "d12"
      :ldam "d12"
      :to-hit 1
      :hands 2
      :price 100
      :weight 20
      :charge nil
      :weaptool 1
      :subtype :weapon
      :material :bone}
     {:name "expensive camera"
      :price 200
      :weight 12
      :charge 99
      :material :plastic}
     {:name "mirror"
      :price 10
      :weight 13
      :charge 0
      :appearances ["looking glass"]
      :material :glass}
     {:name "crystal ball"
      :price 60
      :weight 150
      :charge 5
      :appearances ["glass orb"]
      :material :glass}
     {:name "lenses"
      :price 80
      :weight 3
      :charge 0
      :safe true
      :subtype :accessory
      :material :glass}
     {:name "blindfold"
      :price 20
      :weight 2
      :charge 0
      :safe true
      :subtype :accessory
      :material :cloth}
     {:name "towel"
      :price 50
      :weight 2
      :charge 0
      :safe true
      :subtype :accessory
      :material :cloth}
     {:name "saddle"
      :price 150
      :weight 200
      :charge 0
      :material :leather}
     {:name "leash"
      :price 20
      :weight 12
      :charge 0
      :material :leather}
     {:name "stethoscope"
      :price 75
      :weight 4
      :charge 0
      :material :iron}
     {:name "tinning kit"
      :price 30
      :weight 100
      :charge 99
      :material :iron}
     {:name "tin opener"
      :price 30
      :weight 4
      :charge 0
      :material :iron}
     {:name "can of grease"
      :price 20
      :weight 15
      :charge 25
      :material :iron}
     {:name "magic marker"
      :price 50
      :weight 2
      :charge 99
      :material :plastic}]))

(def food-data
  (concat
    monster-foods
    [{:name "meatball"
      :price 5
      :weight 1
      :nutrition 5
      :time 1
      :plural "meatballs"
      :stackable 1
      :material :flesh
      :vegan 0
      :vegetarian 0}
     {:name "meat ring"
      :price 5
      :weight 1
      :nutrition 5
      :time 1
      :plural "meat rings"
      :stackable 0
      :material :flesh
      :vegan 0
      :vegetarian 0}
     {:name "meat stick"
      :price 5
      :weight 1
      :nutrition 5
      :time 1
      :plural "meat sticks"
      :stackable 1
      :material :flesh
      :vegan 0
      :vegetarian 0}
     {:name "tripe ration"
      :price 15
      :weight 10
      :nutrition 200
      :time 2
      :unsafe 1
      :plural "tripe rations"
      :stackable 1
      :material :flesh
      :vegan 0
      :vegetarian 0}
     {:name "huge chunk of meat"
      :price 105
      :weight 400
      :nutrition 2000
      :time 20
      :plural "huge chunks of meat"
      :stackable 1
      :material :flesh
      :vegan 0
      :vegetarian 0}
     {:name "kelp frond"
      :price 6
      :weight 1
      :nutrition 30
      :time 1
      :plural "kelp fronds"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "eucalyptus leaf"
      :price 6
      :weight 1
      :nutrition 30
      :time 1
      :plural "eucalyptus leaves"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "clove of garlic"
      :price 7
      :weight 1
      :nutrition 40
      :time 1
      :plural "cloves of garlic"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "sprig of wolfsbane"
      :price 7
      :weight 1
      :nutrition 40
      :time 1
      :plural "sprigs of wolfsbane"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "apple"
      :price 7
      :weight 2
      :nutrition 50
      :time 1
      :plural "apples"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "carrot"
      :price 7
      :weight 2
      :nutrition 50
      :time 1
      :plural "carrots"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "pear"
      :price 7
      :weight 2
      :nutrition 50
      :time 1
      :plural "pears"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "banana"
      :price 9
      :weight 2
      :nutrition 80
      :time 1
      :plural "bananas"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "orange"
      :price 9
      :weight 2
      :nutrition 80
      :time 1
      :plural "oranges"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "melon"
      :price 10
      :weight 5
      :nutrition 100
      :time 1
      :plural "melons"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "slime mold"
      :price 17
      :weight 5
      :nutrition 250
      :time 1
      :plural "slime molds"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "fortune cookie"
      :price 7
      :weight 1
      :nutrition 40
      :time 1
      :plural "fortune cookies"
      :stackable 1
      :material :veggy
      :vegan 0
      :vegetarian 1}
     {:name "candy bar"
      :price 10
      :weight 2
      :nutrition 100
      :time 1
      :plural "candy bars"
      :stackable 1
      :material :veggy
      :vegan 0
      :vegetarian 1}
     {:name "cream pie"
      :price 10
      :weight 10
      :nutrition 100
      :time 1
      :plural "cream pies"
      :stackable 1
      :material :veggy
      :vegan 0
      :vegetarian 1}
     {:name "lump of royal jelly"
      :price 15
      :weight 2
      :nutrition 200
      :time 1
      :plural "lumps of royal jelly"
      :stackable 1
      :material :veggy
      :vegan 0
      :vegetarian 1}
     {:name "pancake"
      :price 15
      :weight 2
      :nutrition 200
      :time 2
      :plural "pancakes"
      :stackable 1
      :material :veggy
      :vegan 0
      :vegetarian 1}
     {:name "C-ration"
      :price 20
      :weight 10
      :nutrition 300
      :time 1
      :plural "C-rations"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "K-ration"
      :price 25
      :weight 10
      :nutrition 400
      :time 1
      :plural "K-rations"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "cram ration"
      :price 35
      :weight 15
      :nutrition 600
      :time 3
      :plural "cram rations"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "food ration"
      :price 45
      :weight 20
      :nutrition 800
      :time 5
      :plural "food rations"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "lembas wafer"
      :price 45
      :weight 5
      :nutrition 800
      :time 2
      :plural "lembas wafers"
      :stackable 1
      :material :veggy
      :vegan 1
      :vegetarian 1}
     {:name "empty tin"
      :price 5
      :weight 10
      :nutrition 0
      :plural "empty tins"
      :stackable 1
      :material :metal
      :appearances ["tin"]
      :vegan 1
      :vegetarian 1}
     {:name "tin of spinach"
      :price 5
      :weight 10
      :nutrition 800
      :plural "tins of spinach"
      :stackable 1
      :material :metal
      :appearances ["tin"]
      :vegan 1
      :vegetarian 1}]))

(def others-data
  [{:name "boulder"
    :price 0
    :weight 6000
    :glyph \8
    :sdam "d20"
    :ldam "d20"
    :nutrition 2000
    :plural "boulders"
    :material :mineral}
   {:name "heavy iron ball"
    :price 10
    :weight 480
    :glyph \0
    :sdam "d25"
    :ldam "d25"
    :nutrition 480
    :plural "heavy iron balls"
    :material :iron}
   {:name "iron chain"
    :price 0
    :weight 120
    :glyph \_
    :sdam "d4+1"
    :ldam "d4+1"
    :nutrition 120
    :plural "iron chains"
    :material :iron}
   {:name "acid venom"
    :price 0
    :weight 1
    :glyph \.
    :sdam "2d6"
    :ldam "2d6"
    :plural "acid venoms"
    :stackable 1
    :material "liquid"}
   {:name "blinding venom"
    :price 0
    :weight 1
    :glyph \.
    :plural "blinding venoms"
    :stackable 1
    :material "liquid"}
   {:name "gold piece"
    :price 1
    :weight 0.01
    :glyph \$
    :plural "gold pieces"
    :stackable 1
    :material :gold}])

(def statue-data
  (for [{:keys [tags gen-flags name] :as m} monster-types]
    {:name (str "statue of "
                (if (and (:unique gen-flags) (not (:proper-name tags)))
                  "the ")
                (if-not (:unique gen-flags)
                  (if (re-seq #"^[aeiouAEIOU]" name) "an " "a "))
                name)
     :price 0
     :weight 900
     :glyph \`
     :sdam "d20"
     :ldam "d20"
     :nutrition 2500
     :material :mineral
     :monster m}))

(def scroll-data
  (map #(assoc % :plural (string/replace (:name %) #"^scroll" "scrolls"))
       [{:name "scroll of mail"
         :price 0
         :ink 2
         :appearances ["stamped scroll"]}
        {:name "scroll of blank paper"
         :price 60
         :ink 0
         :safe true
         :appearances ["unlabeled scroll"]}
        {:name "scroll of identify"
         :price 20
         :safe true
         :ink 14}
        {:name "scroll of light"
         :price 50
         :safe true
         :ink 8}
        {:name "scroll of enchant weapon"
         :safe true
         :price 60
         :ink 16}
        {:name "scroll of enchant armor"
         :safe true
         :price 80
         :ink 16}
        {:name "scroll of remove curse"
         :safe true
         :price 80
         :ink 16}
        {:name "scroll of confuse monster"
         :safe true
         :price 100
         :ink 12}
        {:name "scroll of destroy armor"
         :price 100
         :ink 10}
        {:name "scroll of fire"
         :price 100
         :ink 8}
        {:name "scroll of food detection"
         :price 100
         :safe true
         :ink 8}
        {:name "scroll of gold detection"
         :price 100
         :safe true
         :ink 8}
        {:name "scroll of magic mapping"
         :price 100
         :safe true
         :ink 8}
        {:name "scroll of scare monster"
         :price 100
         :safe true
         :ink 20}
        {:name "scroll of teleportation"
         :price 100
         :ink 20}
        {:name "scroll of amnesia"
         :price 200
         :ink 8}
        {:name "scroll of create monster"
         :price 200
         :ink 10}
        {:name "scroll of earth"
         :safe true
         :price 200
         :ink 8}
        {:name "scroll of taming"
         :safe true
         :price 200
         :ink 20}
        {:name "scroll of charging"
         :safe true
         :price 300
         :ink 16}
        {:name "scroll of genocide"
         :safe true
         :price 300
         :ink 30}
        {:name "scroll of punishment"
         :price 300
         :ink 10}
        {:name "scroll of stinking cloud"
         :price 300
         :ink 20}
        {:name "scroll of spare appearance" ; doesn't exist in-game, prevents itemid from pairing nonexistent appearances by elimination
         :price 0
         :safe true
         :ink 0}]))

(def ring-appearances
  (map (partial format "%s ring")
       ["wooden" "granite" "opal" "clay" "coral" "moonstone" "jade" "bronze"
        "agate" "topaz" "sapphire" "ruby" "diamond" "pearl" "iron" "brass"
        "copper" "twisted" "steel" "silver" "gold" "ivory" "emerald" "wire"
        "engagement" "shiny" "black onyx" "tiger eye"]))

(def ring-data
  [{:name "ring of adornment"
    :safe true
    :price 100
    :autoid true
    :chargeable true}
   {:name "ring of hunger"
    :price 100
    :chargeable false}
   {:name "ring of protection"
    :safe true
    :price 100
    :autoid true
    :chargeable true}
   {:name "ring of protection from shape changers"
    :safe true
    :price 100
    :chargeable false}
   {:name "ring of stealth"
    :safe true
    :price 100
    :chargeable false}
   {:name "ring of sustain ability"
    :safe true
    :price 100
    :chargeable false}
   {:name "ring of warning"
    :price 100
    :safe true
    :chargeable false}
   {:name "ring of aggravate monster"
    :price 150
    :chargeable false}
   {:name "ring of cold resistance"
    :price 150
    :safe true
    :chargeable false}
   {:name "ring of gain constitution"
    :price 150
    :autoid true
    :safe true
    :chargeable true}
   {:name "ring of gain strength"
    :safe true
    :autoid true
    :price 150
    :chargeable true}
   {:name "ring of increase accuracy"
    :price 150
    :safe true
    :chargeable true}
   {:name "ring of increase damage"
    :safe true
    :price 150
    :chargeable true}
   {:name "ring of invisibility"
    :safe true
    :price 150
    :chargeable false}
   {:name "ring of poison resistance"
    :safe true
    :price 150
    :chargeable false}
   {:name "ring of see invisible"
    :safe true
    :price 150
    :chargeable false}
   {:name "ring of shock resistance"
    :safe true
    :price 150
    :chargeable false}
   {:name "ring of fire resistance"
    :price 200
    :safe true
    :chargeable false}
   {:name "ring of free action"
    :price 200
    :safe true
    :chargeable false}
   {:name "ring of levitation"
    :price 200
    :safe true
    :autoid true
    :chargeable false}
   {:name "ring of regeneration"
    :price 200
    :safe true
    :chargeable false}
   {:name "ring of searching"
    :price 200
    :safe true
    :chargeable false}
   {:name "ring of slow digestion"
    :price 200
    :safe true
    :chargeable false}
   {:name "ring of teleportation"
    :price 200
    :chargeable false}
   {:name "ring of conflict"
    :price 300
    :chargeable false}
   {:name "ring of polymorph"
    :price 300
    :chargeable false}
   {:name "ring of polymorph control"
    :price 300
    :safe true
    :chargeable false}
   {:name "ring of teleport control"
    :safe true
    :price 300
    :chargeable false}])

(def potion-data
  (map #(assoc % :plural (string/replace (:name %) #"^potion" "potions"))
       [{:name "potion of booze"
         :autoid true
         :food true
         :price 50}
        {:name "potion of fruit juice"
         :safe true
         :food true
         :price 50}
        {:name "potion of see invisible"
         :safe true
         :price 50}
        {:name "potion of sickness"
         :attack true
         :price 50}
        {:name "potion of confusion"
         :attack true
         :autoid true
         :price 100}
        {:name "potion of extra healing"
         :safe true
         :autoid true
         :price 100}
        {:name "potion of hallucination"
         :attack true
         :autoid true
         :price 100}
        {:name "potion of healing"
         :safe true
         :autoid true
         :price 100}
        {:name "potion of restore ability"
         :safe true
         :price 100}
        {:name "potion of sleeping"
         :attack true
         :autoid true
         :price 100}
        {:name "potion of water"
         :safe true
         :food true
         :price 100
         :appearances ["clear potion"]}
        {:name "potion of blindness"
         :attack true
         :autoid true
         :price 150}
        {:name "potion of gain energy"
         :safe true
         :autoid true
         :price 150}
        {:name "potion of invisibility"
         :autoid true
         :price 150}
        {:name "potion of monster detection"
         :safe true
         :price 150}
        {:name "potion of object detection"
         :safe true
         :price 150}
        {:name "potion of enlightenment"
         :safe true
         :autoid true
         :price 200}
        {:name "potion of full healing"
         :safe true
         :autoid true
         :price 200}
        {:name "potion of levitation"
         :autoid true
         :price 200}
        {:name "potion of polymorph"
         :autoid true
         :price 200}
        {:name "potion of speed"
         :safe true
         :price 200}
        {:name "potion of acid"
         :autoid true
         :price 250}
        {:name "potion of oil"
         :safe true
         :autoid true
         :price 250}
        {:name "potion of gain ability"
         :safe true
         :autoid true
         :price 300}
        {:name "potion of gain level"
         :safe true
         :autoid true
         :price 300}
        {:name "potion of paralysis"
         :autoid true
         :price 300}]))

(def exclusive-appearances
  "appearances that are sticky to the given identity - rings or armor have exclusive appearances, gems, lamps or luck/flint/loadstones don't (gray stone)"
  (set (concat ring-appearances
               armor-appearances
               amulet-appearances
               scroll-appearances
               potion-appearances
               spellbook-appearances
               wands-appearances)))
