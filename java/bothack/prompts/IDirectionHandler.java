package bothack.prompts;

import bothack.bot.Direction;

/**
 * Called when a direction prompt is presented to the player.
 * Most of these prompts should get handled automatically via primitive actions
 * (use ApplyAt instead of Apply, ZapWandAt instead of ZapWand etc.).
 * Escaped by default.
 */
public interface IDirectionHandler {
/**
 * Called when a direction prompt is presented to the player.
 * Most of these prompts should get handled automatically via primitive actions
 * (use ApplyAt instead of Apply, ZapWandAt instead of ZapWand etc.).
 * Escaped by default.
 */
	Direction whatDirection();
}
