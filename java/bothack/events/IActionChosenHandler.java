package bothack.events;

import bothack.actions.IAction;

/** 
 * Called with the action that was chosen in response to
 * {@link bothack.prompts.IActionHandler#chooseAction(bothack.bot.IGame)}
 * and is about to be triggered. 
 */
public interface IActionChosenHandler {
	/** Called with the action that was chosen and is about to be triggered. */
	void actionChosen(IAction action);
}
