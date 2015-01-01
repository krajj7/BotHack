package bothack.events;

public interface IBOTLHandler {
	/** Invoked when the bottom two status lines are drawn and parsed.
	 * Informations from these are propagated to the game representation by the framework. */
	void botl(java.util.Map<clojure.lang.Keyword, Object> status);
}
