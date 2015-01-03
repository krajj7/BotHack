package bothack.bot;

import clojure.lang.Keyword;

public enum Color {
    RED(Keyword.intern(null, "red")),
    GREEN(Keyword.intern(null, "green")),
    BROWN(Keyword.intern(null, "brown")),
    BLUE(Keyword.intern(null, "blue")),
    MAGENTA(Keyword.intern(null, "magenta")),
    CYAN(Keyword.intern(null, "cyan")),
    GRAY(Keyword.intern(null, "gray")),
    BOLD(Keyword.intern(null, "bold")),
    ORANGE(Keyword.intern(null, "orange")),
    BRIGHT_GREEN(Keyword.intern(null, "bright-green")),
    YELLOW(Keyword.intern(null, "yellow")),
    BRIGHT_BLUE(Keyword.intern(null, "bright-blue")),
    BRIGHT_MAGENTA(Keyword.intern(null, "bright-magenta")),
    BRIGHT_CYAN(Keyword.intern(null, "bright-cyan")),
    WHITE(Keyword.intern(null, "white")),
    INVERSE(Keyword.intern(null, "inverse")),
    INVERSE_RED(Keyword.intern(null, "inverse-red")),
    INVERSE_GREEN(Keyword.intern(null, "inverse-green")),
    INVERSE_BROWN(Keyword.intern(null, "inverse-brown")),
    INVERSE_BLUE(Keyword.intern(null, "inverse-blue")),
    INVERSE_MAGENTA(Keyword.intern(null, "inverse-magenta")),
    INVERSE_CYAN(Keyword.intern(null, "inverse-cyan")),
    INVERSE_GRAY(Keyword.intern(null, "inverse-gray")),
    INVERSE_BOLD(Keyword.intern(null, "inverse-bold")),
    INVERSE_ORANGE(Keyword.intern(null, "inverse-orange")),
    INVERSE_BRIGHT_GREEN(Keyword.intern(null, "inverse-bright-green")),
    INVERSE_YELLOW(Keyword.intern(null, "inverse-yellow")),
    INVERSE_BRIGHT_BLUE(Keyword.intern(null, "inverse-bright-blue")),
    INVERSE_BRIGHT_MAGENTA(Keyword.intern(null, "inverse-bright-magenta")),
    INVERSE_BRIGHT_CYAN(Keyword.intern(null, "inverse-bright-cyan")),
    INVERSE_WHITE(Keyword.intern(null, "inverse-white"));

    private final Keyword kw;
    private Color(Keyword kw) {
        this.kw = kw;
    }

    public Keyword getKeyword() {
        return kw;
    }
}