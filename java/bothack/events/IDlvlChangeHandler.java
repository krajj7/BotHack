package bothack.events;

	/** 
	 * Called when the player switches dungeon levels.
	 * Game state may not be fully updated yet.
	 */
public interface IDlvlChangeHandler {
	/** 
	 * Called when the player switches dungeon levels.
	 * Game state may not be fully updated yet.
	 */
	void dlvlChanged(String oldDlvl, String newDlvl);
}
