package bothack.bot;

import clojure.lang.Keyword;

public enum Intrinsic {
	TELEPATHY(Keyword.intern(null, "telepathy")),
	/** Teleport control */
	TELECONTROL(Keyword.intern(null, "telecontrol")),
	/** Fire resistance */
	FIRE(Keyword.intern(null, "fire")),
	/** Cold resistance */
	COLD(Keyword.intern(null, "cold")),
	/** Sleep resistance */
	SLEEP(Keyword.intern(null, "sleep")),
	/** Shock resistance */
	SHOCK(Keyword.intern(null, "shock")),
	/** Poison resistance */
	POISON(Keyword.intern(null, "poison")),
	/** Teleportitis */
	TELEPORT(Keyword.intern(null, "teleport")),
	/** Disintegration resistance */
	DISINTEGRATION(Keyword.intern(null, "disintegration")),
	/** Warning (numbers for nearby unseen monsters) */
	WARNING(Keyword.intern(null, "warning")),
	/** Doesn't wake up monsters */
	STEALTH(Keyword.intern(null, "stealth")),
	/** Aggravate monster */
	AGGRAVATE(Keyword.intern(null, "aggravate")),
	INVISIBILITY(Keyword.intern(null, "invisibility")),
	/** See invisible */
	SEE_INVIS(Keyword.intern(null, "see-invis")),
	/** Automatic searching */
	SEARCH(Keyword.intern(null, "search")),
	SPEED(Keyword.intern(null, "speed"));

    private final Keyword kw;
    private Intrinsic(Keyword kw) {
        this.kw = kw;
    }

    public Keyword getKeyword() {
        return kw;
    }
}