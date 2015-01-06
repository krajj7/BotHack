package bothack.bot;

import java.util.Map;
import java.util.Set;

import bothack.actions.Actions;
import bothack.actions.ActionsComplex;
import bothack.bot.items.IItem;
import bothack.bot.monsters.IMonsterType;

/** Immutable representation of player-related state and functionality. */
public interface IPlayer extends IPosition {
	/** Returns the player's current alignment.
	 * Not to be confused with <i>alignment record</i>, which is currently untracked. */
	Alignment alignment();
	/** Returns the current hunger status (possibly null). */
	Hunger hunger();
	/** Returns the current outstanding encumbrance status (possibly null). */
	Encumbrance encumbrance();
	/**
	 * True if there are skills that can be Enhanced.
	 * @see Actions#Enhance()
	 * @see ActionsComplex#enhanceAll(IGame)
	 */
	Boolean canEnhance();
	/** False when polymorphed into a handless or limbless monster. */
	Boolean hasHands();
	/** True if the player has wounded legs.
	 * This can be caused by xan attacks, land mines, bad kicks etc.
	 * and fixes itself in a while. */
	Boolean hasHurtLegs();
	/** True if the player is overloaded.
	 * Almost all actions are impossible in this state. */
	Boolean isOverloaded();
	/** True if the player is overtaxed or worse.
	 * Most actions are impossible in this state. */
	Boolean isOvertaxed();
	/** True if the player is strained or worse.
	 * Some actions are impossible in this state. */
	Boolean isStrained();
	/** True if the player is stressed or worse.
	 * Some actions are impossible in this state. */
	Boolean isStressed();
	/** True if the player is burdened or worse. */
	Boolean isBurdened();
	/** True if the hunger status is WEAK or worse */
	Boolean isWeak();
	/** True if the player is hungry or worse. */
	Boolean isHungry();
	/** True if the player is blinded by a cream pie or venom. */
	Boolean isBlindExternally();
	/** True if the player is engulfed by a monster. */
	Boolean isEngulfed();
	/** Returns the monster type into which the player is currently polymorphed. */
	IMonsterType polymorphed();
	/** Returns true if the player has the given intrinsic. */
	Boolean hasIntrinsic(Intrinsic intrinsic);
	/** Name displayed on the status line. */
	String nickname();
	/** Title displayed on the status line. */
	String title();
	/** The amount of hit points remanining. */
	Long HP();
	/** The maximum amount of hit points. */
	Long maxHP();
	/** The amount of magic power. */
	Long PW();
	/** The maximum amount of magic power. */
	Long maxPW();
	/** Armor class – lower is better. */
	Long AC();
	/** The player's level.  You need at least 14 to be able to enter the quest. */
	Long experienceLevel();
	/** The player's inventory. */
	Map<Character, IItem> inventory();
	/** The slot-item pair for item wielded in hand. */
	Map.Entry<Character, IItem> wielding();
	/** True if the player is stuck in a trap. */
	Boolean isTrapped();
	/** True if the player has restorable drained stats. */
	Boolean isStatDrained();
	/** True if the player is a were-something.
	 * @see IPlayer#polymorphed() */
	Boolean hasLycantrophy();
	/** True if the player is about to turn into stone. */
	Boolean isStoning();
	/** True if the player is blinded.
	 * @see IPlayer#isBlindExternally() */
	Boolean isBlind();
	/** True if the player is stunned. */
	Boolean isStunned();
	/** True if the player is deathly sick (including food poisoning). */
	Boolean isIll();
	/** True if the player is confused or stunned. */
	Boolean isDizzy();
	/** True if the player can eat the food with no ill effects.
	 * Does not check corpse freshness!
	 * @see IGame#isCorpseFresh(IPosition, IItem) */
	Boolean canEat(IItem food);
	/** True if the player can gain intrinsics or stats by eating the corpse.
	 * Does not check corpse freshness!
	 * @see IGame#isCorpseFresh(IPosition, IItem) */
	Boolean canEatWithBenefit(IItem corpse);
	/** How much weight the player can carry before getting burdened. */
	Long carryingCapacity();
	/** Get the effective integer value of stat.
	 * @see IPlayer#getDisplayedStr() */
	Long getStat(Stat stat);
	/** The displayed value of strenght – may be something like 18/10 or 18/** */
	Long getDisplayedStr();
	//Role role();
	//Race race();
}
