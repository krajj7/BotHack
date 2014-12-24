package bothack.javabots;

import bothack.bot.*;

/** A dumb example bot.  Equivalent to simplebot.clj */
public class JavaBot {
	IBotHack bothack;
	
	class CircleMover implements IActionHandler {
		Direction[] smallCircle = {Direction.N, Direction.E, Direction.S, Direction.W};
		int next = 0;
		
		@Override
		public IAction chooseAction(IGame game) {
			next = next % smallCircle.length;
			//return Actions.Contribute(smallCircle[next++]);
			return Actions.Move(smallCircle[next++]);
		}
	}
	
	class PrayForFood implements IActionHandler {
		@Override
		public IAction chooseAction(IGame game) {
			if (game.player().hunger() == Hunger.FAINTING)
				return Actions.Pray();
			else
				return null;
		}
	}
	
	public JavaBot(IBotHack bothack) {
		this.bothack = bothack;
		bothack.registerHandler(new IChooseCharacterHandler() {
			@Override
			public String chooseCharacter() {
				return "nsm"; // choose samurai
			}
		});
		bothack.registerHandler(0, new PrayForFood());
		bothack.registerHandler(1, new CircleMover());
		System.out.println("bot initialized");
	}
}