package bothack.actions;

import java.util.List;

import bothack.bot.IGame;
import bothack.bot.IPosition;
import bothack.bot.IPredicate;
import bothack.bot.dungeon.Branch;
import bothack.bot.dungeon.ITile;
import bothack.bot.dungeon.LevelTag;
import clojure.java.api.Clojure;
import clojure.lang.IFn;

/** 
 * Utility functions for navigation and dungeon exploration.
 * <p>Note that navigation is one of the most computationally expensive functions
 * the bot will likely use.  You can limit the search by specifying a low maxSteps.
 * When using the IPredicate function variants, efficient implementation of the
 * predicate is crucial.
 * </p><p>
 * By default the functions will automatically make use of safe items like a 
 * pick-axe or rings of levitation.
 * Furthermore:
 * <ul>
 * <li> shorter paths will be dug through walls and rock when possible
 * <li> a pick-axe will be dropped when entering a shop (bots should not try to pick it back up – use {@link Navigation#isEnteringShop(IGame)})
 * <li> the Pay command will be tried automatically when leaving a shop
 * <li> locked doors will be unlocked or kicked down when possible
 * <li> blocked doors may be kicked down
 * <li> traps will be escaped automatically, bot may wait for legs to heal
 * <li> levitation items will be used to cross water, ice, lava, trapdoors and holes, also on the planes of air and water
 * </ul>
 * Most of these features can be disabled using some {@link NavOption} when using {@link Navigation#navigate}.
 * Exploration and seeking will however always use the listed behaviors.
 * </p><p>
 * When seeking/searching, the bot will generally:
 * <ul>
 * <li> search dead ends and towards blanks on the map, eventually also corridors (where appropriate)
 * <li> push boulders around
 * <li> attempt to dig downwards or teleport when stuck
 * </ul>
 * </p><p>
 * Seaching in sokoban levels is not recommended and may get the bot stuck.
 * You should use {@link ActionsComplex#doSokoban(IGame)} in sokoban.
 * </p><p>
 * All level navigation functions automatically choose A* or Dijkstra's algorithm
 * depending on the number of targets.
 * The step cost function currently can't be influenced by the user.
 * </p>
 */
public final class Navigation {
	private Navigation() {};
	
	/** Representation of a navigation result. */
	public interface IPath {
		/** The next action that will lead towards reaching the target. */
		IAction step();
		/** The list of positions that are going to be stepped on along the
		 * way towards target. */
		List<IPosition> path();
		/** The target tile position.
		 * When using {@link NavOption#ADJACENT} this is not the adjacent tile but
		 * the matching target itself. */
		IPosition target();
	}
	
	private static final IFn NAVIGATE = Clojure.var("bothack.pathing", "navigate");
	private static final IFn SEEK = Clojure.var("bothack.pathing", "seek");
	private static final IFn NAVOPTS = Clojure.var("bothack.pathing", "navopts");
	
	static {
		IFn require = Clojure.var("clojure.core", "require");
		require.invoke(Clojure.read("bothack.pathing"));
	}

	/**
	 * Returns the shortest path to the given target and an action to perform to move along it.
	 * Returns null if the target is not reachable.
	 * @param opts Additional modifiers
	 */
	public static IPath navigate(IGame game, IPosition target, NavOption... opts) {
		return (IPath) NAVIGATE.invoke(game, target, NAVOPTS.invoke(opts));
	}

	/**
	 * Returns the shortest path to the given target and an action to perform to move along it.
	 * Returns null if the target is not reachable.
	 * @param maxSteps Maximum number of steps
	 * @param opts Additional modifiers
	 */
	public static IPath navigate(IGame game, IPosition target, long maxSteps, NavOption... opts) {
		return (IPath) NAVIGATE.invoke(game, target, NAVOPTS.invoke(opts, maxSteps));
	}

	/**
	 * Returns the shortest path to a matching target and an action to perform to move along it.
	 * Returns null if no target is reachable.
	 * @param opts Additional modifiers
	 */
	public static IPath navigate(IGame game, IPredicate<ITile> target, NavOption... opts) {
		return (IPath) NAVIGATE.invoke(game, new Predicate(target), NAVOPTS.invoke(opts));
	}

