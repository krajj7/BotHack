package anbf.javabots;

import anbf.bot.*;

/** A dumb example bot.  Equivalent to simplebot.clj */
public class SimpleBot {
	IANBF anbf;
	
	class CircleMover implements IActionHandler {
		int[] smallCircle = {6, 2, 4, 8};
		int next = 0;
		
		@Override
		public IAction chooseAction(IGame game) {
			next = next % smallCircle.length;
			return Actions.Move(smallCircle[next++]);
		}
	}
	
	class PrayForFood implements IActionHandler {
		@Override
		public IAction chooseAction(IGame game) {
			if (game.player().isWeak())
				return Actions.Pray();
			else
				return null;
		}
	}
	
	public SimpleBot(IANBF anbf) {
		this.anbf = anbf;
		anbf.registerHandler(new IChooseCharacterHandler() {
			@Override
			public String chooseCharacter() {
				return "nsm"; // choose samurai
			}
		});
		anbf.registerHandler(0, new PrayForFood());
		anbf.registerHandler(1, new CircleMover());
		System.out.println("bot initialized");
	}
}