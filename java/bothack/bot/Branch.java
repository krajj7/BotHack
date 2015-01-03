package bothack.bot;

import clojure.lang.Keyword;

public enum Branch {
    MAIN(Keyword.intern(null, "main")),
    MINES(Keyword.intern(null, "mines")),
    SOKOBAN(Keyword.intern(null, "sokoban")),
    QUEST(Keyword.intern(null, "quest")),
    LUDIOS(Keyword.intern(null, "ludios")),
    VLAD(Keyword.intern(null, "vlad")),
    EARTH(Keyword.intern(null, "earth")),
    FIRE(Keyword.intern(null, "fire")),
    AIR(Keyword.intern(null, "air")),
    WATER(Keyword.intern(null, "water")),
    ASTRAL(Keyword.intern(null, "astral")),
    UNKNOWN_1(Keyword.intern(null, "unknown-1")),
    UNKNOWN_2(Keyword.intern(null, "unknown-2")),
    UNKNOWN_3(Keyword.intern(null, "unknown-3")),
    UNKNOWN_4(Keyword.intern(null, "unknown-4")),
    UNKNOWN_5(Keyword.intern(null, "unknown-5")),
    UNKNOWN_6(Keyword.intern(null, "unknown-6")),
    UNKNOWN_7(Keyword.intern(null, "unknown-7")),
    UNKNOWN_8(Keyword.intern(null, "unknown-8")),
    UNKNOWN_9(Keyword.intern(null, "unknown-9")),
    UNKNOWN_10(Keyword.intern(null, "unknown-10")),
    UNKNOWN_11(Keyword.intern(null, "unknown-11")),
    UNKNOWN_12(Keyword.intern(null, "unknown-12")),
    UNKNOWN_13(Keyword.intern(null, "unknown-13")),
    UNKNOWN_14(Keyword.intern(null, "unknown-14")),
    UNKNOWN_15(Keyword.intern(null, "unknown-15")),
    UNKNOWN_16(Keyword.intern(null, "unknown-16")),
    UNKNOWN_17(Keyword.intern(null, "unknown-17")),
    UNKNOWN_18(Keyword.intern(null, "unknown-18")),
    UNKNOWN_19(Keyword.intern(null, "unknown-19")),
    UNKNOWN_20(Keyword.intern(null, "unknown-20"));

    private final Keyword kw;
    private Branch(Keyword kw) {
        this.kw = kw;
    }

    public Keyword getKeyword() {
        return kw;
    }
}
