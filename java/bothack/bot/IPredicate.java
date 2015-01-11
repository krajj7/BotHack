package bothack.bot;

import bothack.actions.Navigation;

/** 
 * Custom filter function.  Implement this for use with
 * {@link IGame#have},
 * {@link Navigation#navigate}, {@link Navigation#seek} etc.
 */
public interface IPredicate<T> {
	/** 
	 * Return true for things you are interested in.
	 * This function may be run many times, don't do expensive computation here if you can avoid it. */
	boolean apply(T input);
}
