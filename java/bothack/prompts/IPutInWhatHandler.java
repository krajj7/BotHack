package bothack.prompts;

public interface IPutInWhatHandler {
	/** Can return either a raw string or java.util.Set<Character> */
	Object putInWhat(java.util.Map<Character,String> options);
}
