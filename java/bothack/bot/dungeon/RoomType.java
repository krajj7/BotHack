package bothack.bot.dungeon;

import clojure.lang.Keyword;

public enum RoomType {
	/** Unknown shop. */
	SHOP(Keyword.intern(null, "shop")),
	/** Only marked around the altar. */
	TEMPLE(Keyword.intern(null, "temple")),
	/** General shop. */
	GENERAL(Keyword.intern(null, "general")),
	/** Armor shop. */
	ARMOR(Keyword.intern(null, "armor")),
	/** Book shop. */
	BOOK(Keyword.intern(null, "book")),
	/** Potion shop. */
	POTION(Keyword.intern(null, "potion")),
	/** Weapon shop. */
	WEAPON(Keyword.intern(null, "weapon")),
	/** Food shop / delicatessen. */
	FOOD(Keyword.intern(null, "food")),
	/** Gem and jewelry shop. */
	GEM(Keyword.intern(null, "gem")),
	/** Wand shop. */
	WAND(Keyword.intern(null, "wand")),
	/** Tool shop. */
	TOOL(Keyword.intern(null, "tool")),
	/** Light shop. */
	LIGHT(Keyword.intern(null, "light"));

	private final Keyword kw;

	private RoomType(Keyword kw) {
		this.kw = kw;
	}

	public Keyword getKeyword() {
		return kw;
	}
}
