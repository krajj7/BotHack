package bothack.javabots.javabot;

import bothack.actions.IAction;
import bothack.actions.Navigation;
import bothack.bot.IGame;
import bothack.bot.dungeon.Branch;
import bothack.bot.dungeon.LevelTag;
import bothack.prompts.IActionHandler;

class ProgressHandler implements IActionHandler {
	public IAction chooseAction(IGame game) {
		IAction res = null;
		res = Navigation.explore(game, Branch.MAIN, LevelTag.ORACLE);
		if (res != null)
			return res;
		res = Navigation.explore(game, Branch.MINES, LevelTag.MINETOWN);
		if (res != null)
			return res;
		res = Navigation.explore(game, Branch.MAIN, LevelTag.QUEST);
		if (res != null)
			return res;
		res = Navigation.explore(game, Branch.MINES);
		if (res != null)
			return res;
		res = Navigation.explore(game, Branch.MAIN);
		return res;
	}
}