package bothack.bot.items;

import java.util.List;

import bothack.bot.monsters.IMonsterType;

/** Representation of item "prototypes" of all kinds.
 * @see ItemType
 * @see ItemKind */
public interface IItemType {
	/** Name of the item type. */
	String name();
	/** For artifact items – return the base type. */
	IItemType baseType();
	/** Glyph representation of the item type. */
	Character glyph();
	/** Base price for items of this type. */
	Long price();
	/** Returns the item category. */
	ItemKind kind();
	/** How much the item weights. */
	Long weight();
	/** Initial possible appearances of the item. */
	List<String> appearances();
	/** True if items of this type may stack. */
	Boolean isStackable();
	/** True if the item type is an artifact.
	 * @see IItemType#baseType() */
	Boolean isArtifact();
	/** If true generally won't cause any immediate bad effects upon use. */
	Boolean isSafe();
	/** True if the item requires both hands to wield. */
	Boolean isTwohanded();
	/** How much the item lowers your AC (lower AC = better). */
	Long AC();
	/** Magic cancellation – how well the item protects against magical attack
	 * effects */
	Long MC();
	/** Armor slot or item category. */
	ItemSubtype subtype();
	/**
	 * For corpses, tins, eggs, statues and figurines may return the monster type
	 * if known.
	 */
	IMonsterType monster();
	/** For edible items. */
	Long nutrition();
}
