ANBF
====

A Nethack Bot Framework

An attempt at an implementation of a NetHack bot framework as a base for my master's thesis.

Written in Clojure but provides a nice API for bots in Java.  No game modifications are used to make the game more accessible for a bot, so bots can also play on public servers (http://alt.org/nethack/).

Inspired by previous bots and bot frameworks, in particular TAEB (http://taeb.github.io/) and Pogamut (http://pogamut.cuni.cz/).

## Milestones reached

**15.3.2014**: A basic GUI-less virtual terminal emulator is working, implemented using the JTA Telnet/SSH/Terminal library (http://www.javatelnet.org/).
Manual interaction with a remote telnet terminal is possible via the emulator, current frame for the terminal can be accessed programatically and displayed.

**25.3.2014**: A simple event-driven script (src/anbf/bot/nao\_menu.clj) can interact with the nethack.alt.org game menu and start a game.

**17.4.2014**: A first trivial bot implementation can run around the first level blindly ~~until it starves~~ and prays for food until it gets smitten.  It can read game messages but doesn't yet understand any.  Synchronization with NetHack is solved in a way that is only hinted at on the TAEB blog (http://taeb-nethack.blogspot.cz/2009/06/synchronizing-with-nethack.html) – so far it seems very reliable, but it is at the cost of more round-trips to the server.

Code for the bot: https://github.com/krajj7/ANBF/blob/master/src/anbf/bots/simplebot.clj

**27.4.2014**: The trivial bot is now also implemented in Java as an example of the Java API use.

Code for the Java bot: https://github.com/krajj7/ANBF/blob/master/javabots/SimpleBot/src/anbf/javabots/SimpleBot.java

## Roadmap

### Short-term goals

Basic senses – track player status (proper statusline parsing), inventory etc.

More actions, handling of prompts and menus.

Parsing of the map, model of the game world.

More advanced bot.

### Medium-term goals

Pathfinding and long-distance travel.

Make basic monster and item stats available.

### Future goals

Basic heuristic monster tracking.

Utilities for shopping and price-identification of items.

Utilities for solving sokoban.

...
