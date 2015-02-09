package bothack.bot;

import clojure.java.api.Clojure;
import clojure.lang.Keyword;

public enum Direction {
    N(Keyword.intern(null, "N")),
    NE(Keyword.intern(null, "NE")),
    E(Keyword.intern(null, "E")),
    SE(Keyword.intern(null, "SE")),
    S(Keyword.intern(null, "S")),
    SW(Keyword.intern(null, "SW")),
    W(Keyword.intern(null, "W")),
    NW(Keyword.intern(null, "NW")),
    DOWN(Keyword.intern(null, ">")),
    UP(Keyword.intern(null, "<")),
    HERE(Keyword.intern(null, "."));

	static {
		Clojure.var("clojure.core", "require").invoke(Clojure.read("bothack.position"));
	}

	private final Keyword kw;

	private Direction(Keyword kw) {
		this.kw = kw;
	}

	public Keyword getKeyword() {
		return kw;
	}

	public static Direction towards(IPosition p1, IPosition p2) {
		return Direction.valueOf(((Keyword) Clojure.var("bothack.position", "towards").invoke(p1, p2)).getName());
	}
}
