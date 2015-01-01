package bothack.prompts;

import bothack.bot.IAction;
import bothack.bot.IGame;

public interface IActionHandler {
	IAction chooseAction(IGame gamestate);
}
