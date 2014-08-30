#!/bin/sed -f

# turns TAEB's item data into somewhat more Clojure-friendly data
# thanks TAEB!

/=> {/ !s/, \([a-z][a-z]* [^=]*=> \)/         :\1/g
s/   \([a-zA-Z0-9][a-zA-Z0-9]*[^'] *=>\)/:\1/g
s/=> ''/=> nil/g
s/'/"/g
s/=> //g
s/,//g
s/ }/}/g
s/\(".*"\) *{/{:name \1/
s/\(:[a-zA-Z0-9][a-zA-Z0-9]*\)  */\1 /
/:glyph "[08]"/ !s/"\([0-9][0-9]*\)"/\1/
s/:glyph "\(.\)"/:glyph \\\1/
s/}};$/}};/
s/};}$/};}/
s/"accessory"/:accessory/
/:appearanc/!s/"key"/:key/
s/"wood"/:wood/
s/"container"/:container/
s/"light"/:light/
s/"metal"/:metal/
/:appearance/!s/"horn"/:horn/
s/"weapon"/:weapon/
s/"leather"/:leather/
s/"figurine"/:figurine/
s/"instrument"/:instrument/
s/"gold"/:gold/
s/"soft"/:soft/
s/"silver"/:silver/
s/"copper"/:copper/
s/"hard"/:hard/
s/"iron"/:iron/
s/"glass"/:glass/
s/"boots"/:boots/
s/"shield"/:shield/
s/"gloves"/:gloves/
s/"helmet"/:helmet/
s/"cloak"/:cloak/
s/"bodyarmor"/:body-armor/
s/"veggy"/:veggy/
s/"flesh"/:flesh/
s/"dragon hide"/:dragon-hide/
s/"mineral"/:mineral/
s/"cloth"/:cloth/
s/"plastic"/:plastic/
s/"wax"/:wax/
s/"gemstone"/:gemstone/
s/"beam"/:beam/
s/"nodir"/:nodir/
s/"ray"/:ray/
s/"mithril"/:mithril/
s/"bone"/:bone/
s/"matter"/:matter/
s/"paper"/:paper/
s/"escape"/:escape/
s/"clerical"/:clerical/
s/"enchantment"/:enchantment/
s/"divination"/:divination/
s/"healing"/:healing/
s/"attack"/:attack/
s/"distant"/:distant/
s/"Pri"/:priest/
s/"Kni"/:knight/
s/"Sam"/:samurai/
s/"Wiz"/:wizard/
s/"Arc"/:archeologist/
s/"Bar"/:barbarian/
s/"Cav"/:caveman/
s/"Hea"/:healer/
s/"Mon"/:monk/
s/"Rog"/:rogue/
s/"Ran"/:ranger/
s/"Tou"/:tourist/
s/"Val"/:valkyrie/
s/:tohit\>/:to-hit/
/:appearances / s/\([a-zA-Z]\) \([a-zA-Z]\)/\1" "\2/g
/:appearances / s/qw\//"/
/:appearances / s/\//"/
s/:appearances \\@/:appearances /
s/:appearance "\([^"]*\)"/:appearances ["\1"]/
s/:maxcharges\>/:max-charges/
s/:chargeable 0/:chargeable false/
s/:chargeable 1/:chargeable true/
s/:vegan 0/:vegan false/
s/:vegan 1/:vegan true/
s/:vegetarian 0/:vegetarian false/
s/:vegetarian 1/:vegetarian true/
s/:stackable 1/:stackable true/
s/:stackable 0/:stackable false/
s/:emergency 1/:emergency true/
s/:emergency 0/:emergency false/
s/:artifact 1/:artifact true/
s/:artifact 0/:artifact false/
/^[ \t]*$/d
