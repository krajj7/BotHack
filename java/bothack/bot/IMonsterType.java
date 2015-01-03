package bothack.bot;

import java.util.Set;

/** Immutable representation of one of the 375 NetHack monsters. */
public interface IMonsterType extends IAppearance {
	/** Name of the monster type */
	String name();
	/** Difficulty according to NetHack sources */
	Long difficulty();
	/** How often the monster gets a turn - larger is better */
	Long speed();
	/** Armor class - lower is better */
	Long AC();
	/** Magic resistance */
	Long MR();
	Long alignment();
	//List<IMonsterAttack> attacks();
	Long nutrition();
	Set<Intrinsic> resistances();
	/** Intrinsics the player can gain when eating the corpse */
	Set<Intrinsic> conferredResistances();
	/** Does the monster have a poisonous corpse? */
	Boolean isPoisonous();
	Boolean isUnique();
	Boolean hasHands();
	/** Is the monster always hostile when generated?
	 * A peaceful or tame instance of this monster can still exist. */
	Boolean isHostile();
	/** Monsters that will teleport to the upstairs to heal and steal artifacts. */
	Boolean isCovetous();
	Boolean respectsElbereth();
	Boolean seesInvisible();
	Boolean isFollower();
	Boolean isAmphibious();
	Boolean hasDrowningAttack();
	Boolean isFlying();
	Boolean isWerecreature();
	Boolean isMimic();
	Boolean isPriest();
	Boolean isShopkeeper();
	/** Does the monster corrode weapons passively? */
	Boolean isCorrosive();
	/** Does the monster lack active attacks? */
	Boolean isPassive();
	/** Sessile monsters don't move at all. */
	Boolean isSessile();
	/** Killing humans has a penalty for lawfuls, as does cannibalism */
	Boolean isHuman();
	/** Corresponds to a NetHack monster flag */
	Boolean isNasty();
	/** Riders of the apocalypse on Astral */
	Boolean isRider();
	/** Corresponds to a NetHack monster flag */
	Boolean isStrong();
	Boolean isUndead();
	/** True for the minetown watch and vault guards */
	Boolean isGuard();
	/** Mindless monsters can't be seen by telepathy */
	Boolean isMindless();
	Boolean canBeSeenByInfravision();
}
