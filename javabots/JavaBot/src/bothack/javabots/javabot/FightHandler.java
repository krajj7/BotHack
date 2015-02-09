package bothack.javabots.javabot;

import bothack.actions.Actions;
import bothack.actions.IAction;
import bothack.bot.Direction;
import bothack.bot.IGame;
import bothack.bot.IPlayer;
import bothack.bot.Position;
import bothack.bot.monsters.IMonster;
import bothack.prompts.IActionHandler;

public class FightHandler implements IActionHandler {

	public IAction chooseAction(IGame game) {
		IPlayer player = game.player();
		if (player.isEngulfed()) {
			return Actions.Move(Direction.W);
		}
		for (IMonster m : game.currentLevel().monsters().values()) {
			if (Position.areAdjacent(player, m)) {
				return Actions.Move(Direction.towards(player, m));
			}
		}
		return null;
	}

}
