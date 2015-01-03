package bothack.bot;

import clojure.lang.Keyword;

public enum Stat {
	/** Charisma */
    CHA(Keyword.intern(null, "cha")),
	/** Wisdom */
    WIS(Keyword.intern(null, "wis")),
	/** Intelligence */
    INT(Keyword.intern(null, "int")),
	/** Constitution */
    CON(Keyword.intern(null, "con")),
	/** Dexterity */
    DEX(Keyword.intern(null, "dex")),
	/** Strength */
    STR(Keyword.intern(null, "str"));

    private final Keyword kw;
    private Stat(Keyword kw) {
        this.kw = kw;
    }

    public Keyword getKeyword() {
        return kw;
    }
}
