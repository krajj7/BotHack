package bothack.events;

/** 
 * Called when a message appears on the top line (without --More-- if any).
 * Game state may not be fully updated yet.
 */
public interface IToplineMessageHandler {
/** 
 * Called when a message appears on the top line (without --More-- if any).
 * Game state may not be fully updated yet.
 */
	void message(String text);
}
