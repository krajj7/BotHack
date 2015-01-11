package bothack.actions;

import bothack.bot.dungeon.ITile;
import clojure.lang.Keyword;

public enum NavOption {
	/** Don't use any actions except Move to reach the target.
	 * Means no door opening, digging etc. */
	WALKING(Keyword.intern(null, "walking")),
	/** Navigate to nearest tile adjacent to the target. */
	ADJACENT(Keyword.intern(null, "adjacent")),
	/** Only navigate through tiles that were already seen or walked. */
	EXPLORED(Keyword.intern(null, "explored")),
	/** Avoid all traps. */
	NO_TRAPS(Keyword.intern(null, "no-traps")),
	/** Don't use digging tools. */
	NO_DIG(Keyword.intern(null, "no-dig")),
	/** Don't use levitation items. */
	NO_LEVITATION(Keyword.intern(null, "no-levitation")),
	/** Prefer to step on tiles with new items.
	 * This is useful for quicker exploration but potentially dangerous when
	 * retreating as tiles with new items may likely be traps.
	 * @see ITile#hasNewItems() */
	PREFER_ITEMS(Keyword.intern(null, "prefer-items")),
	/** Don't use the autonavigation (_) command.
	 * Autonavigation makes the bot play much faster but may miss interesting
	 * game updates (monsters closing in).  Recommended when fighting,
	 * otherwise is usually safe as NetHack stops autonavigation in dangerous
	 * situations. */
	NO_AUTONAV(Keyword.intern(null, "no-autonav")),
	/** Don't path through tiles containing hostile monsters (seen or remembered).
	 * There is no sophisticated monster avoidance, the path may still be
	 * dangerous or end up blocked when monsters move. */
	NO_FIGHT(Keyword.intern(null, "no-fight")),
	/** For interlevel navigation. */
	UP(Keyword.intern(null, "up"));

	private final Keyword kw;

	private NavOption(Keyword kw) {
		this.kw = kw;
	}

	public Keyword getKeyword() {
		return kw;
	}
}
