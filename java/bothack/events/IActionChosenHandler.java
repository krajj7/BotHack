package bothack.events;

import bothack.actions.IAction;

/** Called with the action that was chosen and is about to be triggered. */
public interface IActionChosenHandler {
	/** Called with the action that was chosen and is about to be triggered. */
	void actionChosen(IAction action);
}
