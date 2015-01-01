package bothack.prompts;

/** Called when the player can choose the amount of gold to offer. */
public interface IOfferHandler {
/** Called when the player can choose the amount of gold to offer. 
 * @return Amount of gold to offer*/
	Long offerHowMuch(String prompt);
}
