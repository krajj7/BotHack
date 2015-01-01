package bothack.actions;

import java.util.List;

import bothack.bot.Direction;
import bothack.bot.IGame;
import bothack.bot.IItem;
import bothack.bot.IPlayer;
import bothack.bot.ITile;
import clojure.java.api.Clojure;

/**
 * Factories for primitive BotHack actions.
 * 
 * <p>When using these actions you should first make sure the action can actually
 * be performed in the current context before returning it in an ActionHandler.
 * A bot can get stuck easily for example by attempting to wield something
 * repeatedly when it has no hands since trying it takes no game turns.</p>
 * 
 * <p>Many actions have a "smarter" variant in ActionsComplex that also checks
 * preconditions.</p>
 * 
 * @see ActionsComplex 
 */
public final class Actions {
	static {
		Clojure.var("clojure.core", "require").invoke(Clojure.read("bothack.actions"));
	}
	
	/** 
	 * Force attack in the given direction (NetHack 'F' command).
	 * Note that you can't attack when overtaxed or worse.
	 * @see IPlayer#isOvertaxed() 
	 */
	public static IAction Attack(Direction dir) {
		return (IAction) Clojure.var("bothack.actions", "->Attack").invoke(dir);
	}
	
	/**
	 *  Move or attack in the given direction.
	 * Note that you can't move when overloaded.
	 * @see IPlayer#isOverloaded()
	 *  */
	public static IAction Move(Direction dir) {
		return (IAction) Clojure.var("bothack.actions", "->Move").invoke(dir);
	}

	/**
	 * Search the around the current tile once.
	 * @see Actions#Search(Long)
	 */
	public static IAction Search() {
		return (IAction) Clojure.var("bothack.actions", "->Search").invoke();
	}
	
	/** Shorthand for searching multiple times */
	public static IAction Search(Long num) {
		return (IAction) Clojure.var("bothack.actions", "search").invoke(num);
	}

	/** Do nothing in the next turn.  You might as well Search. */
	public static IAction Wait() {
		return (IAction) Clojure.var("bothack.actions", "->Wait").invoke();
	}

	/** 
	 * Go up the stairs or climb a ladder.
	 * Note that you can't do this when stressed or worse.
	 * @see IPlayer#isStressed()
	 * @see ITile#isUpstairs()
	 */
	public static IAction Ascend() {
		return (IAction) Clojure.var("bothack.actions", "->Ascend").invoke();
	}

	/** 
	 * Go down the stairs or a ladder. 
	 * @see ITile#isDownstairs()
	 * @see ActionsComplex#descend(IGame)
	 */
	public static IAction Descend() {
		return (IAction) Clojure.var("bothack.actions", "->Descend").invoke();
	}

	/** 
	 * Kick in the given direction.
	 * Note that you can't do this with hurt legs, when trapped or when stressed or worse.
	 * @see ActionsComplex#kick(IGame, Direction)
	 * @see IPlayer#isStressed()
	 * @see IPlayer#hasHurtLegs() 
	 */
	public static IAction Kick(Direction dir) {
		return (IAction) Clojure.var("bothack.actions", "->Kick").invoke(dir);
	}
	
	/** Close a door */
	public static IAction Close(Direction dir) {
		return (IAction) Clojure.var("bothack.actions", "->Close").invoke(dir);
	}
	
	/** Open a door */
	public static IAction Open(Direction dir) {
		return (IAction) Clojure.var("bothack.actions", "->Open").invoke(dir);
	}
	
	/**
	 * #pray action.
	 * @see IGame#canPray()
	 * @see ActionsComplex#pray(IGame)
	 */
	public static IAction Pray() {
		return (IAction) Clojure.var("bothack.actions", "->Pray").invoke();
	}

	/**
	 * Engrave action appending to the current engraving (if prompted).
	 * @param slot Inventory slot to engrave with.  Use '-' to engrave with fingers.
	 * @param engraving String to engrave
	 * @see IGame#canEngrave()
	 * @see ITile#isEngravable()
	 */
	public static IAction EngraveOverwriting(Character slot, String engraving) {
		return (IAction) Clojure.var("bothack.actions", "->Engrave").invoke(slot, engraving, false);
	}

	/**
	 * Engrave action overwriting the current engraving (if prompted).
	 * @param slot Inventory slot to engrave with.  Use '-' to engrave with fingers.
	 * @param engraving String to engrave
	 * @see IGame#canEngrave()
	 * @see ITile#isEngravable()
	 */
	public static IAction EngraveAppending(Character slot, String engraving) {
		return (IAction) Clojure.var("bothack.actions", "->Engrave").invoke(slot, engraving, true);
	}
	

