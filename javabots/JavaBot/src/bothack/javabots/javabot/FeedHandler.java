package bothack.javabots.javabot;

import bothack.actions.Actions;
import bothack.actions.IAction;
import bothack.actions.Navigation;
import bothack.actions.Navigation.IPath;
import bothack.bot.Hunger;
import bothack.bot.IGame;
import bothack.bot.IPlayer;
import bothack.bot.IPredicate;
import bothack.bot.dungeon.ITile;
import bothack.bot.items.IItem;
import bothack.bot.items.ItemKind;
import bothack.prompts.IActionHandler;

public class FeedHandler implements IActionHandler {
	String getFoodLabel(IGame game, ITile tile, boolean beneficial) {
		final IPlayer player = game.player();
		for (IItem i : tile.items())
			if (i.type().kind() == ItemKind.FOOD &&
					(!i.isCorpse() || (i.isCorpse() && game.isCorpseFresh(tile, i))) &&
					((beneficial && player.canEatWithBenefit(i)) || (!beneficial && player.canEat(i))))
				return i.label();
		return null;
	}
	
	public IAction chooseAction(final IGame game) {
		final IPlayer player = game.player();
		final ITile atPlayer = game.currentLevel().at(player);
		if (player.isOverloaded() || player.hunger() == Hunger.SATIATED) {
			return null;
		}

		// eat beneficial corpses
		IPath res = null;
		res = Navigation.navigate(game, new IPredicate<ITile>() {
			public boolean apply(ITile tile) {
				return getFoodLabel(game, tile, true) != null;
			}
		}, 15);
		if (res != null) {
			if (res.step() != null) {
				return res.step();
			} else {
				return Actions.Eat(getFoodLabel(game, atPlayer, true));
			}
		}
		
		//if (!player.isHungry())
		//	return null;

		// eat anything
		res = Navigation.navigate(game, new IPredicate<ITile>() {
			public boolean apply(ITile tile) {
				return getFoodLabel(game, tile, false) != null;
			}
		}, 15);
		if (res != null) {
			if (res.step() != null) {
				return res.step();
			} else {
				return Actions.Eat(getFoodLabel(game, atPlayer, false));
			}
		}
		
		return null;
	}
}
