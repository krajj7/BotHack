#!/bin/sed -f

# turns saiph's Monster.cpp data into Clojure-friendly data
# thanks saiph!

s/new Monster(/(MonsterType. /
s/^(\(.*\));$/\1/
s/Attack(/(MonsterAttack. /g
s/(MonsterAttack\./[(MonsterAttack./
s/\(^.*MonsterAttack\. [^ ]* [^ ]* [^ ]* [^)]*\)), \([0-9]*\),/\1)], \2,/
s/\(^.*MonsterAttack\. \)), \([0-9]*\),/\1)], \2,/
s/\[\(([^)]*)\), \(([^)]*)\), \(([^)]*)\), \(([^)]*)\), \(([^)]*)\), \(([^)]*)\)\]/[\1 \2 \3 \4 \5 \6]/g
s/ \?(MonsterAttack. )//g
s/(MonsterAttack\. \([^,]*\), \([^,]*\), \([^,]*\), \([^,]*\))/(MonsterAttack. \1 \2 \3 \4)/g
s/(MonsterType\. \([^,]*\), \([^,]*\), \([^,]*\), \([^,]*\), \([^,]*\), \([^,]*\), \([^,]*\), (\([^,]*\)), /(MonsterType. \1, \2, \3, \4, \5, \6, \7, \8, /
s/(MonsterType\. \([^,]*\), \([^,]*\), \([^,]*\), \([^,]*\), \([^,]*\), \([^,]*\), \([^,]*\), \([^,]*\), /(MonsterType. \1, \2, \3, \4, \5, \6, \7, #{\8}, /
s/\(#{[^)]*\) | [0-5]}/\1}/
s/#{[0-5]}/#{}/
s/, \([^,]*\), \([^,]*\), \([^,]*\), \([^,)]*\))$/, #{\1 \2 \3}, \4)/
s/ | / /g
# rm extension
s/\(.*\], [0-9][0-9]*, [0-9][0-9]*\), [0-9][0-9]*/\1/
s/ 0, \[/ #{}, [/
s/\(\], [^,]*, [^,]*, [^,]*, [^,]*,\) \([^,]*\), \([^,]*\)/\1 #{\2}, #{\3}/
s/\(^[^,]*, [^,]*,\)\(.*\), \([^)]*\))/\1 \3,\2)/
s/{0}/{}/g
s/\(#{[^}]*\) 0}/\1}/
s/\(#{[^}]*\) 0}/\1}/
s/^/  /
s/\<S_ANT\>/\\a/g
s/\<S_BLOB\>/\\b/g
s/\<S_COCKATRICE\>/\\c/g
s/\<S_DOG\>/\\d/g
s/\<S_EYE\>/\\e/g
s/\<S_FELINE\>/\\f/g
s/\<S_GREMLIN\>/\\g/g
s/\<S_HUMANOID\>/\\h/g
s/\<S_IMP\>/\\i/g
s/\<S_JELLY\>/\\j/g
s/\<S_KOBOLD\>/\\k/g
s/\<S_LEPRECHAUN\>/\\l/g
s/\<S_MIMIC\>/\\m/g
s/\<S_NYMPH\>/\\n/g
s/\<S_ORC\>/\\o/g
s/\<S_PIERCER\>/\\p/g
s/\<S_QUADRUPED\>/\\q/g
s/\<S_RODENT\>/\\r/g
s/\<S_SPIDER\>/\\s/g
s/\<S_TRAPPER\>/\\t/g
s/\<S_UNICORN\>/\\u/g
s/\<S_VORTEX\>/\\v/g
s/\<S_WORM\>/\\w/g
s/\<S_XAN\>/\\x/g
s/\<S_LIGHT\>/\\y/g
s/\<S_ZRUTY\>/\\z/g
s/\<S_ANGEL\>/\\A/g
s/\<S_BAT\>/\\B/g
s/\<S_CENTAUR\>/\\C/g
s/\<S_DRAGON\>/\\D/g
s/\<S_ELEMENTAL\>/\\E/g
s/\<S_FUNGUS\>/\\F/g
s/\<S_GNOME\>/\\G/g
s/\<S_GIANT\>/\\H/g
s/\<S_JABBERWOCK\>/\\J/g
s/\<S_KOP\>/\\K/g
s/\<S_LICH\>/\\L/g
s/\<S_MUMMY\>/\\M/g
s/\<S_NAGA\>/\\N/g
s/\<S_OGRE\>/\\O/g
s/\<S_PUDDING\>/\\P/g
s/\<S_QUANTMECH\>/\\Q/g
s/\<S_RUSTMONST\>/\\R/g
s/\<S_SNAKE\>/\\S/g
s/\<S_TROLL\>/\\T/g
s/\<S_UMBER\>/\\U/g
s/\<S_VAMPIRE\>/\\V/g
s/\<S_WRAITH\>/\\W/g
s/\<S_XORN\>/\\X/g
s/\<S_YETI\>/\\Y/g
s/\<S_ZOMBIE\>/\\Z/g
s/\<S_GHOST\>/\\X/g
s/\<S_EEL\>/\\;/g
s/\<S_LIZARD\>/\\:/g
s/\<S_INVISIBLE\>/\\I/g
s/\<S_HUMAN\>/\\@/g
s/\<S_DEMON\>/\\\&/g
s/\<S_GOLEM\>/\\'/g
s/\<S_WORM_TAIL\>/\\~/g
s/\<MS_SILENT\>/:silent/g
s/\<MS_BARK\>/:bark/g
s/\<MS_MEW\>/:mew/g
s/\<MS_ROAR\>/:roar/g
s/\<MS_GROWL\>/:growl/g
s/\<MS_SQEEK\>/:sqeek/g
s/\<MS_SQAWK\>/:sqawk/g
s/\<MS_HISS\>/:hiss/g
s/\<MS_BUZZ\>/:buzz/g
s/\<MS_GRUNT\>/:grunt/g
s/\<MS_NEIGH\>/:neigh/g
s/\<MS_WAIL\>/:wail/g
s/\<MS_GURGLE\>/:gurgle/g
s/\<MS_BURBLE\>/:burble/g
s/\<MS_ANIMAL\>/:animal/g
s/\<MS_SHRIEK\>/:shriek/g
s/\<MS_BONES\>/:bones/g
s/\<MS_LAUGH\>/:laugh/g
s/\<MS_MUMBLE\>/:mumble/g
s/\<MS_IMITATE\>/:imitate/g
s/\<MS_HUMANOID\>/:humanoid/g
s/\<MS_ARREST\>/:arrest/g
s/\<MS_SOLDIER\>/:soldier/g
s/\<MS_GUARD\>/:guard/g
s/\<MS_DJINNI\>/:djinni/g
s/\<MS_NURSE\>/:nurse/g
s/\<MS_SEDUCE\>/:seduce/g
s/\<MS_VAMPIRE\>/:vampire/g
s/\<MS_BRIBE\>/:bribe/g
s/\<MS_CUSS\>/:cuss/g
s/\<MS_RIDER\>/:rider/g
s/\<MS_LEADER\>/:leader/g
s/\<MS_NEMESIS\>/:nemesis/g
s/\<MS_GUARDIAN\>/:guardian/g
s/\<MS_SELL\>/:sell/g
s/\<MS_ORACLE\>/:oracle/g
s/\<MS_PRIEST\>/:priest/g
s/\<MS_SPELL\>/:spell/g
s/\<MS_WERE\>/:were/g
s/\<MS_BOAST\>/:boast/g
s/\<MR_FIRE\>/:fire/g
s/\<MR_COLD\>/:cold/g
s/\<MR_SLEEP\>/:sleep/g
s/\<MR_POISON\>/:poison/g
s/\<MR_ACID\>/:acid/g
s/\<MR_STONE\>/:stone/g
s/\<M1_FLY\>/:fly/g
s/\<M1_SWIM\>/:swim/g
s/\<M1_AMORPHOUS\>/:amorphous/g
s/\<M1_CLING\>/:cling/g
s/\<M1_TUNNEL\>/:tunnel/g
s/\<M1_CONCEAL\>/:conceal/g
s/\<M1_HIDE\>/:hide/g
s/\<M1_AMPHIBIOUS\>/:amphibious/g
s/\<M1_BREATHLESS\>/:breathless/g
s/\<M1_NOTAKE\>/:notake/g
s/\<M1_NOEYES\>/:noeyes/g
s/\<M1_NOHANDS\>/:nohands/g
s/\<M1_NOLIMBS\>/:nolimbs/g
s/\<M1_NOHEAD\>/:nohead/g
s/\<M1_MINDLESS\>/:mindless/g
s/\<M1_HUMANOID\>/:humanoid/g
s/\<M1_ANIMAL\>/:animal/g
s/\<M1_SLITHY\>/:slithy/g
s/\<M1_UNSOLID\>/:unsolid/g
s/\<M1_THICK_HIDE\>/:thick_hide/g
s/\<M1_OVIPAROUS\>/:oviparous/g
s/\<M1_REGEN\>/:regen/g
s/\<M1_SEE_INVIS\>/:see_invis/g
s/\<M1_ACID\>/:acid/g
s/\<M1_POIS\>/:poisonous/g
s/\<M1_CARNIVORE\>/:carnivore/g
s/\<M1_HERBIVORE\>/:herbivore/g
s/\<M1_OMNIVORE\>/:omnivore/g
s/\<M1_METALLIVORE\>/:metallivore/g
s/\<M2_NOPOLY\>/:nopoly/g
s/\<M2_UNDEAD\>/:undead/g
s/\<M2_WERE\>/:were/g
s/\<M2_HUMAN\>/:human/g
s/\<M2_ELF\>/:elf/g
s/\<M2_DWARF\>/:dwarf/g
s/\<M2_GNOME\>/:gnome/g
s/\<M2_ORC\>/:orc/g
s/\<M2_DEMON\>/:demon/g
s/\<M2_LORD\>/:lord/g
s/\<M2_PRINCE\>/:prince/g
s/\<M2_MINION\>/:minion/g
s/\<M2_GIANT\>/:giant/g
s/\<M2_MALE\>/:male/g
s/\<M2_FEMALE\>/:female/g
s/\<M2_NEUTER\>/:neuter/g
s/\<M2_HOSTILE\>/:hostile/g
s/\<M2_PEACEFUL\>/:peaceful/g
s/\<M2_DOMESTIC\>/:domestic/g
s/\<M2_WANDER\>/:wander/g
s/\<M2_NASTY\>/:nasty/g
s/\<M2_STRONG\>/:strong/g
s/\<M2_COLLECT\>/:collect/g
s/\<M1_WALLWALK\>/:phase/g
s/\<M1_NEEDPICK\>/:digger/g
s/\<MR_DISINT\>/:disintegration/g
s/\<MR_ELEC\>/:shock/g
s/\<MR_NO_ELBERETH\>/:no-elbereth/g
s/\<MS_ORC\>/:grunt/g
s/\<M2_GREEDY\>/:picks-gold/g
s/\<M2_JEWELS\>/:picks-jewels/g
s/\<M2_MAGIC\>/:picks-magic-items/g
s/\<M2_ROCKTHROW\>/:throws-boulders/g
s/\<M2_PNAME\>/:proper-name/g
s/\<M1_TPORT_CNTRL\>/:telecontrol/g
s/\<M1_TPORT\>/:teleport/g
s/\<M2_STALK\>/:follows/g
s/\<M2_MERC\>/:mercenary/g
s/\<M3_WANTSAMUL\>/:wants-amulet/g
s/\<M3_WANTSBELL\>/:wants-bell/g
s/\<M3_WANTSBOOK\>/:wants-book/g
s/\<M3_WANTSCAND\>/:wants-candelabrum/g
s/\<M3_WANTSARTI\>/:wants-arti/g
s/\<M3_WANTSALL\>/:wants-all/g
s/\<M3_WAITFORU\>/:waits/g
s/\<M3_CLOSE\>/:approach/g
s/\<M3_COVETOUS\>/:covetous/g
s/\<M3_INFRAVISION\>/:infravision/g
s/\<M3_INFRAVISIBLE\>/:infravisible/g
s/\<MZ_TINY\>/:tiny/g
s/\<MZ_SMALL\>/:small/g
s/\<MZ_MEDIUM\>/:medium/g
s/\<MZ_HUMAN\>/:human/g
s/\<MZ_LARGE\>/:large/g
s/\<MZ_HUGE\>/:huge/g
s/\<MZ_GIGANTIC\>/:gigantic/g
s/\<G_UNIQ\>/:unique/g
s/\<G_NOHELL\>/:no-hell/g
s/\<G_HELL\>/:hell-only/g
s/\<G_NOGEN\>/:not-generated/g
s/\<G_SGROUP\>/:sgroup/g
s/\<G_LGROUP\>/:lgroup/g
s/\<G_GENO\>/:genocidable/g
s/\<G_NOCORPSE\>/:no-corpse/g
s/\<NO_COLOR\>/nil/g
s/\<BOLD\>/:bold/g
s/\<BLACK\>/:blue/g
s/\<RED\>/:red/g
s/\<GREEN\>/:green/g
s/\<YELLOW\>/:brown/g
s/\<BLUE\>/:blue/g
s/\<MAGENTA\>/:magenta/g
s/\<CYAN\>/:cyan/g
s/\<WHITE\>/nil/g
s/\<BOLD_RED\>/:orange/g
s/\<BOLD_GREEN\>/:bright-green/g
s/\<BOLD_YELLOW\>/:yellow/g
s/\<BOLD_BLUE\>/:bright-blue/g
s/\<BOLD_MAGENTA\>/:bright-magenta/g
s/\<BOLD_CYAN\>/:bright-cyan/g
s/\<BOLD_WHITE\>/:white/g
s/\<AT_NONE\>/:passive/g
s/\<AT_CLAW\>/:claw/g
s/\<AT_BITE\>/:bite/g
s/\<AT_KICK\>/:kick/g
s/\<AT_BUTT\>/:butt/g
s/\<AT_TUCH\>/:touch/g
s/\<AT_STNG\>/:sting/g
s/\<AT_HUGS\>/:hug/g
s/\<AT_SPIT\>/:spit/g
s/\<AT_ENGL\>/:engulf/g
s/\<AT_BREA\>/:breath/g
s/\<AT_EXPL\>/:explode/g
s/\<AT_BOOM\>/:boom/g
s/\<AT_GAZE\>/:gaze/g
s/\<AT_TENT\>/:tentacle/g
s/\<AT_WEAP\>/:weapon/g
s/\<AT_MAGC\>/:magic/g
s/\<AD_PHYS\>/:physical/g
s/\<AD_MAGM\>/:magic-missile/g
s/\<AD_FIRE\>/:fire/g
s/\<AD_COLD\>/:cold/g
s/\<AD_SLEE\>/:sleep/g
s/\<AD_DISN\>/:disintegration/g
s/\<AD_ELEC\>/:shock/g
s/\<AD_DRST\>/:poison/g
s/\<AD_ACID\>/:acid/g
s/\<AD_BLND\>/:blinding/g
s/\<AD_STUN\>/:stun/g
s/\<AD_SLOW\>/:slow/g
s/\<AD_PLYS\>/:paralysis/g
s/\<AD_DRLI\>/:drain-xp/g
s/\<AD_DREN\>/:drain-magic/g
s/\<AD_LEGS\>/:leg-hurt/g
s/\<AD_STON\>/:stone/g
s/\<AD_STCK\>/:stick/g
s/\<AD_SGLD\>/:steal-gold/g
s/\<AD_SITM\>/:steal-items/g
s/\<AD_SEDU\>/:seduce/g
s/\<AD_TLPT\>/:teleport/g
s/\<AD_RUST\>/:rust/g
s/\<AD_CONF\>/:conf/g
s/\<AD_DGST\>/:digest/g
s/\<AD_HEAL\>/:heal/g
s/\<AD_WRAP\>/:wrap/g
s/\<AD_WERE\>/:lycantrophy/g
s/\<AD_DRDX\>/:drain-dex/g
s/\<AD_DRCO\>/:drain-con/g
s/\<AD_DRIN\>/:drain-int/g
s/\<AD_DISE\>/:disease/g
s/\<AD_DCAY\>/:rot/g
s/\<AD_SSEX\>/:sex/g
s/\<AD_HALU\>/:hallu/g
s/\<AD_DETH\>/:death/g
s/\<AD_PEST\>/:pestilence/g
s/\<AD_FAMN\>/:famine/g
s/\<AD_SLIM\>/:slime/g
s/\<AD_ENCH\>/:disenchant/g
s/\<AD_CORR\>/:corrode/g
s/\<AD_CLRC\>/:clerical/g
s/\<AD_SPEL\>/:spell/g
s/\<AD_RBRE\>/:breath/g
s/\<AD_SAMU\>/:steal-amulet/g
s/\<AD_CURS\>/:curse/g
s/\<A_NONE\>/-128/g
s/{0L :nopoly}/{:nopoly}/g
s/_/-/g

# S_ANT \a
# S_BLOB \b
# S_COCKATRICE \c
# S_DOG \d
# S_EYE \e
# S_FELINE \f
# S_GREMLIN \g
# S_HUMANOID \h
# S_IMP \i
# S_JELLY \j
# S_KOBOLD \k
# S_LEPRECHAUN \l
# S_MIMIC \m
# S_NYMPH \n
# S_ORC \o
# S_PIERCER \p
# S_QUADRUPED \q
# S_RODENT \r
# S_SPIDER \s
# S_TRAPPER \t
# S_UNICORN \u
# S_VORTEX \v
# S_WORM \w
# S_XAN \x
# S_LIGHT \y
# S_ZRUTY \z
# S_ANGEL \A
# S_BAT \B
# S_CENTAUR \C
# S_DRAGON \D
# S_ELEMENTAL \E
# S_FUNGUS \F
# S_GNOME \G
# S_GIANT \H
# S_JABBERWOCK \J
# S_KOP \K
# S_LICH \L
# S_MUMMY \M
# S_NAGA \N
# S_OGRE \O
# S_PUDDING \P
# S_QUANTMECH \Q
# S_RUSTMONST \R
# S_SNAKE \S
# S_TROLL \T
# S_UMBER \U
# S_VAMPIRE \V
# S_WRAITH \W
# S_XORN \X
# S_YETI \Y
# S_ZOMBIE \Z
# S_GHOST \X
# S_EEL \;
# S_LIZARD \:
# S_INVISIBLE \I
# S_HUMAN \@
# S_DEMON \&
# S_GOLEM \'
# S_WORM_TAIL \~
# MS_SILENT :silent 
# MS_BARK :bark 
# MS_MEW :mew 
# MS_ROAR :roar 
# MS_GROWL :growl 
# MS_SQEEK :sqeek 
# MS_SQAWK :sqawk 
# MS_HISS :hiss 
# MS_BUZZ :buzz 
# MS_GRUNT :grunt 
# MS_NEIGH :neigh 
# MS_WAIL :wail 
# MS_GURGLE :gurgle 
# MS_BURBLE :burble 
# MS_ANIMAL :animal 
# MS_SHRIEK :shriek 
# MS_BONES :bones 
# MS_LAUGH :laugh 
# MS_MUMBLE :mumble 
# MS_IMITATE :imitate 
# MS_HUMANOID :humanoid 
# MS_ARREST :arrest 
# MS_SOLDIER :soldier 
# MS_GUARD :guard 
# MS_DJINNI :djinni 
# MS_NURSE :nurse 
# MS_SEDUCE :seduce 
# MS_VAMPIRE :vampire 
# MS_BRIBE :bribe 
# MS_CUSS :cuss 
# MS_RIDER :rider 
# MS_LEADER :leader 
# MS_NEMESIS :nemesis 
# MS_GUARDIAN :guardian 
# MS_SELL :sell 
# MS_ORACLE :oracle 
# MS_PRIEST :priest 
# MS_SPELL :spell 
# MS_WERE :were 
# MS_BOAST :boast 
# MR_FIRE :fire 
# MR_COLD :cold 
# MR_SLEEP :sleep 
# MR_POISON :poison 
# MR_ACID :acid 
# MR_STONE :stone 
# M1_FLY :fly 
# M1_SWIM :swim 
# M1_AMORPHOUS :amorphous 
# M1_CLING :cling 
# M1_TUNNEL :tunnel 
# M1_CONCEAL :conceal 
# M1_HIDE :hide 
# M1_AMPHIBIOUS :amphibious 
# M1_BREATHLESS :breathless 
# M1_NOTAKE :notake 
# M1_NOEYES :noeyes 
# M1_NOHANDS :nohands 
# M1_NOLIMBS :nolimbs 
# M1_NOHEAD :nohead 
# M1_MINDLESS :mindless 
# M1_HUMANOID :humanoid 
# M1_ANIMAL :animal 
# M1_SLITHY :slithy 
# M1_UNSOLID :unsolid 
# M1_THICK_HIDE :thick_hide 
# M1_OVIPAROUS :oviparous 
# M1_REGEN :regen 
# M1_SEE_INVIS :see_invis 
# M1_ACID :acid 
# M1_POIS :poisonous
# M1_CARNIVORE :carnivore 
# M1_HERBIVORE :herbivore 
# M1_OMNIVORE :omnivore 
# M1_METALLIVORE :metallivore 
# M2_NOPOLY :nopoly 
# M2_UNDEAD :undead 
# M2_WERE :were 
# M2_HUMAN :human 
# M2_ELF :elf 
# M2_DWARF :dwarf 
# M2_GNOME :gnome 
# M2_ORC :orc 
# M2_DEMON :demon 
# M2_LORD :lord 
# M2_PRINCE :prince 
# M2_MINION :minion 
# M2_GIANT :giant 
# M2_MALE :male 
# M2_FEMALE :female 
# M2_NEUTER :neuter 
# M2_HOSTILE :hostile 
# M2_PEACEFUL :peaceful 
# M2_DOMESTIC :domestic 
# M2_WANDER :wander 
# M2_NASTY :nasty 
# M2_STRONG :strong 
# M2_COLLECT :collect 
# M1_WALLWALK :phase
# M1_NEEDPICK :digger
# MR_DISINT :disintegration
# MR_ELEC :shock
# MR_NO_ELBERETH :no-elbereth
# MS_ORC :grunt
# M2_GREEDY :picks-gold
# M2_JEWELS :picks-jewels
# M2_MAGIC :picks-magic-items
# M2_ROCKTHROW :throws-boulders
# M2_PNAME :proper-name
# M1_TPORT_CNTRL :telecontrol
# M1_TPORT :teleport
# M2_STALK :follows
# M2_MERC :mercenary
# M3_WANTSAMUL :wants-amulet
# M3_WANTSBELL :wants-bell
# M3_WANTSBOOK :wants-book
# M3_WANTSCAND :wants-candelabrum
# M3_WANTSARTI :wants-arti
# M3_WANTSALL :wants-all
# M3_WAITFORU :waits
# M3_CLOSE :approach
# M3_COVETOUS :covetous
# M3_INFRAVISION :infravision
# M3_INFRAVISIBLE :infravisible
# MZ_TINY :tiny
# MZ_SMALL :small
# MZ_MEDIUM :medium
# MZ_HUMAN :human
# MZ_LARGE :large
# MZ_HUGE :huge
# MZ_GIGANTIC :gigantic
# G_UNIQ :unique
# G_NOHELL :no-hell
# G_HELL :hell-only
# G_NOGEN :not-generated
# G_SGROUP :sgroup
# G_LGROUP :lgroup
# G_GENO :genocidable
# G_NOCORPSE :no-corpse
# NO_COLOR nil
# BOLD :bold
# BLACK :blue
# RED :red
# GREEN :green
# YELLOW :brown
# BLUE :blue
# MAGENTA :magenta
# CYAN :cyan
# WHITE nil
# BOLD_RED :orange
# BOLD_GREEN :bright-green
# BOLD_YELLOW :yellow
# BOLD_BLUE :bright-blue
# BOLD_MAGENTA :bright-magenta
# BOLD_CYAN :bright-cyan
# BOLD_WHITE :white
# AT_NONE :passive
# AT_CLAW :claw
# AT_BITE :bite
# AT_KICK :kick
# AT_BUTT :butt
# AT_TUCH :touch
# AT_STNG :sting
# AT_HUGS :hug
# AT_SPIT :spit
# AT_ENGL :engulf
# AT_BREA :breath
# AT_EXPL :explode
# AT_BOOM :boom
# AT_GAZE :gaze
# AT_TENT :tentacle
# AT_WEAP :weapon
# AT_MAGC :magic
# AD_PHYS :physical
# AD_MAGM :magic-missile
# AD_FIRE :fire
# AD_COLD :cold
# AD_SLEE :sleep
# AD_DISN :disintegration
# AD_ELEC :shock
# AD_DRST :poison
# AD_ACID :acid
# AD_BLND :blinding
# AD_STUN :stun
# AD_SLOW :slow
# AD_PLYS :paralysis
# AD_DRLI :drain-xp
# AD_DREN :drain-magic
# AD_LEGS :leg-hurt
# AD_STON :stone
# AD_STCK :stick
# AD_SGLD :steal-gold
# AD_SITM :steal-items
# AD_SEDU :seduce
# AD_TLPT :teleport
# AD_RUST :rust
# AD_CONF :conf
# AD_DGST :digest
# AD_HEAL :heal
# AD_WRAP :wrap
# AD_WERE :lycantrophy
# AD_DRDX :drain-dex
# AD_DRCO :drain-con
# AD_DRIN :drain-int
# AD_DISE :disease
# AD_DCAY :rot
# AD_SSEX :sex
# AD_HALU :hallu
# AD_DETH :death
# AD_PEST :pestilence
# AD_FAMN :famine
# AD_SLIM :slime
# AD_ENCH :disenchant
# AD_CORR :corrode
# AD_CLRC :clerical
# AD_SPEL :spell
# AD_RBRE :breath
# AD_SAMU :steal-amulet
# AD_CURS :curse
