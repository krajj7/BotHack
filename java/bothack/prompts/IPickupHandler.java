package bothack.prompts;

public interface IPickupHandler {
	/** Can return either a raw string or java.util.Set<Character> */
	Object pickUpWhat(java.util.Map<Character,String> options);
}
