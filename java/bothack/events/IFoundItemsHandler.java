package bothack.events;

import bothack.bot.IItem;

public interface IFoundItemsHandler {
	void foundItems(java.util.List<IItem> items);
}
