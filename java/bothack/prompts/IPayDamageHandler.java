package bothack.prompts;

/** Called when the player is asked to pay damage caused in the shop. */
public interface IPayDamageHandler {
/** 
 * Called when the player is asked to pay damage caused in the shop.
 * The damage is paid for by default.
 * @return False to refuse paying for the damage. */
	Boolean payDamage(String prompt);
}
