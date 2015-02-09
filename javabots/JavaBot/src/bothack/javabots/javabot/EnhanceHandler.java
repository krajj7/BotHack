package bothack.javabots.javabot;

import bothack.actions.ActionsComplex;
import bothack.actions.IAction;
import bothack.bot.IGame;
import bothack.prompts.IActionHandler;

public class EnhanceHandler implements IActionHandler {

	public IAction chooseAction(IGame game) {
		if (game.player().canEnhance()) {
			return ActionsComplex.enhanceAll(game);
		} else {
			return null;
		}
	}

}
