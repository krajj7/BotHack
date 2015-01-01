package bothack.events;

import bothack.bot.IItem;

/** Called every time the player encounters items on his current tile and in containers on the tile. */
public interface IFoundItemsHandler {
/** Called every time the player encounters items on his current tile and in containers on the tile. */
	void foundItems(java.util.List<IItem> items);
}
