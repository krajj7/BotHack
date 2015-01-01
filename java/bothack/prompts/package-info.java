/** 
 * Prompts from the game that the bot may have to respond to.
 * 
 * <p>Prompt handlers are invoked on registered handlers in priority order until
 * one of them returns a non-null value.  This value is used as the response and
 * no more handlers are invoked.  The framework may handle some prompts
 * automatically via actions before they reach bot handlers.</p>
 * 
 * <p>There are many more types of prompts that the framework always handles
 * automatically via actions or default handlers.  These are undocumented but
 * available in the package bothack.internal.</p>
 * 
 * @see bothack.bot.IBotHack#registerHandler(Integer, Object)
 */
package bothack.prompts;