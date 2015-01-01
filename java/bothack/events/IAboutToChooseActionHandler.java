package bothack.events;

import bothack.bot.IGame;

public interface IAboutToChooseActionHandler {
	void aboutToChoose(IGame gamestate);
}
