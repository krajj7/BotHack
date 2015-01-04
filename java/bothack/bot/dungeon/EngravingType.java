package bothack.bot.dungeon;

import clojure.lang.Keyword;

public enum EngravingType {
	/** Temporary engraving. */
	DUST(Keyword.intern(null, "dust")),
	/** Semi-permanent engraving. */
	SEMI(Keyword.intern(null, "semi")),
	/** Permanent engraving. */
	PERMANENT(Keyword.intern(null, "permanent"));

	private final Keyword kw;

	private EngravingType(Keyword kw) {
		this.kw = kw;
	}

	public Keyword getKeyword() {
		return kw;
	}
}
