package bothack.bot;

import java.util.Map;

/** The immutable interface representing a dungeon level with its inhabitants. */
public interface ILevel {
	/** Returns the Dlvl string for the level. */
	String dlvl();
	Map<IPosition,IMonster> monsters();
	IMonster monsterAt(IPosition pos);
}
