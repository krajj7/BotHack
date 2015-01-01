package bothack.prompts;

/** Called when the Enhance skill menu appears with a choice of skill to enhance.
 * @see ActionsComplex#EnhanceAll(bothack.bot.IGame) */
public interface IEnhanceWhatHandler {
/** Called when the Enhance skill menu appears with a choice of skill to enhance.
 * @see ActionsComplex#EnhanceAll(bothack.bot.IGame) */
	Character enhanceWhat(java.util.Map<Character,String> options);
}
