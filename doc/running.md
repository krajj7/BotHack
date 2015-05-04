## Running the example Java bot

### Prerequisites

Make sure you have Java 7 JDK and Maven installed (check `mvn -V`)

### Compiling the bot skeleton

There is a working bot Maven project skeleton available here:
https://github.com/krajj7/BotHack/blob/master/javabots/SimpleBot

Or a more expanded, but still fairly basic bot example here:
https://github.com/krajj7/BotHack/blob/master/javabots/JavaBot

To compile these, run `mvn compile` in the project directory.

BotHack itself doesn't need to be compiled manually to run Java bots against public servers, a packaged version of the framework jar and other dependencies will be downloaded automatically by Maven.

### Running against a public server

The framework should work with any server running the nethack.alt.org version of NetHack, for example those at http://alt.org/nethack (USA) and http://nethack.xd.cm (Germany).

NOTE: Java 8 may be necessary to play on nethack.xd.cm due to [a JDK bug](http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6521495)

* Register an account on your chosen server.
* Replace the default nethackrc for the account with [the one provided](https://github.com/krajj7/BotHack/blob/master/bothack.nethackrc).
* Edit `config/simplebot-ssh-config.edn` or `config/simplebot-telnet-config.edn` in the SimpleBot project (or the JavaBot equivalents), change the server address, your username and password.
* Run `mvn test -Prun-ssh` or `mvn test -Prun-telnet`

The DGL (dgamelaunch) menubot set by default in the config files should work with nethack.alt.org and nethack.xd.cm servers, it may need tweaking for other servers.

Network latency may make the bot play very slowly (and not be CPU-bound), if that is the case consider setting up a local instalation of NetHack (which also allows you to do testing in wizard mode).

Increasing the JVM heap size limits (`:jvm-opts ["-Xms4g" "-Xmx4g"]` in project.clj) might improve performance.

### Running against a local NetHack installation (on Linux)

The only supported version of NetHack is 3.4.3 with the nethack.alt.org patchset, available here: http://alt.org/nethack/naonh.php

* Run `ant` in the `jta26` directory.
* Run `make` in the `jta26/jni/linux` directory (use jni.h and jni\_md.h headers provided with your JDK).
* Edit `config/simplebot-shell-config.edn`, set the command to run NetHack on your system.
* Make sure [the provided nethackrc](https://github.com/krajj7/BotHack/blob/master/bothack.nethackrc). is used when you run the command manually (you should NOT get the initial "It is written in the Book of $DEITY" message when you start a game).
* Run `LD_LIBRARY_PATH=../../jta26/jni/linux/ mvn test -Prun-shell`
