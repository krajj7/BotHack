package bothack.bot;

import bothack.actions.Navigation;

/** 
 * Custom filter function.  Implement this for use with
 * {@link IGame#have},
 * {@link Navigation#navigate}, {@link Navigation#seek} etc.
 */
public interface IPredicate<T> {
	boolean apply(T input);
}
