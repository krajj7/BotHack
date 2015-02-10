package bothack.javabots.javabot;

import org.apache.commons.lang3.RandomUtils;

import bothack.actions.Actions;
import bothack.actions.IAction;
import bothack.bot.Direction;
import bothack.bot.IGame;
import bothack.bot.IPlayer;
import bothack.bot.Position;
import bothack.bot.dungeon.ITile;
import bothack.bot.monsters.IMonster;
import bothack.prompts.IActionHandler;

public class FightHandler implements IActionHandler {
	IAction hit(IPlayer player, IMonster m) {
		return Actions.Move(Direction.towards(player, m));
	}
	
	public IAction chooseAction(IGame game) {
		IPlayer player = game.player();
		ITile atPlayer = game.currentLevel().at(player);
		if (player.isEngulfed()) {
			return Actions.Move(Direction.W);
		}
		int adjacent = 0;
		IMonster target = null;
		for (IMonster m : game.currentLevel().monsters().values()) {
			if (!m.isPeaceful() && !m.isFriendly() && Position.areAdjacent(player, m)) {
				++adjacent;
				target = m;
			}
		}
		if ((adjacent > 1) && atPlayer.isEngravable() && RandomUtils.nextInt(0, 2) == 1) 
			return Actions.EngraveAppending('-', "Elbereth");
		if (target != null)
			return hit(game.player(), target);
		return null;
	}
}
