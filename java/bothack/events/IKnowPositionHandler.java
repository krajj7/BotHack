package bothack.events;

import bothack.bot.IFrame;

	/**
	 * Called when the cursor is on the player –
	 * besides full frames this also occurs on location prompts.
	 * Game state may not be fully updated yet.
	 */
public interface IKnowPositionHandler {
	/**
	 * Called when the cursor is on the player –
	 * besides full frames this also occurs on location prompts.
	 * Game state may not be fully updated yet.
	 */
	void knowPosition(IFrame frame);
}
