package bothack.events;

import bothack.bot.IGame;

/** Called before IActionHandler#chooseAction is about to be triggered. */
public interface IAboutToChooseActionHandler {
	/** Called before IActionHandler#chooseAction is about to be triggered. */
	void aboutToChoose(IGame gamestate);
}
