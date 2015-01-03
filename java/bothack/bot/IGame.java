package bothack.bot;

import java.util.Map;
import java.util.Set;

/** Immutable representation of the game state at some point. */
public interface IGame {
	/** Returns the last snapshot of the virtual terminal screen. */
	IFrame frame();
	/** Returns the player representation. */
	IPlayer player();
	/** True if the player can safely pray (95% confidence) */
	Boolean canPray();
	/** True if the player is capable of engraving and is not currently on the plane of air or water. */
	Boolean canEngrave();
	/** Returns the slot-item pair of a levitation item currently in use (or null) */
	Map.Entry<Character,IItem> haveLevitationItemOn();
	/** Returns the set of monster types and classes that have been genocided in the game. */
	Set<String> genocided();
	/** Returns the current dungeon level. */
	ILevel currentLevel();
}
