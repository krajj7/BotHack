package bothack.javabots.javabot;

import bothack.actions.Actions;
import bothack.actions.IAction;
import bothack.actions.NavOption;
import bothack.actions.Navigation;
import bothack.actions.Navigation.IPath;
import bothack.bot.IGame;
import bothack.bot.IPlayer;
import bothack.bot.IPredicate;
import bothack.bot.dungeon.ITile;
import bothack.bot.items.IItem;
import bothack.bot.items.ItemType;
import bothack.prompts.IActionHandler;

public class GatherItems implements IActionHandler {
	IPredicate<ITile> hasGold = new IPredicate<ITile>() {
		public boolean apply(ITile tile) {
			for (IItem i : tile.items())
				if (i.type().equals(ItemType.byName("gold piece")))
					return true;
			return false;
		}
	};
	
	boolean wantGold(IGame game) {
		return game.gold() < 100;
	}

	public IAction chooseAction(IGame game) {
		if (!wantGold(game))
			return null;
		final IPlayer player = game.player();
		ITile atPlayer = game.currentLevel().at(player);
		IPath res = null;
		res = Navigation.navigate(game, hasGold, NavOption.EXPLORED);
		if (res != null) {
			if (res.step() != null) {
				return res.step();
			} else {
				IItem gold = null;
				for (IItem i : atPlayer.items()) {
					if (i.type().equals(ItemType.byName("gold piece"))) {
						gold = i;
						break;
					}
				}
				return Actions.PickUp(gold.label());
			}
		}
		return null;
	}
}