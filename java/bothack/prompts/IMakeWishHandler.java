package bothack.prompts;

public interface IMakeWishHandler {
	/** For auto-identification to work use a standard item label 
	 * (same as the item would appear in inventory) */
	String makeWish(String prompt);
}
