package bothack.actions;

import bothack.bot.IBotHack;

/** 
 * Representation of a primitive BotHack action.
 * This is not meant to be implemented by user code.
 * @see Actions
 */
public interface IAction {
	/**
	 * Retuns null or an event/prompt handler that will be registered just
	 * before executing the action and deregistered when the next action is
	 * chosen.  May also modify the game state immediately without returning
	 * a handler.
	 */
	Object handler(IBotHack bh);
	/** Returns the string to write to NetHack to trigger the action. */
	String trigger();
}
