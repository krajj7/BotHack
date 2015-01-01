package bothack.prompts;

/** Called when the player is close to dying from overeating. */
public interface IStopEatingHandler {
/** Called when the player is close to dying from overeating.
 * False is returned by default.
 * @return True to keep eating (and possibly die) */
	Boolean stopEatingHandler(String prompt);
}
