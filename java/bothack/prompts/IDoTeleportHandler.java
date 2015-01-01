package bothack.prompts;

/** 
 * Called when the player is prompted whether or not he wants to teleport.
 * Escaped by default.
 */
public interface IDoTeleportHandler {
/** 
 * Called when the player is prompted whether or not he wants to teleport.
 * Escaped by default.
 * @return true if you want to teleport
 */
	Boolean doTeleport(String prompt);
}
