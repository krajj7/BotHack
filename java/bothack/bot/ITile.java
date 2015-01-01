package bothack.bot;

public interface ITile extends IPosition {
	/** True if it is possible to engrave on this tile by some means. */
	Boolean isEngravable();
	/** True if the tile is a throne. */
	Boolean isThrone();
	/** True if the tile is an altar. */
	Boolean isAltar();
	/** True if the tile is a staircase up or a ladder up. */
	Boolean isUpstairs();
	/** True if the tile is a staircase down or a ladder down. */
	Boolean isDownstairs();
}
