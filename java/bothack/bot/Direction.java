package bothack.bot;

import clojure.lang.Keyword;

public enum Direction {
    N(Keyword.intern(null, "N")),
    NE(Keyword.intern(null, "NE")),
    E(Keyword.intern(null, "E")),
    SE(Keyword.intern(null, "SE")),
    S(Keyword.intern(null, "S")),
    SW(Keyword.intern(null, "SW")),
    W(Keyword.intern(null, "W")),
    NW(Keyword.intern(null, "NW"));

    private final Keyword kw;
    private Direction(Keyword kw) {
        this.kw = kw;
    }

    public Keyword getKeyword() {
        return kw;
    }
}
