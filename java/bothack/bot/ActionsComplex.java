package bothack.bot;

import java.util.Map;
import clojure.java.api.Clojure;
import clojure.lang.IFn;

/** 
 * Factories for "smarter" actions with precondition checks and more complex behaviors.
 * @see Actions 
 */
public final class ActionsComplex {
	static {
		IFn require = Clojure.var("clojure.core", "require");
		require.invoke(Clojure.read("bothack.actions"));
		require.invoke(Clojure.read("bothack.behaviors"));
	}

	/**
	 * Pray if it is likely to succeed, otherwise returns null.
	 * @see IGame#canPray()
	 */
	public static IAction Pray(IGame game) {
		return (IAction) Clojure.var("bothack.behaviors", "pray").invoke(game);
	}
	
	/**
	 * Kick in the given direction if the player is capable of kicking
	 * and there don't seem to be heavy objects.
	 * Auto-removes levitation items, if the player has hurt legs does Search
	 * instead, if trapped moves out of the trap.
	 */
	public static IAction Kick(IGame game, Direction dir) {
		return (IAction) Clojure.var("bothack.actions", "kick").invoke(game, dir.getKeyword());
	}
	
	/**
	 * If the slot of the item MapEntry is a bag, take one of item out of the bag, otherwise returns null.
	 * Meant for use with entries returned by IGame#have or IGame#haveAll with the BAGGED option */
	public static IAction Unbag(IGame game, Map.Entry<Character, IItem> item) {
		return (IAction) Clojure.var("bothack.actions", "unbag").invoke(game, item.getKey(), item.getValue());
	}
	
	/** If it is possible for the player, wield item at slot as a weapon, otherwise returns null.
	 * Removes any shield if the weapon is two-handed. */
	public static IAction Wield(IGame game, Character slot) {
		return (IAction) Clojure.var("bothack.actions", "wield").invoke(game, slot);
	}
	
	/** If it is possible for the player, use the item at slot in the usual way:
	 * This means wearing armor (removing any blocking armor) or putting on jewelry. */
	public static IAction MakeUse(IGame game, Character slot) {
		return (IAction) Clojure.var("bothack.actions", "make-use").invoke(game, slot);
	}
	
	/** If it is possible for the player, stop using the item at slot.
	 * This means removing armor (with blockers), unwielding a weapon or
	 * removing jewelry or accessories. */
	public static IAction RemoveUse(IGame game, Character slot) {
		return (IAction) Clojure.var("bothack.actions", "remove-use").invoke(game, slot);
	}

	/** Enhance any skill that can be enhanced or return null. */
	public static IAction EnhanceAll(IGame game) {
		return (IAction) Clojure.var("bothack.behaviors", "enhance").invoke(game);
	}
	
	/** Remove levitation item or Descend.  Returns null if levitation cannot be removed. */
	public static IAction Descend(IGame game) {
		return (IAction) Clojure.var("bothack.actions", "descend").invoke(game);
	}

	/** Move in any direction to escape a trap. */
	public static IAction UntrapMove(IGame game) {
		return (IAction) Clojure.var("bothack.actions", "untrap-move").invoke(game);
	}
	
	/** 
	 * Action modifier – if the player is levitating, replaces the given action
	 * with removal of the levitation item, otherwise returns the action unmodified.
	 * If removal of levitation is not possible, returns null. 
	 * If the action is null, returns null.
	 * Doesn't handle intrinsic levitation (eg. from a potion)
	 * @see IGame#haveLevitationItemOn()
	 */
	public static IAction WithoutLevitation(IGame game, IAction action) {
		return (IAction) Clojure.var("bothack.actions", "without-levitation").invoke(game, action);
	}

	/** 
	 * Action modifier – attach a human-understandable reason to the given action.
	 * This will be seen in the logs when the action is triggered and may help with debugging.
	 * If the action is null, returns null.
	 * Reasons are stacked, all reasons will be logged when used repeatedly.
	 * @param reason Human readable text that will be logged when the action triggers
	 */
	public static IAction WithReason(IAction action, String reason) {
		return (IAction) Clojure.var("bothack.actions", "with-reason").invoke(reason, action);
	}
}