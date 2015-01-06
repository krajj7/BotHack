package bothack.bot.items;

import java.util.List;

import clojure.java.api.Clojure;

/** Factory and utility functions for item types. */
public final class ItemType {
	private ItemType() {};

	static {
		Clojure.var("clojure.core", "require").invoke(Clojure.read("bothack.itemtype"));
	}
	
	/** If the name is a recognized NetHack item returns its IItemType.
	 * @see IItem#name() */
	public static IItemType byName(String name) {
		return (IItemType) Clojure.var("bothack.itemtype", "name->item").invoke(name);
	}
	
	/** Return the list of all item types in NetHack. */
	public static List<IItemType> allItems() {
		return (List<IItemType>) Clojure.var("clojure.core", "vec").invoke(Clojure.var("bothack.itemtype", "items"));
	}
}
