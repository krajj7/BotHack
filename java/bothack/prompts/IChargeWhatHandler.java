package bothack.prompts;

/** Called when the player may choose an item to charge after reading
 * a scroll of charging. */
public interface IChargeWhatHandler {
/** Called when the player may choose an item to charge after reading
 * a scroll of charging.  Return the slot of the item to be charged. 
 * Escaped by default.
 * 
 * @return the slot of the item to be charged.
 */
	Character chargeWhat(String prompt);
}
