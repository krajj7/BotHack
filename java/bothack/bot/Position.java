package bothack.bot;

import clojure.java.api.Clojure;

/** Position factory */
final public class Position {
	static {
		Clojure.var("clojure.core", "require").invoke(Clojure.read("bothack.position"));
	}

	/** Make an IPosition with given coordinates */
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
}