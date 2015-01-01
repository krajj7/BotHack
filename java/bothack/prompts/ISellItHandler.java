package bothack.prompts;

/** 
 * Called when you drop an item in a shop and the shopkeeper is interested
 * in buying it.
 */
public interface ISellItHandler {
/** 
 * Called when you drop an item in a shop and the shopkeeper is interested
 * in buying it.
 * False is returned by default.
 * 
 * @return True to sell the item.
 */
	Boolean sellIt(Integer offer, String what);
}
