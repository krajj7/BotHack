package bothack.bot.monsters;

import java.util.List;

import clojure.java.api.Clojure;

/** Factory and utility functions for item types. */
public final class MonsterType {
	private MonsterType() {};

	static {
		Clojure.var("clojure.core", "require").invoke(Clojure.read("bothack.montype"));
	}
	
	/** If the name is a NetHack monster returns its IMonsterType.
	 * Also works for all-lowercase name variants.
	 * @see IMonsterType#name() */
	public IMonsterType byName(String name) {
		return (IMonsterType) Clojure.var("bothack.montype", "name->monster").invoke(name);
	}
	
	/** Return the list of all monster types recognized by BotHack. */
	public List<IMonsterType> allMonsters() {
		return (List<IMonsterType>) Clojure.var("bothack.montype", "monster-types");
	}
}
