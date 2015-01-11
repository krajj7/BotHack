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
 * the bot will likely use.  You can limit the cost by specifying a low maxDistance.
 * When using the IPredicate function variants, efficient implementation of the
 * predicate is crucial.
 * </p><p>
 * By default the functions will automatically make use of safe items like a 
 * pick-axe, ring of levitation, wands and scrolls of teleportation (when stuck).
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
 * Most of these features can be disabled using some {@link NavOption} when using Navigation#navigate.
 * Exploration and seeking will however always use the listed behaviors.
 * </p><p>
 * When seeking/searching, the bot will generally:
 * <li> search dead ends and towards blanks on the map, eventually also corridors (where appropriate)
 * <li> push boulders around
 * <li> attempt to dig downwards or teleport when stuck
 * </p><p>
 * Navigation behaves poorly in unsolved sokoban levels and may get the bot stuck.
 * You should use {@link ActionsComplex#doSokoban(IGame)} in sokoban.
 * </p><p>
 * All level navigation functions automatically choose A* or Dijkstra's algorithm
 * depending on the number of targets.</p>*/
public final class Navigation {
	private Navigation() {};
	
	/** Representation of a navigation result. */
	public interface IPath {
		/** Returns the next action that will lead towards reaching the target. */
		IAction step();
		/** Returns the list of positions that are going to be stepped on along the
		 * way towards target. */
		List<IPosition> path();
		/** Returns the target tile position.
		 * When using {@link NavOption#ADJACENT} this is not the adjacent tile but
		 * the matching target itself. */
		IPosition target();
	}
	
	private static final IFn NAVIGATE = Clojure.var("bothack.pathing", "navigate");
	private static final IFn SEEK = Clojure.var("bothack.pathing", "seek");
	private static final IFn NAVOPTS = Clojure.var("bothack.pathing", "navopts");
	
	/** Makes IPredicate callable natively from clojure (by implementing IFn via AFn) */
	private final class Predicate extends clojure.lang.AFn {
		final IPredicate p;

		Predicate(IPredicate p) {
			this.p = p;
		}
		
		@Override
		public Object invoke(Object x) {
			return p.apply(x);
		}
	}
	
	static {
		IFn require = Clojure.var("clojure.core", "require");
		require.invoke(Clojure.read("bothack.pathing"));
	}

	/** Intralevel navigation. */
	IPath navigate(IGame game, IPosition target, NavOption... opts) {
		return (IPath) NAVIGATE.invoke(game, target, NAVOPTS.invoke(opts));
	}

	IPath navigate(IGame game, IPosition target, Long maxDistance, NavOption... opts) {
		return (IPath) NAVIGATE.invoke(game, target, NAVOPTS.invoke(opts, maxDistance));
	}

	IPath navigate(IGame game, IPredicate<ITile> target, NavOption... opts) {
		return (IPath) NAVIGATE.invoke(game, new Predicate(target), NAVOPTS.invoke(opts));
	}

	IPath navigate(IGame game, IPredicate<ITile> target, Long maxDistance, NavOption... opts) {
		return (IPath) NAVIGATE.invoke(game, new Predicate(target), NAVOPTS.invoke(opts, maxDistance));
	}

	IAction seek(IGame game, IPosition target, Long maxDistance, NavOption... opts) {
		return (IAction) SEEK.invoke(game, target, NAVOPTS.invoke(opts, maxDistance));
	}

	IAction seek(IGame game, IPredicate<ITile> target, Long maxDistance, NavOption... opts) {
		return (IAction) SEEK.invoke(game, new Predicate(target), NAVOPTS.invoke(opts, maxDistance));
	}

	IAction seek(IGame game, IPosition target, NavOption... opts) {
		return (IAction) SEEK.invoke(game, target, NAVOPTS.invoke(opts));
	}

	IAction seek(IGame game, IPredicate<ITile> target, NavOption... opts) {
		return (IAction) SEEK.invoke(game, new Predicate(target), NAVOPTS.invoke(opts));
	}
	
	IAction seekBranch(IGame game, Branch branch) {
		return (IAction) Clojure.var("bothack.pathing", "seek-branch").invoke(game, branch);
	}
	
	IAction seekLevel(IGame game, Branch branch, String dlvl) {
		return (IAction) Clojure.var("bothack.pathing", "seek-level").invoke(game, branch, dlvl);
	}
	
	IAction seekLevel(IGame game, Branch branch, LevelTag tag) {
		return (IAction) Clojure.var("bothack.pathing", "seek-level").invoke(game, branch, tag);
	}

	IAction exploreLevel(IGame game, Branch branch, String dlvl) {
		return (IAction) Clojure.var("bothack.pathing", "explore-level").invoke(game, branch, dlvl);
	}

	IAction exploreLevel(IGame game, Branch branch, LevelTag tag) {
		return (IAction) Clojure.var("bothack.pathing", "explore-level").invoke(game, branch, tag);
	}
	
	IAction exploreCurrentLevel(IGame game) {
		return (IAction) Clojure.var("bothack.pathing", "explore").invoke(game);
	}
	
	IAction searchCurrentLevel(IGame game) {
		return (IAction) Clojure.var("bothack.pathing", "search-level").invoke(game);
	}
	
	IAction visitBranch(IGame game, Branch branch) {
		return (IAction) Clojure.var("bothack.pathing", "visit").invoke(game, branch);
	}
	
	IAction visitLevel(IGame game, Branch branch, String dlvl) {
		return (IAction) Clojure.var("bothack.pathing", "visit").invoke(game, branch, dlvl);
	}

	IAction visitLevel(IGame game, Branch branch, LevelTag tag) {
		return (IAction) Clojure.var("bothack.pathing", "visit").invoke(game, branch, tag);
	}

	/** Return Dlvl of the main branch containing entrance to branch, if static or already visited */
	IAction branchEntrance(IGame game, Branch branch) {
		return (IAction) Clojure.var("bothack.pathing", "branch-entry").invoke(game, branch);
	}
	
	/** 
	 * Intralevel navigation to the nearest matching tile.
	 * Will not traverse the castle or medusa's if the player is lacking safe levitation source,
	 * otherwise assumes all levels are passable and unit distance.  Only runs tile-based navigation on the current level.
	 * @param maxDelta limits the number of traversed levels
	 */
	IAction seekInterlevel(IGame game, IPredicate<ITile> target, Long maxDelta) {
		return (IAction) Clojure.var("bothack.pathing", "seek-tile").invoke(game, new Predicate(target), NAVOPTS.invoke(new NavOption[] {NavOption.UP}, maxDelta));
	}
	
	/** 
	 * Interlevel navigation to the nearest matching tile.
	 * Will not traverse the castle or medusa's if the player is lacking a safe levitation source.
	 * Otherwise assumes all levels are passable and unit distance.  
	 * Only runs tile-based navigation on the current level.
	 */
	IAction seekInterlevel(IGame game, IPredicate<ITile> target) {
		return (IAction) Clojure.var("bothack.pathing", "seek-tile").invoke(game, new Predicate(target));
	}

	/**
	 * Like {@link Navigation#seekInterlevel(IGame, IPredicate)} but will only change levels
	 * upwards and avoid subbranches.
	 */
	IAction seekInterlevelUpwards(IGame game, IPredicate<ITile> target) {
		return (IAction) Clojure.var("bothack.pathing", "seek-tile").invoke(game, new Predicate(target), Clojure.read("#{:up}"));
	}

	/**
	 * Like {@link Navigation#seekInterlevel(IGame, IPredicate)} but will only change levels
	 * upwards and avoid subbranches.
	 * @param maxDelta limits the number of traversed levels
	 */
	IAction seekInterlevelUpwards(IGame game, IPredicate<ITile> target, Long maxDelta) {
		return (IAction) Clojure.var("bothack.pathing", "seek-tile").invoke(game, new Predicate(target), Clojure.read("{:up true :max-delta "+maxDelta+"}"));
	}

	/** Returns the intended path of the last performed action – if it was
	 * generated by some of the methods of this class. */
	List<IPosition> lastPath(IGame game) {
		return (List<IPosition>) clojure.lang.Keyword.intern(null, ":last-path").invoke(game);
	}

	/** Returns true if the player is just about to enter a shop and should not pick up pickaxes. */
	Boolean isEnteringShop(IGame game) {
		return Clojure.var("bothack.pathing", "entering-shop?").invoke(game) != null;
	}
}