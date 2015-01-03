package bothack.bot;

import java.util.Map;

public interface IDungeon {
	Map<String,ILevel> getBranch(Branch branch);
	ILevel getLevel(Branch branch, String dlvl);
	ILevel getLevel(Branch branch, LevelTag tag);
}
