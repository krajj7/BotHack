package bothack.bot;

import bothack.actions.Actions;
import bothack.actions.ActionsComplex;

/** The immutable interface representing player-related state and functionality. */
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
}
