/** 
 * Representations of elements of the game world.
 * <p>All interfaces except for IBotHack are backed by immutable objects and can
 * be used as keys in a Map, shared freely between threads etc.</p>
 * <p>None of these except IPredicate are meant to be implemented by user code.</p>
 * @see bothack.bot.IBotHack#game()
 */
package bothack.bot;