package bothack.bot;

import java.util.Map;

/** Immutable representation of the dungeon level tree. */
public interface IDungeon {
	/** Returns the Dlvl => ILevel map for the branch. */
	Map<String,ILevel> getBranch(Branch branch);
	/** Returns a level by Branch and Dlvl, if such was already visited. */
	ILevel getLevel(Branch branch, String dlvl);
	/** Returns a level by Branch and tag, if such was already visited and identified. */
	ILevel getLevel(Branch branch, LevelTag tag);
	/** Returns true if the floor is not too hard to dig. */
	Boolean hasDiggableFloor();
}
