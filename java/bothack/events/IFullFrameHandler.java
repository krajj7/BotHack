package bothack.events;

import bothack.bot.IFrame;

public interface IFullFrameHandler {
	/** Called when the frame on screen is fully drawn – the cursor is on the player,
	 *  the map and status lines are completely drawn. */
	void fullFrame(IFrame frame);
}
