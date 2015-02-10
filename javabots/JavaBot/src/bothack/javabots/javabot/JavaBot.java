package bothack.javabots.javabot;

import bothack.bot.IBotHack;

public class JavaBot {
	IBotHack bothack;
	
	public JavaBot(IBotHack bothack) {
		this.bothack = bothack;
		// All of these implement IActionHandler and will be invoked in priority
		// order until one of them returns a non-null value (the action to perform).
		bothack.registerHandler(-16, new EnhanceHandler());
		bothack.registerHandler(-11, new MajorTroubleHandler());
		bothack.registerHandler(-6, new FightHandler());
		bothack.registerHandler(-3, new MinorTroubleHandler());
		bothack.registerHandler(1, new FeedHandler());
		bothack.registerHandler(3, new RecoverHandler());
		bothack.registerHandler(19, new ProgressHandler());
		System.out.println("bot initialized");
	}
}