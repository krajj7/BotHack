package bothack.javabots.javabot;

import bothack.bot.*;
import bothack.delegator.*;

public class JavaBot {
	IBotHack bothack;
	
	class CircleMover implements IActionHandler {
		Direction[] smallCircle = {Direction.N, Direction.E, Direction.S, Direction.W};
		int next = 0;
		
		@Override
		public IAction chooseAction(IGame game) {
			next = next % smallCircle.length;
			return Actions.Move(smallCircle[next++]);
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
		bothack.registerHandler(-25, new StarvationHandler());
		bothack.registerHandler(1, new CircleMover());
		System.out.println("bot initialized");
	}
}