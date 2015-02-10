package bothack.javabots.javabot;

import bothack.actions.Actions;
import bothack.actions.ActionsComplex;
import bothack.actions.IAction;
import bothack.bot.Hunger;
import bothack.bot.IGame;
import bothack.prompts.IActionHandler;

class MajorTroubleHandler implements IActionHandler {
	public IAction chooseAction(IGame game) {
		if ((game.player().hunger() == Hunger.FAINTING 
				|| game.player().hasLycantrophy()
				|| game.player().isIll()
				|| (game.player().HP() < 11 && game.player().maxHP() > 30))
				&& game.canPray())
			return ActionsComplex.withReason(Actions.Pray(), "major trouble");
		return null;
	}
}