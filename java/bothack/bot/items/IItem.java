package bothack.bot.items;

/** 
 * Immutable representation of an item.
 * The item may be on the ground or in inventory or not exist at all. 
 */
public interface IItem {
	/** For stackable items.  Non-stackable will return 1. */
	Long quantity();
	/** How the item appears in the inventory. */
	String label();
	/** The stripped base name of the item.  Japanese names are converted to english. */
	String name();
	/** Blessedness of the item (if known). */
	BUC buc();
	/** How corroded/burnt/rusty the item is (0-3). */
	Long erosion();
	/** True for rustproof/fireproof/fixed items. */
	Boolean isFixed();
	/** Positive or negative enchantment. */
	Long enchantment();
	/** Displayed number of charges. */
	Long charges();
	/** Displayed number of recharges. */
	Long recharges();
	/** True if the item is not known to be empty. */
	Boolean isCharged();
	/** True if the item has been recharged. */
	Boolean isRecharged();
	/** True if the item requires both hands to wield. */
	Boolean isTwohanded();
	/** True if the item can be safely enchanted. */
	Boolean isSafeToEnchant();
	/** True if the item is an artifact.
	 * @see IItemType#baseType() */
	Boolean isArtifact();
	/** Returns the item type if it can be determined <b>without the current game
	* context (discoveries)</b>. */
	IItemType type();
	/** Price demanded at a shop.
	 * @see IItemType#price() */
	Long cost();
}
