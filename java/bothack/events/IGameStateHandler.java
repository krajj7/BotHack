package bothack.events;

/** Notification of game start or end. */
public interface IGameStateHandler {
	/** Called when the game starts. */
	void started();
	/** Called when the game ends. */
	void ended();
}
