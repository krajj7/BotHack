package bothack.bot.dungeon;

import java.util.List;

import bothack.bot.Alignment;
import bothack.bot.IAppearance;
import bothack.bot.IGame;
import bothack.bot.IPosition;
import bothack.bot.items.IItem;

/**
 *  Immutable representation of a tile of the dungeon.
 *  This doesn't include monsters present on the tile.
 *  @see ILevel#monsters()
 *  @see ILevel#monsterAt(IPosition)
 */
public interface ITile extends IPosition,IAppearance {
	/** 
	 * True if there is an undamaged Elbereth engraving on the tile. 
	 * @see ITile#engravingType()
	 */
	Boolean hasElbereth();
	/**
	 * True if it is possible to engrave on this tile by some means. 
	 * @see IGame#canEngrave()
	 */
	Boolean isEngravable();
	/** The dungeon feature present on this tile or null. */
	Feature feature();
	/** 
	 * True if the tile has a trap.
	 * @see ITile#feature()
	 */
	Boolean isTrap();
	/** True if there appears to be a boulder on the tile. */
	Boolean hasBoulder();
	/** True if the player was adjacent to the tile when not blind. */
	Boolean wasSeen();
	/** First game turn the tile has been stepped on. */
	Long firstWalkedTurn();
	/** Last game turn the tile has been stepped on. */
	Long lastWalkedTurn();
	/** 
	 * True if the player dug the tile or it is assumed it has been dug.  
	 * Not 100% reliable. 
	 */
	Boolean dug();
	/** Number of times the tile has been searched (est.) */
	Long searched();
	/** Items laying on the ground on the tile */
	List<IItem> items();
	/** True if items on the tile seem to have changed since it was last examined. */
	Boolean hasNewItems();
	/** Returns the text engraved on this tile. */
	String engraving();
	/** If there is an engraving returns the longevity type. */
	EngravingType engravingType();
	/** If the tile is in a recognized room returns the room type. */
	RoomType room();
	/** For tiles with the SINK feature - true if kicking it already yielded a ring. */ 
	Boolean sinkGaveRing();
	/** For tiles with the SINK feature - true if kicking it already yielded a succubus or an incubus. */ 
	Boolean sinkGaveFoocubus();
	/** For tiles with the SINK feature - true if kicking it already yielded a pudding. */
	Boolean sinkGavePudding();
	/** For visited stairs or portals - returns the target branch. */
	Branch leadsTo();
	/** For altars - returns the altar's alignment. */
	Alignment altarAlignment();
	/** True for the vibrating square. */
	Boolean isVibrating();
}