	/**
	 * Apply item at given inventory slot.  For handling containers and direction prompts use TakeOut, PutIn or ApplyAt.
	 * @see IPlayer#hasHands()
	 * @see Actions#TakeOut
	 * @see Actions#PutIn
	 * @see Actions#ApplyAt(Character, Direction)
	 * @see ActionsComplex#unbag(IGame, java.util.Map.Entry)
	 */
	public static IAction Apply(Character slot) {
		return (IAction) Clojure.var("bothack.actions", "->Apply").invoke(slot);
	}

	/**
	 * Take out all of item with label itemLabel from container at bagSlot.
	 * @param bagSlot can be '.' for looting containers on the ground.
	 * @see ActionsComplex#unbag(IGame, java.util.Map.Entry)
	 */
	public static IAction TakeOut(Character bagSlot, String itemLabel) {
		return (IAction) Clojure.var("bothack.actions", "take-out").invoke(bagSlot, itemLabel);
	}
	
	/**
	 * Take out amount of item with label itemLabel from container at bagSlot.
	 * @param bagSlot can be '.' for looting containers on the ground.
	 * @see ActionsComplex#unbag(IGame, java.util.Map.Entry)
	 */
	public static IAction TakeOut(Character bagSlot, String itemLabel, Long amount) {
		return (IAction) Clojure.var("bothack.actions", "take-out").invoke(bagSlot, itemLabel, amount);
	}
	
	/** Put amount of item at itemSlot into the container at bagSlot. */
	public static IAction PutIn(Character bagSlot, Character itemSlot, Long amount) {
		return (IAction) Clojure.var("bothack.actions", "put-in").invoke(bagSlot, itemSlot, amount);
	}
	
	/** Put all of item at itemSlot into the container at bagSlot. */
	public static IAction PutIn(Character bagSlot, Character itemSlot) {
		return (IAction) Clojure.var("bothack.actions", "put-in").invoke(bagSlot, itemSlot);
	}

	/** Apply the item in the given direction (if prompted).  For use with a key, a pickaxe...
	 * With a pickaxe wielding it explicitly first is strongly recommended (anything can happen during the wielding). */
	public static IAction ApplyAt(Character slot, Direction dir) {
		return (IAction) Clojure.var("bothack.actions", "->ApplyAt").invoke(slot, dir);
	}
	
	/** Use current weapon to force a lock on a container. */
	public static IAction ForceLock() {
		return (IAction) Clojure.var("bothack.actions", "->ForceLock").invoke();
	}
	
	/** Use key, lockpick or credit card at slot to unlock a door. */
	public static IAction Unlock(Character slot, Direction dir) {
		return (IAction) Clojure.var("bothack.actions", "->Unlock").invoke(slot, dir);
	}
	
	/**
	 * Wield item at slot as a weapon. 
	 * @see ActionsComplex#wield(IGame, Character)
	 */
	public static IAction Wield(Character slot) {
		return (IAction) Clojure.var("bothack.actions", "->Wield").invoke(slot);
	}
	
	/**
	 * Wear item at slot (for armor).
	 * @see ActionsComplex#makeUse(IGame, Character)
	 */
	public static IAction Wear(Character slot) {
		return (IAction) Clojure.var("bothack.actions", "->Wear").invoke(slot);
	}
	
	/**
	 * Put on item at slot (for jewelry).
	 * @see ActionsComplex#makeUse(IGame, Character)
	 */
	public static IAction PutOn(Character slot) {
		return (IAction) Clojure.var("bothack.actions", "->PutOn").invoke(slot);
	}
	
	/**
	 * Take off item at slot (for armor).
	 * @see ActionsComplex#removeUse(IGame, Character)
	 */
	public static IAction TakeOff(Character slot) {
		return (IAction) Clojure.var("bothack.actions", "->TakeOff").invoke(slot);
	}
	
	/**
	 * Remove item at slot (for jewelry).
	 * @see ActionsComplex#removeUse(IGame, Character)
	 */
	public static IAction Remove(Character slot) {
		return (IAction) Clojure.var("bothack.actions", "->Remove").invoke(slot);
	}
	
	/** Drop one of item at slot. */
	public static IAction Drop(Character slot) {
		return (IAction) Clojure.var("bothack.actions", "->Drop").invoke(slot);
	}
	
	/** 
	 * Drop a specific amount of item at slot.
	 * @see IItem#quantity() 
	 */
	public static IAction Drop(Character slot, Long num) {
		return (IAction) Clojure.var("bothack.actions", "->Drop").invoke(slot, num);
	}
	
