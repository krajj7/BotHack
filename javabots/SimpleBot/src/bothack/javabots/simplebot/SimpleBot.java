package bothack.javabots.simplebot;

import bothack.actions.*;
import bothack.bot.*;
import bothack.prompts.*;

/** A dumb example bot.  Equivalent to simplebot.clj
 * All the bot does is run in a circle until it dies.
 * See the JavaBot project for a slightly more advanced example bot.
 * See {@link https://github.com/krajj7/BotHack/blob/master/doc/running.md}
 * for how to run this. */
public class SimpleBot {
	IBotHack bothack;
	
	class CircleMover implements IActionHandler {
		Direction[] smallCircle = {Direction.N, Direction.E, Direction.S, Direction.W};
		int next = 0;
		
		public IAction chooseAction(IGame game) {
			next = next % smallCircle.length;
			return Actions.Move(smallCircle[next++]);
		}
	}
	
	class PrayForFood implements IActionHandler {
		public IAction chooseAction(IGame game) {
			if (game.player().hunger() == Hunger.FAINTING)
				return Actions.Pray();
			else
				return null;
		}
	}
	
	public SimpleBot(IBotHack bothack) {
		this.bothack = bothack;
		bothack.registerHandler(0, new PrayForFood());
		bothack.registerHandler(1, new CircleMover());
		System.out.println("bot initialized");
	}
}
