package bothack.prompts;

/** Called when the player can make a wish. */
public interface IMakeWishHandler {
/** Called when the player can make a wish. 
	* For auto-identification to work use a "standard" item label 
	* (same as the item would appear in inventory) 
	* Nothing is wished for by default.
	* 
	* @return Label of the item to wish for, with the same order of modifiers
	* as would appear in the inventory */
	String makeWish(String prompt);
}
