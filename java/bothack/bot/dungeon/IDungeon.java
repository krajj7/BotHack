package bothack.bot.dungeon;

import java.util.Map;

/** Immutable representation of the dungeon level tree. */
public interface IDungeon {
	/** Returns the Dlvl => ILevel map for the branch, if it was already visited and recognized. */
	Map<String,ILevel> getBranch(Branch branch);
	/** Returns a level by Branch and Dlvl, if such was already visited. */
	ILevel getLevel(Branch branch, String dlvl);
	/** Returns a level by Branch and tag, if such was already visited and identified. */
	ILevel getLevel(Branch branch, LevelTag tag);
}
