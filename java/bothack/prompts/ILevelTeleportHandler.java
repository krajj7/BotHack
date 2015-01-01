package bothack.prompts;

/**
 * Called when the player can choose a level to teleport to.
 * Escaped by default.
 */
public interface ILevelTeleportHandler {
/**
 * Called when the player can choose a level to teleport to.
 * Escaped by default.
 * @return The number of the level to teleport to
 */
	Long leveltele(String prompt);
}
