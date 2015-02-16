**Table of Contents**  

- [Programming BotHack bots in Java](#)
    - [Compiling and running BotHack bots](#)
    - [BotHack basics, event and prompt handlers](#)
    - [NetHack world representation](#)
    - [Actions](#)
    - [Navigation, exploration](#)
    - [Fighting monsters](#)
    - [Items: identification and inventory management](#)
    - [Food](#)
    - [Shopping and special rooms](#)
    - [Advanced: accessing Clojure functionality and data directly](#)

# Programming BotHack bots in Java

This tutorial provides a high-level overview of the BotHack Java API functionality.

For low-level documentation of individual classes and methods, see the [BotHack Java API reference (JavaDoc)](http://krajj7.github.io/BotHack/javadoc/).

A word of warning: You may find Java a cumbersome language to develop and debug bots in.  Many things that are trivial in Clojure require significant boilerplate in Java.  I can only recommend using a more dynamic JVM-based language (like Clojure) with the possibility to easily debug and reload code on the fly – this tutorial will still apply.

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

*Communication diagram:*

![Communication diagram](http://krajj7.github.io/BotHack/doc/bothack-comm.png)

<!-- - basic world repre: IGame, ILevel -->
## NetHack world representation

BotHack provides a model of the NetHack game world that the bots can query when deciding on what to do.  This means bots don't have to care about parsing the terminal contents, but rather deal directly with what it represents.  

Since there are many ambiguities in NetHack's display, some information may not always be represented entirely or completely reliably.  I have tried to make the representation as practical as possible for typical bots, even when not completely accurate in problematic situations.

An immutable snapshot of the current game world representation ([IGame](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IGame.html)) can be obtained from the [IBotHack](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IBotHack.html#game()) interface, or it is provided as a parameter in some prompt and event methods.  This is the root of all knoweledge about the state of the NetHack world at some point.  All of the interfaces referenced from IGame are also backed by immutable objects so they can be saved and remembered, used as keys in Maps, shared freely between threads etc.

Various properties of the bot's avatar are available via the [IPlayer](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IPlayer.html) interface.  This contains information about the bot's health, XP, hunger, various afflictions and also the known resistances and intrinsics.
<!-- - player senses: hp, xp, hunger, state, inventory, resistances ... -->

The layout of the dungeon and its branches is available via the [IDungeon](http://krajj7.github.io/BotHack/javadoc/bothack/bot/dungeon/IDungeon.html) interface.  Sometimes the bot may not be able to tell which branch it is currently in, for example if it descends into a completely dark room from Dlvl:3, it may not be possible to tell immediately if it ended up in the gnomish mines.  The branch ID for these levels will be set to a placeholder value (like [UNKNOWN\_1](http://krajj7.github.io/BotHack/javadoc/bothack/bot/dungeon/Branch.html#UNKNOWN_1)) which will be recognized as the actual branch once it is identified.  The branch ID and Dlvl combination always uniquely and permanently identifies a level in the dungeon.

The current level and monsters that the bot knows about are represented in the [ILevel](http://krajj7.github.io/BotHack/javadoc/bothack/bot/dungeon/ILevel.html) interface.  The current level can be obtained by calling [currentLevel](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IGame.html#currentLevel()) on IGame.

Most of the special levels of NetHack that are not completely randomly generated (Medusa's island, demon lairs, quest...) have their layouts partially pre-mapped in BotHack.  Once the level is recognized, static information about the level layout is automatically added to the world representation, which makes navigation much easier.

Individual level tiles are represented by the [ITile](http://krajj7.github.io/BotHack/javadoc/bothack/bot/dungeon/ITile.html) interface, from which you can find out whether the tile was stepped on by the player, the [dungeon feature](http://krajj7.github.io/BotHack/javadoc/bothack/bot/dungeon/Feature.html), list of items laying on the tile and various other properties.

## Actions

The [IActionHandler](http://krajj7.github.io/BotHack/javadoc/bothack/prompts/IActionHandler.html) has been mentioned previously in this tutorial.  A bot needs to have one or more of these registered with the delegator and when they are invoked, one of them must return the next action to perform.  Actions are represented by the [IAction](http://krajj7.github.io/BotHack/javadoc/bothack/actions/IAction.html) interface.  The bot is not supposed to implement this interface itself (although it is possible), but rather use one of the factories in the [bothack.actions](http://krajj7.github.io/BotHack/javadoc/bothack/actions/package-summary.html) package to create the desired action.

The primitive actions of NetHack are available for creation from the [Actions](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Actions.html) factory class.  These actions generally have handlers associated with them which will use the constructor arguments of the action to respond appropriately to prompts and events that may result from the action.  For example the [Unlock](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Actions.html#Unlock(java.lang.Character,%20bothack.bot.Direction)) action will automatically answer the "Apply what?" and "In which direction?" prompts when they appear.  These handlers are deregistered once the action is finished.

The actions available in the [Actions](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Actions.html) class are all primitive, "dumb" actions that have no extra validation logic, so you have to be careful to check if you can actually perform the action in the current game context, rather than trying to engrave without free hands, read while blind etc.

There is also the [ActionsComplex](http://krajj7.github.io/BotHack/javadoc/bothack/actions/ActionsComplex.html) factory available, which offers "smarter" variants of some of the primitive actions and some helpers for more complex actions, like the [sokoban solver](http://krajj7.github.io/BotHack/javadoc/bothack/actions/ActionsComplex.html#doSokoban(bothack.bot.IGame)).  These will generally also check preconditions and return null instead of the IAction if the action cannot be performed effectively in the current context.

There are also two "action modifiers" available from ActionsComplex: [withReason()](http://krajj7.github.io/BotHack/javadoc/bothack/actions/ActionsComplex.html#withReason(bothack.actions.IAction,%20java.lang.String)) and [withoutLevitation()](http://krajj7.github.io/BotHack/javadoc/bothack/actions/ActionsComplex.html#withoutLevitation(bothack.bot.IGame,%20bothack.actions.IAction)).  You can pass any primitive or complex action to these.  See the javadoc links for what they do.

The last action factory available in BotHack is [Navigation](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Navigation.html), which is covered in the next section.

<!-- - moving around, exploring: move, navigate, seek, explore, misc actions (sit) -->
<!-- - navigating across levels/branches - ascend, descend, seek-level -->
## Navigation, exploration

One of the biggest strengths of the BotHack framework are the powerful high-level navigation and auto-exploration capabilities available from the [Navigation](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Navigation.html) factory class.  These solve some of the most painful problems a NetHack bot has, which is moving around the dungeon effectively and not getting stuck on or killed by the various dungeon features (doors, hidden passages, pools of water or lava etc.) or dungeon inhabitants (peaceful monsters, guards, shopkeepers...)

While the primitive [Move](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Actions.html#Move(bothack.bot.Direction)) action is available, as well as Search, Ascend/Descend, Open/Close etc., these are very low-level for direct usage.  When using the Navigation methods, you can for example simply say ["explore minetown"](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Navigation.html#exploreLevel(bothack.bot.IGame,%20bothack.bot.dungeon.Branch,%20java.lang.String)) and you will get an action that will lead to finding and exploring the level, even if you haven't even entered the mines before.  The bot will automatically search the appropriate level range for double stairs, look for hidden doors or dig when stuck, avoid peacefuls, use a ring of levitation appropriately if available etc.

You can similarly navigate to the nearest tile on the current level that satisfies a predicate that you implement using the [navigate](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Navigation.html#navigate(bothack.bot.IGame,%20bothack.bot.IPredicate,%20bothack.actions.NavOption...)) or [seek](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Navigation.html#seek(bothack.bot.IGame,%20bothack.bot.IPredicate)) functions.

You can read the [Navigation class JavaDoc](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Navigation.html) for details about the available methods and actions the bot may try when navigating.

The navigation methods can generally handle any situation and level layout, including the endgame planes, provided that the bot has a pick-axe, a ring of levitation and a key or a lock pick available.  If these are not available, some levels may not be navigable, and the resulting situations could be difficult even for humans to handle.  In these cases the bot will likely search hopelessly until throwing a "stuck" exception.  This is usually not a problem at least until the Medusa's island level (the mines and the main dungeon until Medusa's are generally navigable without any special items), at which point the only possibility may be to dig down to the castle and use the guaranteed wand of wishing to obtain the missing items.

<!-- - attacking monsters: move, attack (no strategy helpers implemented), mention tracker -->
## Attacking monsters

A player will not get very far in NetHack without having to deal with the various hostile monsters randomly generated in the dungeon.

BotHack doesn't provide any pre-programmed combat tactics, so you will have to use the navigation facilities in combination with [Move](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Actions.html#Move(bothack.bot.Direction)), [Attack](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Actions.html#Attack(bothack.bot.Direction)) and other primitive actions to fight monsters.

BotHack provides a simple tracker which will remember the presence and properties of monsters that were seen but went out of the line of sight, or moved within it.  Monsters with ambiguous appearance or peacefulness status are automatically examined by the FarLook command (which costs no game turns), so you can always know the actual type and state of the monsters the bot is facing.

<!-- - items & inventory mgmt: pickup, drop, identification, weight, all other actions -->
## Items: identification and inventory management

BotHack maintains a representation of items in the player's inventory and on the floors of the dungeon.  There are two interfaces for this: [IItem](http://krajj7.github.io/BotHack/javadoc/bothack/bot/items/IItem.html) and [IItemType](http://krajj7.github.io/BotHack/javadoc/bothack/bot/items/ItemType.html).  IItemType represents the identified "prototype" (eg. a wand of wishing) for an IItem (eg. a tin wand).  Until fully identified an IItem may have multiple possibilities for an IItemType.

Another neat feature of BotHack is smart identification of items.  BotHack maintains a database of facts about object identities (eg. which potion is a potion of healing, how much a red potion costs, what is the engrave effect of a wand) and uses this automatically to determine possible identities of random item appearances.  Simply walking through a shop will trigger price-identification, and the bot will then be presented only with the non-eliminated possibilities, or the actual fully-identified types even for items that are not really identified in-game.  Zapping or engraving with wands, dropping rings into sinks and trying to sell items in shops will also help identification.

This functionality is available from the [IGame](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IGame.html) interface by methods [identifyType](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IGame.html#identifyType(bothack.bot.items.IItem)), [identifyPossibilities](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IGame.html#identifyPossibilities(bothack.bot.items.IItem)) and a few others.

Representation of the player's inventory and carrying capacity is accessible from the [IPlayer](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IPlayer.html) interface, and there are some nice helper methods for querying the inventory in IGame (since they also need the item fact DB), namely the [have](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IGame.html#have) and [haveAll](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IGame.html#haveAll) methods, through which you can easily check for available items matching an item type and various criteria (not cursed, safe to use, possible to use etc.) without having to iterate over the inventory and containers manually.

Primitive actions for interacting with items like [PickUp](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Actions.html#PickUp(java.lang.String)), [Apply](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Actions.html#Apply(java.lang.Character)), [Read](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Actions.html#Read(java.lang.Character)) etc. are available in the [Actions](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Actions.html) factory.

There are also some more high-level functions for item usage in [ActionsComplex](http://krajj7.github.io/BotHack/javadoc/bothack/actions/ActionsComplex.html), for example [makeUse](http://krajj7.github.io/BotHack/javadoc/bothack/actions/ActionsComplex.html#makeUse(bothack.bot.IGame,%20java.lang.Character)) and [removeUse](http://krajj7.github.io/BotHack/javadoc/bothack/actions/ActionsComplex.html#removeUse(bothack.bot.IGame,%20java.lang.Character)), which will handle wearing and removing armor including handling possible blockers when possible – for example removing a cloak to wear a suit of armor.

Unfortunately, there is no automatic inventory management (picking up and dropping items) in BotHack, so bots have to deal with this themselves.  It would be very nice to have a declarative way to specify what the bot should gather and maintain in what quantities, letting the framework deal with deciding on when to drop or pick something up, but this currently remains as possible future work.

<!-- - feeding, corpse tracker -->
## Food

A specific important kind of items is food.  The bot needs to get nutrition to stay alive, usually also by eating corpses of edible monsters.  

You can check hunger status from the IPlayer interface and BotHack also provides a tracker of corpse freshness ([isCorpseFresh](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IGame.html#isCorpseFresh(bothack.bot.IPosition,%20bothack.bot.items.IItem))) which can help you to only eat safe food and not get fatally poisoned by eating old or unsafe corpses.  This works by remembering which monsters were killed by the bot at what time for each tile, considering unknown corpses unsafe by default.

You can check for edibility ([canEat](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IPlayer.html#canEat(bothack.bot.items.IItem))) or possible beneficial effects (gaining intrinsics) of corpses and food items, taking into account the player's known intrinsics ([canEatWithBenefit](http://krajj7.github.io/BotHack/javadoc/bothack/bot/IPlayer.html#canEatWithBenefit(bothack.bot.items.IItem))).

The [Eat](http://krajj7.github.io/BotHack/javadoc/bothack/actions/Actions.html#Eat) action can then be used to eat food from the inventory or from the ground.

You can look at the [JavaBot FeedHandler](https://github.com/krajj7/BotHack/blob/master/javabots/JavaBot/src/bothack/javabots/javabot/FeedHandler.java) for example usage of this functionality.

## Shopping and special rooms

When your bot picks up items from a shop, you can simply navigate outside of it and the Pay command will automatically be used.  Everything will be paid for by default, assuming you have enough gold.  If not, you should drop items you can't afford or the bot will get stuck in the shop.

Use the [ITile.room](http://krajj7.github.io/BotHack/javadoc/bothack/bot/dungeon/ITile.html#room()) function to find out if the tile belongs to a shop and the type of the shop.  Besides shops, BotHack also marks temple tiles around the altar with [RoomType.TEMPLE](http://krajj7.github.io/BotHack/javadoc/bothack/bot/dungeon/RoomType.html#TEMPLE).  Other kinds of special rooms (barracks, graveyards etc.) don't have great significance for bots so they are not marked.

<!-- - calling clojure directly (non-wrapped fns), nonpublic data, menubots -->
## Advanced: accessing Clojure functionality and data directly

All of the BotHack functionality mentioned here is written in Clojure, with some Java code wrapping it up in a simpler, more accessible API.  The Clojure code contains a lot of functions and data that were left out from the Java API, usually because I considered them internal or not useful for most bots.  With a bit of extra effort these are however still accessible from Java, if you find something useful that is missing in the public API.  This may also be a way to re-use parts of or extend [mainbot](https://github.com/krajj7/BotHack/blob/master/src/bothack/bots/mainbot.clj), the Clojure bot capable of winning the game.

See the [Clojure Java API](http://clojure.github.io/clojure/javadoc/clojure/java/api/package-summary.html) documentation for how to do this.  See also [Navigation.java](https://github.com/krajj7/BotHack/blob/master/java/bothack/actions/Navigation.java) for functional examples of Java facades for Clojure functions.

Some data that you may find useful that is not available from the Java API directly is the [data about monsters](https://github.com/krajj7/BotHack/blob/master/src/bothack/montype.clj) and [data about item types](https://github.com/krajj7/BotHack/blob/master/src/bothack/itemdata.clj).

See also: [Compiling BotHack from source and running Clojure bots](https://github.com/krajj7/BotHack/blob/master/doc/compiling.md)
