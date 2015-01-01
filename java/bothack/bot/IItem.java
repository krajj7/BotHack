package bothack.bot;

/** 
 * The immutable interface representing an item.
 * The item may be on the ground or in inventory or not exist at all. 
 */
public interface IItem {
	Long quantity();
	String label();
}
