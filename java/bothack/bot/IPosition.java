package bothack.bot;

import clojure.lang.IPersistentMap;

/** Implemented by anything that has a position on the screen.
 * @see bothack.bot.Position#create(Long, Long) */
public interface IPosition extends IPersistentMap {
	/** 0 to 79 */
	Long x();
	/** 1 to 21 */
	Long y();
	// the Longs are probably wasteful but it's what's currently used internally (Clojure's default)
}