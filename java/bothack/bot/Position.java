package bothack.bot;

import java.util.List;

import clojure.java.api.Clojure;

/** Position factory and utility functions. */
final public class Position {
	private Position() {};
	
	static {
		Clojure.var("clojure.core", "require").invoke(Clojure.read("bothack.position"));
	}

	/** 
	 * Make an IPosition with given coordinates.
	 * @param x 0 to 79 inclusive
	 * @param y 0 to 23 inclusive, level tiles appear at 1 to 21 inclusive
	 */
	public static IPosition create(Long x, Long y) {
		return (IPosition) Clojure.var("bothack.position", "position").invoke(x, y);
	}
	
	/** 
	 * Strip the given IPosition map of all keys except :x and :y.
	 * Useful for printing.
	 */
	public static IPosition of(IPosition something) {
		return (IPosition) Clojure.var("bothack.position", "position").invoke(something);
	}
	
	public static Boolean areAdjacent(IPosition pos1, IPosition pos2) {
		return (Boolean) Clojure.var("bothack.position", "adjacent?").invoke(pos1, pos2);
	}
	
	/** Uniform norm distance (steps allowing diagonals). */
	public static Long distance(IPosition pos1, IPosition pos2) {
		return (Long) Clojure.var("bothack.position", "distance").invoke(pos1, pos2);
	}
	
	/** Manhattan distance (not allowing diagonals). */
	public static Long distanceManhattan(IPosition pos1, IPosition pos2) {
		return (Long) Clojure.var("bothack.position", "distance-manhattan").invoke(pos1, pos2);
	}
	
	/**
	 * Returns a list of valid adjacent positions.
	 * @see ILevel#neighbors(IPosition pos)
	 */
	public static List<IPosition> neighbors(IPosition pos) {
		return (List<IPosition>) Clojure.var("clojure.core", "vec").invoke(
				Clojure.var("bothack.position", "neighbors").invoke(pos));
	}
}