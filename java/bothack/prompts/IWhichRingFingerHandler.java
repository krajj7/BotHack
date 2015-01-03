package bothack.prompts;

/** Called when you can choose which hand to put a ring on. */
public interface IWhichRingFingerHandler {
/** Called when you can choose which hand to put a ring on. 
 * The left hand is chosen by default.
 * @return 'r' or 'l' */
	Character whichFinger(String prompt);
}
