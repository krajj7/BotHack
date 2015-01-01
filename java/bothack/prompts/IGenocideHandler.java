package bothack.prompts;

import bothack.bot.IGame;

/** Called when the player can choose monsters to genocide. */
public interface IGenocideHandler {
/**
 * Called when the player can choose a class of monsters to genocide.
 * Nothing is genocided by default. 
 * @param prompt The prompt text
 * @return A character representing the class to genocide
 * @see IGame#genocided() */
	Character genocideClass(String prompt);
/** 
 * Called when the player can choose a single monster type to genocide.
 * Nothing is genocided by default.
 * @param prompt The prompt text
 * @return The name of the monster type to genocide
 * @see IGame#genocided()
 */
	String genocideMonster(String prompt);
}
