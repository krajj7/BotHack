package bothack.bot.items;

/** Representation of item "prototypes".
 * @see ItemType */
public interface IItemType {
	/** For artifact items – return the base type. */
	IItemType baseType();
	/** Glyph representation of the item type. */
	Character glyph();
	/** Base price for items of this type. */
	Long price();
}
