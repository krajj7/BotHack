package bothack.prompts;

/** Called when a succubus or an incubus makes a request. */
public interface ISeducedHandler {
/** Called when a succubus or an incubus makes a request to put on a ring.
 * False is returned by default. 
 * @return True to put the ring on.*/
	Boolean seducedPuton();
/** Called when a succubus or an incubus makes a request to remove your clothing.
 * False is returned by default. 
 * @return True to strip.*/
	Boolean seducedRemove();
}
