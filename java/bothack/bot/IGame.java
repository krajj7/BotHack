package bothack.bot;

import java.util.Map;
import java.util.Set;

/** The immutable interface representing a snapshot of all game state at some point. */
public interface IGame {
	IFrame frame();
	IPlayer player();
	Boolean canPray();
	Boolean canEngrave();
	/** Returns the slot-item pair of a levitation item currently in use (or null) */
	Map.Entry<Character,IItem> haveLevitationItemOn();
	/** Returns the set of monster types and classes that have been genocided in the game. */
	Set<String> genocided();
}
