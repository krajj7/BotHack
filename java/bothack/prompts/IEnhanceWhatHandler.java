package bothack.prompts;

import bothack.actions.ActionsComplex;

/** Called when the Enhance skill menu appears with a choice of skill to enhance.
 * @see ActionsComplex#enhanceAll(bothack.bot.IGame) */
public interface IEnhanceWhatHandler {
/** Called when the Enhance skill menu appears with a choice of skill to enhance.
 * @see ActionsComplex#enhanceAll(bothack.bot.IGame) */
	Character enhanceWhat(java.util.Map<Character,String> options);
}
