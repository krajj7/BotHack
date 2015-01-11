package bothack.bot.dungeon;

import clojure.lang.Keyword;

public enum LevelTag {
	/** Any level that has an altar */
	ALTAR(Keyword.intern(null, "altar")),
	/** Any level that has a temple */
	TEMPLE(Keyword.intern(null, "temple")),
	ORACLE(Keyword.intern(null, "oracle")),
	MINETOWN(Keyword.intern(null, "minetown")),
	MINETOWN_GROTTO(Keyword.intern(null, "minetown-grotto")),
	MEDUSA(Keyword.intern(null, "medusa")),
	CASTLE(Keyword.intern(null, "castle")),
	/** Asmodeus's level */
	ASMODEUS(Keyword.intern(null, "asmodeus")),
	/** Baalzebub's level */
	BAALZEBUB(Keyword.intern(null, "baalzebub")),
	/** Juiblex's level */
	JUIBLEX(Keyword.intern(null, "juiblex")),
	/** Both levels with and without the portal */
	FAKE_WIZTOWER(Keyword.intern(null, "fake-wiztower")),
	/** Entrance to the wizard's tower */
	WIZTOWER(Keyword.intern(null, "wiztower")),
	/** Valley of the dead */
	VOTD(Keyword.intern(null, "votd")),
	/** Bottom level of a three-level branch. */
	BOTTOM(Keyword.intern(null, "bottom")),
	/** Middle level of a three-level branch. */
	MIDDLE(Keyword.intern(null, "middle")),
	/** Branch end (for MAIN branch this is the level with the vibrating square). */
	END(Keyword.intern(null, "end")),
	/** Moloch's sanctum.  Deepest level of the game. */
	SANCTUM(Keyword.intern(null, "sanctum"));

	private final Keyword kw;

	private LevelTag(Keyword kw) {
		this.kw = kw;
	}

	public Keyword getKeyword() {
		return kw;
	}
}
