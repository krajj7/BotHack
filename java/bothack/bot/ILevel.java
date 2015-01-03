package bothack.bot;

import java.util.List;
import java.util.Map;

/** Immutable representation of a dungeon level with its inhabitants. */
public interface ILevel {
	/** Returns the Dlvl string for the level. */
	String dlvl();
	/** Returns the branch this level belongs to. */
	Branch branch();
	/** Returns the tile at given position on the level. */
	ITile at(IPosition pos);
	/** Returns the map of all monsters known or remembered to be on the level. */
	Map<IPosition,IMonster> monsters();
	/** Returns the monster representation at the given position. */
	IMonster monsterAt(IPosition pos);
	/** Returns the list of tiles on the level adjacent to position pos. */
	List<ITile> neighbors(IPosition pos);
	/** True if the level contains a temple. */
	Boolean hasTemple();
	/** True if the level contains an altar. */
	Boolean hasAltar();
	/** True if the level contains a shop. */
	Boolean hasShop();
	/** True if the level floor can be dug through (until proven otherwise). */
	Boolean hasDiggableFloor();
}
