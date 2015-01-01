/** 
 * Notifications for things that happen in the game world.
 * 
 * <p>All registered handlers are invoked for each event in priority order.</p>
 * 
 * <p>The bot may implement these if found useful.  The framework automatically
 * uses information from these events to update the game state.</p>
 * 
 * <p>There are more undocumented event types in the package bothack.internal.</p>
 * 
 * @see bothack.bot.IBotHack#registerHandler(Integer, Object)
 */
package bothack.events;