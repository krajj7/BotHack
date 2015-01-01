package bothack.prompts;

/** Called when the player can choose items to identify. */
public interface IIdentifyWhatHandler {
/** Called when the player can choose items to identify.
 * Escaped by default.
 * @return The slot of the item you want identified. */
	Character identifyWhat(java.util.Map<Character,String> options);
}
