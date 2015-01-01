package bothack.prompts;

public interface ITakeOutWhatHandler {
	/** Can return either a raw string or java.util.Set<Character> */
	Object takeOutWhat(java.util.Map<Character,String> options);
}
