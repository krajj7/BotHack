package bothack.javabots.javabot;

import bothack.actions.Actions;
import bothack.actions.ActionsComplex;
import bothack.actions.IAction;
import bothack.bot.IGame;
import bothack.bot.IPlayer;
import bothack.prompts.IActionHandler;

public class MinorTroubleHandler implements IActionHandler {
	public IAction chooseAction(IGame game) {
		IPlayer player = game.player();
		if (player.polymorphed() != null || player.isBlind() || player.isDizzy()) {
			return ActionsComplex.withReason(Actions.Search(), "waiting out minor trouble");
		}
		return null;
	}
}