package bothack.javabots.javabot;

import bothack.bot.IBotHack;

/** This is an example of a bot using the Java API.
 * It is a very basic bot, doing only minimal things to demonstrate and test
 * basic functionality of the API.  You can expect it to die fairly quickly.
 * See {@link https://github.com/krajj7/BotHack/blob/master/doc/running.md}
 * for how to run it. */
public class JavaBot {
	public JavaBot(IBotHack bothack) {
		// All of these implement IActionHandler and will be invoked in priority
		// order until one of them returns a non-null value (the action to perform).
		bothack.registerHandler(-16, new EnhanceHandler());
		bothack.registerHandler(-11, new MajorTroubleHandler());
		bothack.registerHandler(-6, new FightHandler());
		bothack.registerHandler(-3, new MinorTroubleHandler());
		bothack.registerHandler(1, new FeedHandler());
		bothack.registerHandler(3, new RecoverHandler());
		bothack.registerHandler(6, new GatherItems());
		bothack.registerHandler(19, new ProgressHandler());
		System.out.println("bot initialized");
	}
}
