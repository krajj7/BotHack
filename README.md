ANBF
====

A Nethack Bot Framework

An attempt at an implementation of a NetHack bot framework as a base for my master's thesis.

Written in Clojure but will provide an API for bots in Java.  No game modifications are used to make the game more accessible for a bot, so bots can also play on public servers (http://alt.org/nethack/).

Inspired by previous bots and bot frameworks, in particular TAEB (http://taeb.github.io/) and Pogamut (http://pogamut.cuni.cz/).

## Milestones reached

**15.3.2014**: A basic GUI-less virtual terminal emulator is working, implemented using the JTA Telnet/SSH/Terminal library (http://www.javatelnet.org/).
Manual interaction with a remote telnet terminal is possible via the emulator, current frame for the terminal can be accessed programatically and displayed.

**25.3.2014**: A simple event-driven script (src/anbf/bot/nao\_menu.clj) can interact with the nethack.alt.org game menu and start a game.

**17.4.2014**: A first trivial bot implementation can run around the first level blindly ~~until it starves~~ and pray for food until it gets smitten.  It can read game messages but doesn't yet understand any.  Synchronization with NetHack is solved in a way that is only hinted at on the TAEB blog (http://taeb-nethack.blogspot.cz/2009/06/synchronizing-with-nethack.html) – so far it seems very reliable, but it is at the cost of more round-trips to the server.

Code for the bot: https://github.com/krajj7/ANBF/blob/master/src/anbf/bots/simplebot.clj

**27.4.2014**: The trivial bot is now also implemented in Java as an example of the Java API use.

Code for the Java bot: https://github.com/krajj7/ANBF/blob/master/javabots/SimpleBot/src/anbf/javabots/SimpleBot.java

**25.5.2014**: The framework can parse the map for basic level layout info and allows for a slightly more complex bot (explorebot.clj) that can explore the first level using A\* pathfinding and charge monsters it encounters.

**14.6.2014**: The bot can navigate effectively as far as minetown/oracle, where it usually gets killed for lack of combat tactics.  It can find hidden doors and passages and looks almost like a newbie human playing ;-)  The framework provides basic monster tracking and shop recognition allowing the bot to deal with (avoid) peaceful monsters and shopkeepers quite nicely.

**22.6.2014**: When boosted in wizmode (NetHack debug mode) the testing bot can navigate autonomously as far as Medusa's lair and deal with all kinds of situations like boulders blocking the way.  Item handling will need to be implemented before the bot can progress further.

A ttyrec of one Medusa run is in the repo:
https://github.com/krajj7/ANBF/blob/master/ttyrec/wizmode-exploration-dlvl1-28medusa.ttyrec?raw=true

**14.7.2014**: The framework can now recognize all types of monsters found in the dungeon and provides information about their properties.  Monsters that have ambiguous representation (like dwarf king/mind flayer) are queried automatically with the FarLook command, so bots don't have to worry about ambiguities or possibly-peaceful monsters at all.

**26.7.2014**: Navigation has been expanded to allow navigating (either exploring everything fully or going as fast as possible) to any given branch – so far only above the Castle – or special level (Minetown, Oracle, Medusa's lair, ...).  Unvisited branches and levels are automatically searched for.

Item handling is still missing so bots can't cross Medusa's yet, but in wizmode the testing bot can use the new navigation API to easily explore dungeon branches above the Castle in any desired order, without having to explicitly deal with finding the right stairs/portal etc.

## Roadmap

### Short-term goals

Handling of items and inventory.

Navigation using pickaxe / ring of levitation.

### Future goals

Item identification.

Smarter combat tactics for the bot, ranged combat.

Utilities for shopping and price-identification of items.

Utilities for solving sokoban.

Utilities for trapping cross-aligned minetown priest.

...
