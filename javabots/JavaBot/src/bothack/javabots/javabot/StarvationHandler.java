package bothack.javabots.javabot;

import bothack.actions.Actions;
import bothack.actions.IAction;
import bothack.bot.Hunger;
import bothack.bot.IGame;
import bothack.prompts.IActionHandler;

class StarvationHandler implements IActionHandler {
	public IAction chooseAction(IGame game) {
		if (game.player().isWeak() && !game.player().isOverloaded()) {
			// TODO food
		}
		
		if (game.player().hunger() == Hunger.FAINTING && game.canPray())
			return Actions.Pray();
		else
			return null;
	}
}