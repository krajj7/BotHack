package bothack.bot;

import bothack.actions.ActionsComplex;
import clojure.lang.Keyword;

public enum HaveOption {
	/** Only items not known to be cursed. */
	NONCURSED(Keyword.intern(null, "noncursed")),
	/** Only items not known to be blessed. */
	NONBLESSED(Keyword.intern(null, "nonblessed")),
	/** Only items known to be blessed. */
	BLESSED(Keyword.intern(null, "blessed")),
	/** Only items known to be cursed. */
	CURSED(Keyword.intern(null, "cursed")),
	/** Only items with known BUC status. */
	KNOW_BUC(Keyword.intern(null, "know-buc")),
	/** Same as {@link HaveOption#KNOW_BUC} + {@link HaveOption#NONCURSED} */
	SAFE_BUC(Keyword.intern(null, "safe-buc")),
	/** Complement of {@link HaveOption#SAFE_BUC}. */
	UNSAFE_BUC(Keyword.intern(null, "unsafe-buc")),
	/** Only items currently worn or wielded. */
	IN_USE(Keyword.intern(null, "in-use")),
	/** Only items currently worn. */
	WORN(Keyword.intern(null, "worn")),
	/**
	 * Only items that are currently in use or are not blocked by any cursed items.
	 * @see ActionsComplex#makeUse(IGame, Character)
	 */
	CAN_USE(Keyword.intern(null, "can-use")),
	/** Complement of {@link HaveOption#CAN_USE}. */
	NO_CAN_USE(Keyword.intern(null, "no-can-use")),
	/**
	 * Only items that are not currently in use or are not blocked by any cursed items.
	 * @see ActionsComplex#removeUse(IGame, Character)
	 */
	CAN_REMOVE(Keyword.intern(null, "can-remove")),
	/** Complement of {@link HaveOption#CAN_REMOVE}. */
	NO_CAN_REMOVE(Keyword.intern(null, "no-can-remove")),
	/**
	 * Also examine containers in main inventory.
	 * If a matching item is not in main inventory(Keyword.intern(null, "inventory")), return the slot of the container and the matching IItem from inside of it (if present).
	 * @see ActionsComplex#unbag(IGame, java.util.Map.Entry)
	 */
	BAGGED(Keyword.intern(null, "bagged"));

	private final Keyword kw;

	private HaveOption(Keyword kw) {
		this.kw = kw;
	}

	public Keyword getKeyword() {
		return kw;
	}
}
