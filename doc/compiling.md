## Compiling BotHack from source and running Clojure bots

These instructions are for Linux systems.

### Prerequisites

* Make sure you have Java 7 JDK (check `javac -version`)
* Install Leiningen from http://leiningen.org/ (prefer the most recent version from the website to a pre-packaged one for your distribution, the installation is very simple)
* Run `lein compile` in the project directory.  Dependecies will be downloaded automatically.

### Running against a public server

The framework should work with any server running the nethack.alt.org version of NetHack, for example those at http://alt.org/nethack (USA) and http://acehack.de (Germany).

* Register an account on your chosen server.
* Replace the default nethackrc for the account with [the one provided](https://github.com/krajj7/BotHack/blob/master/bothack.nethackrc).
* Make a copy of `config/ssh-config.edn` or `config/telnet-config.edn`, set the server address, your username and password.
* Run `lein run config/<your-config-file.edn>`

The DGL (dgamelaunch) menubot set by default in the config files should work with nethack.alt.org and acehack.de servers, it may need tweaking for other servers.

Network latency may make the bot play very slowly (and not be CPU-bound), if that is the case consider setting up a local instalation of NetHack (which also allows you to do testing in wizard mode).

### Running against a local NetHack installation

The only supported version of NetHack is 3.4.3 with the nethack.alt.org patchset, available here: http://alt.org/nethack/naonh.php

* Run `ant` in the `jta26` directory.
* Run `make` in the `jta26/jni/linux` directory (use jni.h and jni\_md.h headers provided with your JDK).
* Make a copy of `config/shell-config.edn`, set the command to run NetHack on your system.
* Make sure [the provided nethackrc](https://github.com/krajj7/BotHack/blob/master/bothack.nethackrc). is used when you run the command manually (you should NOT get the initial "It is written in the Book of $DEITY" message when you start a game).
* Run `LD_LIBRARY_PATH=jta26/jni/linux/ lein run config/<your-config-file.edn>`
