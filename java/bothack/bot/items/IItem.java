package bothack.bot.items;

import java.util.List;

import bothack.bot.IGame;

/** 
 * Immutable representation of an item.
 * The item may be on the ground or in inventory or not exist at all. 
 */
public interface IItem {
	/** For stackable items.  Non-stackable will return 1. */
	Long quantity();
	/** How the item appears in the inventory. */
	String label();
	/** The stripped base name of the item.  Japanese names are converted to english. 
	 * This may not be the same as the item's {@link IItemType#name()}!
	 * It may be an unidentified appearance like "buckled boots", which
	 * is not an existing IItemType name. */
	String name();
	/** Blessedness of the item (if known). */
	BUC buc();
	/** How corroded/burnt/rusty the item is (0-3). */
	Long erosion();
	/** True for rustproof/fireproof/fixed items. */
	Boolean isFixed();
	/** Positive or negative enchantment.  Null if unknown. */
	Long enchantment();
	/** Displayed number of charges.  Null if unknown.
	 * @see IItem#isCharged() */
	Long charges();
	/** Displayed number of recharges.  Null if unknown.
	 * @see IItem#isRecharged() */
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
	/** Returns properties of the item that can be determined <b>without the
	* current game context (discoveries)</b>.
	* @see IGame#identifyType(IItem) */
	IItemType type();
	/** Returns the list of all possible item types for the item <b>without
	* considering the current game context (discoveries)</b>. 
	* @see IGame#identifyPossibilities(IItem) */
	List<IItemType> possibilities();
	/** Price demanded at a shop.  Null if none.
	 * @see IItemType#price() */
	Long cost();
	/** True if the item is worn or wielded. */
	Boolean isInUse();
	/** True if the item wielded by the player. */
	Boolean isWielded();
	/** True if the item worn by the player. */
	Boolean isWorn();
	/** True if the item may contain other items. */
	Boolean isContainer();
	/** False if the item is a container with unknown contents. */
	Boolean knowContents();
	/** If the item is a container returns the contents. 
	 * @see IItem#isContainer()*/
	List<IItem> contents();
	/** True if the item is a monster's corpse. */
	Boolean isCorpse();
}