	/**
	 * Returns the shortest path to a matching target and an action to perform to move along it.
	 * Returns null if no target is reachable.
	 * @param maxSteps Maximum number of steps
	 * @param opts Additional modifiers
	 */
	public static IPath navigate(IGame game, IPredicate<ITile> target, long maxSteps, NavOption... opts) {
		return (IPath) NAVIGATE.invoke(game, new Predicate(target), NAVOPTS.invoke(opts, maxSteps));
	}

	/**
	 * Returns an action to look for the specified tile on the current level or
	 * null if already standing at a matching tile.  You should only use this to
	 * look for tiles that you <i>know</i> are present on the current level and
	 * reachable, otherwise if you loop this the bot will get stuck searching endlessly.
	 * @see Navigation#navigate(IGame, IPosition, NavOption...)
	 */
	public static IAction seek(IGame game, IPosition target) {
		return (IAction) SEEK.invoke(game, target);
	}

	/**
	 * Returns an action to look for the specified tile on the current level or
	 * null if already standing at a matching tile.  You should only use this to
	 * look for tiles that you <i>know</i> are present on the current level and
	 * reachable, otherwise if you loop this the bot will get stuck searching endlessly.
	 * @see Navigation#navigate(IGame, IPosition, NavOption...)
	 */
	public static IAction seek(IGame game, IPredicate<ITile> target) {
		return (IAction) SEEK.invoke(game, new Predicate(target));
	}
	
	/** Returns an action to look for the specified branch or null if already there. */
	public static IAction seekBranch(IGame game, Branch branch) {
		return (IAction) Clojure.var("bothack.pathing", "seek-branch").invoke(game, branch.getKeyword());
	}
	
	/** Returns an action to go to the specified level or null if already there. */
	public static IAction seekLevel(IGame game, Branch branch, String dlvl) {
		return (IAction) Clojure.var("bothack.pathing", "seek-level").invoke(game, branch.getKeyword(), dlvl);
	}
	
	/** Returns an action to go to the specified level or null if already there. */
	public static IAction seekLevel(IGame game, Branch branch, LevelTag tag) {
		return (IAction) Clojure.var("bothack.pathing", "seek-level").invoke(game, branch.getKeyword(), tag.getKeyword());
	}

	/** Unless the specified level already seems fully explored, returns an
	 * action to go to that level or explore it. */
	public static IAction exploreLevel(IGame game, Branch branch, String dlvl) {
		return (IAction) Clojure.var("bothack.pathing", "explore-level").invoke(game, branch.getKeyword(), dlvl);
	}

	/** Unless the specified level already seems fully explored, returns an
	 * action to go to that level or explore it. */
	public static IAction exploreLevel(IGame game, Branch branch, LevelTag tag) {
		return (IAction) Clojure.var("bothack.pathing", "explore-level").invoke(game, branch.getKeyword(), tag.getKeyword());
	}
	
	/**
	 * Returns an action to explore the current level and items or null if it seems fully explored.
	 * @see Navigation#searchCurrentLevel(IGame)
	 */
	public static IAction exploreCurrentLevel(IGame game) {
		return (IAction) Clojure.var("bothack.pathing", "explore").invoke(game);
	}

	/** Returns an action to explore the dungeon up to and including the specified level. */
	public static IAction explore(IGame game, Branch branch, LevelTag tag) {
		return (IAction) Clojure.var("bothack.pathing", "explore").invoke(game, branch.getKeyword(), tag.getKeyword());
	}

	/** Returns an action to explore the dungeon up to and including end of the given branch. */
	public static IAction explore(IGame game, Branch branch) {
		return (IAction) Clojure.var("bothack.pathing", "explore").invoke(game, branch.getKeyword());
	}
	
	/**
	 * Returns an action to explore or search the current level repeatedly.
 * <ul>
 * <li> search dead ends and towards blanks on the map, eventually also corridors (where appropriate)
 * <li> push boulders around
 * <li> attempt to dig downwards or teleport when stuck
 * </ul>
 * If all tiles of the level become extremely thoroughly searched an IllegalStateException will
 * be thrown as the bot is likely stuck.
	 */
	public static IAction searchCurrentLevelRepeatedly(IGame game) {
		return (IAction) Clojure.var("bothack.pathing", "search-level").invoke(game);
	}

