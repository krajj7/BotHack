# Programming BotHack bots in Java

**WORK IN PROGRESS**

This tutorial provides a high-level overview of the BotHack Java API functionality.

For low-level documentation of individual classes and methods, see the [BotHack Java API reference (JavaDoc)](http://krajj7.github.io/BotHack/javadoc/).

<!-- - compiling, running NH, bot skeleton -->
## Compiling and running BotHack bots

To get started, you should try running the example bot skeleton that you can later expand.  This is described on separate pages:

* [Running Java bots](https://github.com/krajj7/BotHack/blob/master/doc/running.md)

* [Configuration options, logging](https://github.com/krajj7/BotHack/blob/master/doc/config.md)

<!-- - IANBF + handlers, IAction, action reasons -->
## BotHack basics, event and prompt handlers

The BotHack framework is an event-driven system.  The central component is the delegator, which registers event and prompt handlers and invokes them as events and prompts arrive.

When an event arrives at the delegator, all registered handlers are invoked serially in order of their priority.  Event handlers don't return anything.  The bot doesn't usually need to handle events, except when you want it to change its internal state in response to something specific happening.  Prompts are just like events, except the handlers are invoked only up to the first one that returns a non-null value (the response to the prompt).

[Summary of public event handlers](http://krajj7.github.io/BotHack/javadoc/bothack/events/package-summary.html)
[Summary of public prompt handlers](http://krajj7.github.io/BotHack/javadoc/bothack/prompts/package-summary.html)

Handlers can be registered and deregistered or replaced dynamically.  The framework is composed of many internal handlers, some are permanent, some are bound to the action that is currently being performed.  These provide defaults for most of the prompts and integrate consequences of events into the model of the game world.

The bot must register its own handlers with the delegator, most importantly an implementation of the [IActionHandler](http://krajj7.github.io/BotHack/javadoc/bothack/prompts/IActionHandler.html), which is invoked every time the bot must choose an action to perform.

Registration of handlers is performed through the [IBotHack](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IBotHack.html) interface, an instance of which is passed as a parameter to the bot's main class constructor.

[Example bot initialization, registering custom handlers](https://github.com/krajj7/BotHack/blob/master/javabots/JavaBot/src/bothack/javabots/javabot/JavaBot.java)

![Communication diagram](http://krajj7.github.io/BotHack/doc/bothack-comm.png)

<!-- - basic world repre: IGame, ILevel -->
## NetHack world representation

BotHack provides a model of the NetHack game world that the bots can query when deciding on what to do.  This means bots don't have to care about parsing the terminal contents, but rather deal directly with what it represents.  

Since there are many ambiguities in NetHack's display, some information may not always be represented entirely or completely reliably.  I have tried to make the representation as practical as possible for typical bots, even when not completely accurate in problematic situations.

An immutable snapshot of the current game world representation ([IGame](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IGame.html)) can be obtained from the [IBotHack](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IBotHack.html#game()) interface, or it is provided as a parameter in some prompt and event methods.  This is the root of all knoweledge about the state of the NetHack world at some point.

Various properties of the bot's avatar are available via the [IPlayer](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IPlayer.html) interface.  This contains information about the bot's health, XP, hunger, various afflictions and also the known resistances and intrinsics.
<!-- - player senses: hp, xp, hunger, state, inventory, resistances ... -->

The layout of the dungeon and its branches is available via the [IDungeon](http://krajj7.github.io/BotHack/javadoc/bothack/bot/dungeon/IDungeon.html) interface.  Sometimes the bot may not be able to tell which branch it is currently in, for example if it descends into a completely dark room from Dlvl:3, it may not be possible to tell immediately if it ended up in the gnomish mines.  The branch ID for these levels will be set to a placeholder value (like [UNKNOWN\_1](http://krajj7.github.io/BotHack/javadoc/bothack/bot/dungeon/Branch.html#UNKNOWN_1)) which will be recognized as the actual branch once it is identified.  The branch ID and Dlvl combination always uniquely and permanently identifies a level in the dungeon.

The current level and monsters that the bot knows about are represented in the [ILevel](http://krajj7.github.io/BotHack/javadoc/bothack/bot/dungeon/ILevel.html) interface.  The current level can be obtained by calling [currentLevel](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IGame.html#currentLevel()) on IGame.

## Actions

[actions](http://krajj7.github.io/BotHack/javadoc/bothack/actions/package-summary.html)
[IActionHandler](http://krajj7.github.io/BotHack/javadoc/bothack/prompts/IActionHandler.html)

<!-- - moving around, exploring: move, navigate, seek, explore, misc actions (sit) -->
<!-- - navigating across levels/branches - ascend, descend, seek-level -->
## Navigation, exploration

popsat strategii

<!-- - attacking monsters: move, attack (no strategy helpers implemented), mention tracker -->
## Attacking monsters

<!-- - items & inventory mgmt: pickup, drop, identification, weight, all other actions -->
## Items and inventory management

<!-- - feeding, corpse tracker -->
## Food

<!-- - calling clojure directly (non-wrapped fns), nonpublic data, menubots -->
## Advanced: accessing Clojure functionality and data directly

All of the BotHack functionality mentioned here is written in Clojure, with some Java code wrapping it up in a simpler, more accessible API.  The Clojure code contains a lot of functions and data that were left out from the Java API, usually because I considered them internal or not useful for most bots.  With a bit of extra effort these are however still accessible from Java, if you find something useful that is missing in the public API.  This may also be a way to re-use parts of or extend [mainbot](https://github.com/krajj7/BotHack/blob/master/src/bothack/bots/mainbot.clj), the Clojure bot capable of winning the game.

See the [Clojure Java API](http://clojure.github.io/clojure/javadoc/clojure/java/api/package-summary.html) documentation for how to do this.  See also [Navigation.java](https://github.com/krajj7/BotHack/blob/master/java/bothack/actions/Navigation.java) for functional examples of Java facades for Clojure functions.

Some data that you may find useful that is not available from the Java API directly is the [data about monsters](https://github.com/krajj7/BotHack/blob/master/src/bothack/montype.clj) and [data about item types](https://github.com/krajj7/BotHack/blob/master/src/bothack/itemdata.clj).

See also: [Compiling BotHack from source and running Clojure bots](https://github.com/krajj7/BotHack/blob/master/doc/compiling.md)
