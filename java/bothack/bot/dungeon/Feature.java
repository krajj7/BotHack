package bothack.bot.dungeon;

import clojure.lang.Keyword;

public enum Feature {
	ROCK(Keyword.intern(null, "rock")),
	FLOOR(Keyword.intern(null, "floor")),
	WALL(Keyword.intern(null, "wall")),
	STAIRS_UP(Keyword.intern(null, "stairs-up")),
	STAIRS_DOWN(Keyword.intern(null, "stairs-down")),
	CORRIDOR(Keyword.intern(null, "corridor")),
	ALTAR(Keyword.intern(null, "altar")),
	POOL(Keyword.intern(null, "pool")),
	DOOR_OPEN(Keyword.intern(null, "door-open")),
	DOOR_CLOSED(Keyword.intern(null, "door-closed")),
	DOOR_LOCKED(Keyword.intern(null, "door-locked")),
	DOOR_SECRET(Keyword.intern(null, "door-secret")),
	SINK(Keyword.intern(null, "sink")),
	FOUNTAIN(Keyword.intern(null, "fountain")),
	GRAVE(Keyword.intern(null, "grave")),
	THRONE(Keyword.intern(null, "throne")),
	BARS(Keyword.intern(null, "bars")),
	TREE(Keyword.intern(null, "tree")),
	DRAWBRIDGE_RAISED(Keyword.intern(null, "drawbridge-raised")),
	DRAWBRIDGE_LOWERED(Keyword.intern(null, "drawbridge-lowered")),
	LAVA(Keyword.intern(null, "lava")),
	ICE(Keyword.intern(null, "ice")),
	/** Unknown trap */
	TRAP(Keyword.intern(null, "trap")),
	ANTIMAGIC(Keyword.intern(null, "antimagic")),
	ARROWTRAP(Keyword.intern(null, "arrowtrap")),
	BEARTRAP(Keyword.intern(null, "beartrap")),
	DARTTRAP(Keyword.intern(null, "darttrap")),
	FIRETRAP(Keyword.intern(null, "firetrap")),
	HOLE(Keyword.intern(null, "hole")),
	MAGICTRAP(Keyword.intern(null, "magictrap")),
	ROCKTRAP(Keyword.intern(null, "rocktrap")),
	MINE(Keyword.intern(null, "mine")),
	LEVELPORT(Keyword.intern(null, "levelport")),
	PIT(Keyword.intern(null, "pit")),
	POLYTRAP(Keyword.intern(null, "polytrap")),
	PORTAL(Keyword.intern(null, "portal")),
	BOULDERTRAP(Keyword.intern(null, "bouldertrap")),
	RUSTTRAP(Keyword.intern(null, "rusttrap")),
	SLEEPTRAP(Keyword.intern(null, "sleeptrap")),
	SPIKEPIT(Keyword.intern(null, "spikepit")),
	SQUEAKY(Keyword.intern(null, "squeaky")),
	TELETRAP(Keyword.intern(null, "teletrap")),
	TRAPDOOR(Keyword.intern(null, "trapdoor")),
	WEB(Keyword.intern(null, "web")),
	STATUETRAP(Keyword.intern(null, "statuetrap"));

	private final Keyword kw;

	private Feature(Keyword kw) {
		this.kw = kw;
	}

	public Keyword getKeyword() {
		return kw;
	}
}
