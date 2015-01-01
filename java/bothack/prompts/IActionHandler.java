package bothack.prompts;

import bothack.actions.Actions;
import bothack.actions.ActionsComplex;
import bothack.actions.IAction;
import bothack.bot.IGame;

/** 
 * This is the most crucial interface to implement in your bot
 * (in one or more registered handlers).
 * 
 * If no action is chosen by any handler the framework will quit the game by
 * default.
 * If you set ":no-exit true" in the configuration an IllegalStateException will
 * be thrown instead.
 */
public interface IActionHandler {
/** 
 * Called when the game state is fully updated and NetHack is expecting
 * a player action.
 * 
 * @return The action to be performed in the next turn
 * 
 * @see Actions
 * @see ActionsComplex
 */
	IAction chooseAction(IGame gamestate);
}