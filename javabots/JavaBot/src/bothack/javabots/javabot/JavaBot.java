package bothack.javabots.javabot;

import bothack.bot.IBotHack;

public class JavaBot {
	IBotHack bothack;
	
	public JavaBot(IBotHack bothack) {
		this.bothack = bothack;
		bothack.registerHandler(-16, new EnhanceHandler());
		bothack.registerHandler(-11, new StarvationHandler());
		bothack.registerHandler(-6, new FightHandler());
		bothack.registerHandler(19, new ProgressHandler());
		System.out.println("bot initialized");
	}
}