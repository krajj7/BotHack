package bothack.bot;

/** 
 * The root of BotHack functionality.
 * <p>This represents a handle for the BotHack framework, through which you can
 * register custom handlers, get the current game state etc. </p>
 * <p>Unlike other BotHack interfaces this is the only mutable one.</p>
 */
public interface IBotHack {
	/**
	 * Register a custom handler with the default priority.  
	 * @param handler The handler object should implement one or more interfaces
	 * from {@link bothack.events} or {@link bothack.prompts}.
	 */
	void registerHandler(Object handler);
	/** 
	 * Register a custom handler with the specified priority. 
	 * @param priority Lower number = processed earlier.
	 * @param handler The handler object should implement one or more interfaces
	 * from {@link bothack.events} or {@link bothack.prompts}.
	 */
	void registerHandler(Integer priority, Object handler);
	/**
	 * Deregister a handler that was registered earlier.
	 * Nothing happens if the handler is not registered. 
	 */
	void deregisterHandler(Object handler);
	/**
	 * Replace a handler that was registered earlier (with the same priority).
	 * @throws IllegalArgumentException if oldHandler is not registered. 
	 */
	void replaceHandler(Object oldHandler, Object newHandler);
	/** Returns the current game state snapshot. */
	IGame game();
}