	/** 
	 * Pick up all items in the list by label.
	 * It is currently not possible to pick up specific amounts (but you can
	 * drop any amount after pick up).
	 * @see IItem#label() 
	 */
	public static IAction PickUp(List<String> labels) {
		return (IAction) Clojure.var("bothack.actions", "->PickUp").invoke(labels);
	}
	
	/** 
	 * Pick up all of item by label.
	 * It is currently not possible to pick up specific amounts (but you can
	 * drop any amount after pick up).
	 * @see IItem#label()
	 */
	public static IAction PickUp(String label) {
		return (IAction) Clojure.var("bothack.actions", "->PickUp").invoke(label);
	}

	/** 
	 * Enhance specific skills.  IEnhanceWhatHandler should be registered
	 * beforehand (or use EnhanceAll).
	 * @see bothack.prompts.IEnhanceWhatHandler
	 * @see IPlayer#canEnhance()
	 * @see ActionsComplex#enhanceAll(IGame)
	 */
	public static IAction Enhance() {
		return (IAction) Clojure.var("bothack.actions", "->Enhance").invoke();
	}
	
	/** Read scrolls or spellbooks. */
	public static IAction Read(Character slot) {
		return (IAction) Clojure.var("bothack.actions", "->Read").invoke(slot );
	}
	
	/** 
	 * Sit down (only useful on thrones). 
	 * @see ITile#isThrone() 
	 */
	public static IAction Sit() {
		return (IAction) Clojure.var("bothack.actions", "->Sit").invoke();
	}
	
	/** Eat item at slot. */
	public static IAction Eat(Character slot) {
		return (IAction) Clojure.var("bothack.actions", "->Eat").invoke(slot);
	}
	
	/** Eat item from the ground by label. */
	public static IAction Eat(String label) {
		return (IAction) Clojure.var("bothack.actions", "->Eat").invoke(label);
	}
	
	/** Drink a potion at slot. */
	public static IAction Quaff(Character slot) {
		return (IAction) Clojure.var("bothack.actions", "->Quaff").invoke(slot);
	}
	
	/** 
	 * Offer item at slot as sacrifice.
	 * @see ITile#isAltar()
	 */
	public static IAction Offer(Character slot) {
		return (IAction) Clojure.var("bothack.actions", "->Offer").invoke(slot);
	}
	
	/**
	 * Offer item from the ground by label. 
	 * @see ITile#isAltar()
	 */
	public static IAction Offer(String label) {
		return (IAction) Clojure.var("bothack.actions", "->Offer").invoke(label);
	}
	
	/** Dip item at itemSlot into potion at potionSlot (or a fountain if it is '.') */
	public static IAction Dip(Character itemSlot, Character potionSlot) {
		return (IAction) Clojure.var("bothack.actions", "->Dip").invoke(itemSlot, potionSlot);
	}
	
	/** Throw item at slot in direction dir. */
	public static IAction Throw(Character slot, Direction dir) {
		return (IAction) Clojure.var("bothack.actions", "->Throw").invoke(slot, dir);
	}
	
	/**
	 * Wipe own face. 
	 * @see IPlayer#isBlindExternally()
	 * @see IPlayer#hasHands()
	 */
	public static IAction Wipe() {
		return (IAction) Clojure.var("bothack.actions", "->Wipe").invoke();
	}
	
	/** 
	 * Zap a known non-directional wand. 
	 * @see Actions#ZapWandAt(Character, Direction)
	 */
	public static IAction ZapWand(Character slot) {
		return (IAction) Clojure.var("bothack.actions", "->ZapWand").invoke();
	}
	
	/** Zap a wand in the given direction (if prompted for a direction). */
	public static IAction ZapWandAt(Character slot, Direction dir) {
		return (IAction) Clojure.var("bothack.actions", "->ZapWandAt").invoke(dir);
	}

	/** Rub a lamp at slot. */
	public static IAction Rub(Character slot) {
		return (IAction) Clojure.var("bothack.actions", "->Rub").invoke();
	}

	/**
	 * Chat with monster at direction dir.
	 * @see Actions#Contribute(Direction, Long)
	 */
	public static IAction Chat(Direction dir) {
		return (IAction) Clojure.var("bothack.actions", "->Chat").invoke(dir);
	}

	/**
	 * Offer a donation to a priest at dir.
	 * @param amount Amount of gold to donate 
	 */
	public static IAction Contribute(Direction dir, Long amount) {
		return (IAction) Clojure.var("bothack.actions", "->Contribute").invoke(dir, amount);
	}
}