package bothack.events;

import bothack.bot.IFrame;

/** Called every time the screen contents change. */
public interface IRedrawHandler {
/** Called every time the screen contents change. */
	void redraw(IFrame frame);
}
