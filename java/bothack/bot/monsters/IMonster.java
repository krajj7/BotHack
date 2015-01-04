package bothack.bot.monsters;

import bothack.bot.IAppearance;
import bothack.bot.IPosition;

/** Immutable representation a monster on a level.
 * It may be currently seen or otherwise known or just remembered by the monster tracker.
 * @see IMonster#isRemembered() */
public interface IMonster extends IPosition,IAppearance {
	/** Turn number when the monster was last seen or otherwise known. */
	Long lastKnown();
	/** Type of the monster if known, else null */
	IMonsterType type();
	/** True if the monster was seen moving. */
	Boolean isAwake();
	/** True for tame monsters */
	Boolean isFriendly();
	/** True for peaceful monsters */
	Boolean isPeaceful();
	/** True if the monster is not currently known to be there. */
	Boolean isRemembered();
	/** True if the monster moved in the last turn */
	Boolean hasJustMoved();
}
