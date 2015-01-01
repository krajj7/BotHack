package bothack.javabots.javabot;

import bothack.actions.*;
import bothack.bot.*;
import bothack.prompts.*;

class StarvationHandler implements IActionHandler {
	@Override
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