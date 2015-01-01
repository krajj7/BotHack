package bothack.events;

import bothack.bot.IFrame;

public interface IKnowPositionHandler {
	/** Called when the cursor is on the player –
	 *  besides full frames this also occurs on location prompts */
	void knowPosition(IFrame frame);
}
