# Known issues

## Of the framework

* if monsters dig out parts of shops in minetown while the bot is entering, the entire level may be marked as a shop and the bot will likely loop trying to price-id things
* amnesia may break item identification for named items and cause a loop (should forget facts about forgotten names but it doesn't always work)
* very rarely the scraper gets stuck in unusual situations (eg. many potions breaking during farming).  should use [vt\_tiledata](http://nethackwiki.com/wiki/Vt_tiledata) instead.  the auto-unstuck mechanism after 3 idle minutes usually fixes the situation however.
* baalzebub level and some other lairs could use more pre-mapping and faster recog, the bot is usually digging out weird patterns.
* items with really long labels (blessed +0 thoroughly rusty thoroughly corroded ...) may cut off before "(being worn)" and cause issues.
* may get stuck in sokoban when boulders get destroyed (monster with a striking wand)
* if two same direction sets of stairs get generated on a level with possibly ambiguous branching (mines range, vlad range) may get confused about which leads where (hard to handle as the bot may randomly appear on the other stair or elsewhere when it descends for the first time)
* zapping spells is not implemented
* multidrop is not implemented
* only valkyrie and samurai have the quest mapped, other roles will get stuck
* sliming is not handled (doesn't have a reliable message when fixed)
* rare unhandled prompt "That jade ring looks pretty.  May I have it?" [yn] (n)

## Of mainbot

* may reach the castle without a wand of striking at get stuck
* when it fails uncursing invocation artifacts it gets stuck
* if fountains at Oracle don't yield Excalibur will dip in minetown and anger guards
* may get stoned by medusa if she descends
* may name a fake Amulet of Yendor as real if Rodney enters the sanctum with a fake (should bag-id)
* unbagging gold piece by piece
* should spend less time exploring during the ascension run
* covetous monsters should be handled more efficiently
* doesn't recover if invocation artifacts fall into water or lava (but tries to avoid it)
* if rodney steals the amulet and gets ported downstairs by the mysterious force, bot will keep looking for him upstairs
* farming will likely fail if there's a trapdoor or a hole or when a djinni appears at the sink
* when missing a usable ring of levitation, bot can't enter Rodney's tower and gets stuck searching
* may hit peacefuls when blind
* hits gas spores even when peacefuls are around
