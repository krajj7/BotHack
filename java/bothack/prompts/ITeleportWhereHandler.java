package bothack.prompts;

import bothack.bot.IPosition;

/** Called when the player can teleport in a controlled way. */
public interface ITeleportWhereHandler {
/** Called when the player can teleport in a controlled way. 
 * Escaped by default.
 * @return Position to teleport to. */
	IPosition teleportWhere();
}
