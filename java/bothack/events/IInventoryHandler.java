package bothack.events;

import bothack.bot.IItem;

public interface IInventoryHandler {
	void inventoryList(java.util.Map<Character,IItem> inventory);
}
