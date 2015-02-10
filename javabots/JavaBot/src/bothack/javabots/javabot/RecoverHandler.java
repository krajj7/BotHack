package bothack.javabots.javabot;

import bothack.actions.Actions;
import bothack.actions.ActionsComplex;
import bothack.actions.IAction;
import bothack.actions.Navigation;
import bothack.actions.Navigation.IPath;
import bothack.bot.IGame;
import bothack.bot.IPredicate;
import bothack.bot.dungeon.ITile;
import bothack.prompts.IActionHandler;

public class RecoverHandler implements IActionHandler {
	public IAction chooseAction(IGame game) {
		
		IPath res = null;
		res = Navigation.navigate(game, new IPredicate<ITile>() {
			public boolean apply(ITile i) {
				return i.hasNewItems();
			}
		}, 5);
		if (res != null && res.step() != null)
			return res.step();
		
		if ((double)game.player().HP() / game.player().maxHP() < 8.0/10.0)
			return ActionsComplex.withReason(Actions.Search(), "recovering");
		return null;
	}
}
