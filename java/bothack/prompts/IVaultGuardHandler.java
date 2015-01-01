package bothack.prompts;

/** Called when a guard finds the player in a vault. */
public interface IVaultGuardHandler {
/** Called when a guard finds the player in a vault. 
 * The prompt is escaped by default.  If the bot doesn't drop its gold it will
 * be attacked.
 * @return "Croesus" to make the guard leave (leaving you possibly stuck in the vault) */
	String whoAreYou(String prompt);
}