	/**
	 * Returns an action to explore or search the current level or null if the first
	 * round of searching is already done.
 * <ul>
 * <li> search dead ends and towards blanks on the map, eventually also corridors (where appropriate)
 * <li> push boulders around
 * <li> attempt to dig downwards or teleport when stuck
 * </ul>
 * @see Navigation#searchCurrentLevelRepeatedly(IGame)
	 */
	public static IAction searchCurrentLevel(IGame game) {
		return (IAction) Clojure.var("bothack.pathing", "search-level").invoke(game, 1L);
	}
	
	/** If the specified branch has never been visited, returns the action to navigate to it
	 * and possibly explore it until identified. */
	public static IAction visitBranch(IGame game, Branch branch) {
		return (IAction) Clojure.var("bothack.pathing", "visit").invoke(game, branch.getKeyword());
	}
	
	/** If the specified level has never been visited, returns the action to navigate to it. */
	public static IAction visitLevel(IGame game, Branch branch, String dlvl) {
		return (IAction) Clojure.var("bothack.pathing", "visit").invoke(game, branch.getKeyword(), dlvl);
	}

	/** If the specified level has never been visited, returns the action to navigate to it. */
	public static IAction visitLevel(IGame game, Branch branch, LevelTag tag) {
		return (IAction) Clojure.var("bothack.pathing", "visit").invoke(game, branch.getKeyword(), tag.getKeyword());
	}

	/** Return Dlvl of {@link Branch#MAIN} containing the entrance to branch, if static or already visited */
	public static IAction branchEntrance(IGame game, Branch branch) {
		return (IAction) Clojure.var("bothack.pathing", "branch-entry").invoke(game, branch.getKeyword());
	}
	
	/** 
	 * Intralevel navigation to the nearest matching tile.
	 * Will not traverse the castle or medusa's if the player is lacking safe levitation source,
	 * otherwise assumes all levels are passable and unit distance.  Only runs tile-based navigation on the current level.
	 * @param maxDelta limits the number of traversed levels
	 */
	public static IAction seekInterlevel(IGame game, IPredicate<ITile> target, long maxDelta) {
		return (IAction) Clojure.var("bothack.pathing", "seek-tile").invoke(game, new Predicate(target), NAVOPTS.invoke(new NavOption[] {}, maxDelta));
	}
	
	/** 
	 * Interlevel navigation to the nearest matching tile.
	 * Will not attempt to traverse the castle or medusa's if the player is lacking a safe levitation source.
	 * Otherwise assumes all levels are passable and uniform distance (cost).  
	 * Only runs tile-based navigation on the current level.
	 */
	public static IAction seekInterlevel(IGame game, IPredicate<ITile> target) {
		return (IAction) Clojure.var("bothack.pathing", "seek-tile").invoke(game, new Predicate(target));
	}

	/**
	 * Like {@link Navigation#seekInterlevel(IGame, IPredicate)} but will only change levels
	 * upwards and avoid subbranches.
	 */
	public static IAction seekInterlevelUpwards(IGame game, IPredicate<ITile> target) {
		return (IAction) Clojure.var("bothack.pathing", "seek-tile").invoke(game, new Predicate(target), NAVOPTS.invoke(new NavOption[] {NavOption.UP}));
	}

	/**
	 * Like {@link Navigation#seekInterlevel(IGame, IPredicate)} but will only change levels
	 * upwards and avoid subbranches.
	 * @param maxDelta limits the number of traversed levels
	 */
	public static IAction seekInterlevelUpwards(IGame game, IPredicate<ITile> target, long maxDelta) {
		return (IAction) Clojure.var("bothack.pathing", "seek-tile").invoke(game, new Predicate(target), NAVOPTS.invoke(new NavOption[] {NavOption.UP}, maxDelta));
	}

	/** Returns the intended path of the last performed action – if it was
	 * generated by some of the methods of this class. */
	public static List<IPosition> lastPath(IGame game) {
		return (List<IPosition>) clojure.lang.Keyword.intern(null, "last-path").invoke(game);
	}

	/** Returns true if the player is just about to enter a shop and should not pick up pickaxes. */
	public static Boolean isEnteringShop(IGame game) {
		return Clojure.var("bothack.pathing", "entering-shop?").invoke(game) != null;
	}
}