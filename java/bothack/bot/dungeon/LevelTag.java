package bothack.bot.dungeon;

import clojure.lang.Keyword;

public enum LevelTag {
	ORACLE(Keyword.intern(null, "oracle")),
	MINETOWN(Keyword.intern(null, "minetown")),
	MEDUSA(Keyword.intern(null, "medusa")),
	CASTLE(Keyword.intern(null, "castle")),
	SANCTUM(Keyword.intern(null, "sanctum")),
	VOTD(Keyword.intern(null, "votd")),
	/** Bottom level of a three-level branch. */
	BOTTOM(Keyword.intern(null, "bottom")),
	/** Middle level of a three-level branch. */
	MIDDLE(Keyword.intern(null, "middle")),
	/** Branch end (for MAIN branch this is the level with the vibrating square). */
	END(Keyword.intern(null, "end"));

	private final Keyword kw;

	private LevelTag(Keyword kw) {
		this.kw = kw;
	}

	public Keyword getKeyword() {
		return kw;
	}
}
