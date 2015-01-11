package bothack.bot.dungeon;

import clojure.lang.Keyword;

public enum Branch {
    /** The main dungeon branch – Dungeons of Doom and Gehennom. */
    MAIN(Keyword.intern(null, "main")),
    /** The gnomish mines. */
    MINES(Keyword.intern(null, "mines")),
    /** Sokoban. */
    SOKOBAN(Keyword.intern(null, "sokoban")),
    /** The quest. */
    QUEST(Keyword.intern(null, "quest")),
    /** Fort ludios. */
    LUDIOS(Keyword.intern(null, "ludios")),
    /** Vlad's tower. */
    VLAD(Keyword.intern(null, "vlad")),
    /** The plane of earth (endgame). */
    EARTH(Keyword.intern(null, "earth")),
    /** The plane of fire (endgame). */
    FIRE(Keyword.intern(null, "fire")),
    /** The plane of air (endgame). */
    AIR(Keyword.intern(null, "air")),
    /** The plane of water (endgame). */
    WATER(Keyword.intern(null, "water")),
    /** The astral plane (endgame). */
    ASTRAL(Keyword.intern(null, "astral")),
    /** The wizard's tower. */
    WIZTOWER(Keyword.intern(null, "wiztower")),
    /** An unidentified branch. */
    UNKNOWN_1(Keyword.intern(null, "unknown-1")),
    /** An unidentified branch. */
    UNKNOWN_2(Keyword.intern(null, "unknown-2")),
    /** An unidentified branch. */
    UNKNOWN_3(Keyword.intern(null, "unknown-3")),
    /** An unidentified branch. */
    UNKNOWN_4(Keyword.intern(null, "unknown-4")),
    /** An unidentified branch. */
    UNKNOWN_5(Keyword.intern(null, "unknown-5")),
    /** An unidentified branch. */
    UNKNOWN_6(Keyword.intern(null, "unknown-6")),
    /** An unidentified branch. */
    UNKNOWN_7(Keyword.intern(null, "unknown-7")),
    /** An unidentified branch. */
    UNKNOWN_8(Keyword.intern(null, "unknown-8")),
    /** An unidentified branch. */
    UNKNOWN_9(Keyword.intern(null, "unknown-9")),
    /** An unidentified branch. */
    UNKNOWN_10(Keyword.intern(null, "unknown-10")),
    /** An unidentified branch. */
    UNKNOWN_11(Keyword.intern(null, "unknown-11")),
    /** An unidentified branch. */
    UNKNOWN_12(Keyword.intern(null, "unknown-12")),
    /** An unidentified branch. */
    UNKNOWN_13(Keyword.intern(null, "unknown-13")),
    /** An unidentified branch. */
    UNKNOWN_14(Keyword.intern(null, "unknown-14")),
    /** An unidentified branch. */
    UNKNOWN_15(Keyword.intern(null, "unknown-15")),
    /** An unidentified branch. */
    UNKNOWN_16(Keyword.intern(null, "unknown-16")),
    /** An unidentified branch. */
    UNKNOWN_17(Keyword.intern(null, "unknown-17")),
    /** An unidentified branch. */
    UNKNOWN_18(Keyword.intern(null, "unknown-18")),
    /** An unidentified branch. */
    UNKNOWN_19(Keyword.intern(null, "unknown-19")),
    /** An unidentified branch. */
    UNKNOWN_20(Keyword.intern(null, "unknown-20"));

    private final Keyword kw;
    private Branch(Keyword kw) {
        this.kw = kw;
    }

    public Keyword getKeyword() {
        return kw;
    }
}
