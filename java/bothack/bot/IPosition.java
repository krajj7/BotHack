package bothack.bot;

/**
 * Immutable representation of a position on the screen.
 * @see bothack.bot.Position#create(Long, Long) 
 */
public interface IPosition {
	/** X coordinate on the screen – 0 to 79 */
	Long x();
	/** Y coordinate on the screen – 1 to 21 */
	Long y();
	// the Longs are probably wasteful but it's what's currently used internally (Clojure's default)
}