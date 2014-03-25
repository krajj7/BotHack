ANBF
====

A Nethack Bot Framework

An attempt at an implementation of a NetHack bot framework as a base for my master's thesis.

Written in Clojure and expected to be accessible for AI plugins written in Java.  No game modifications will be used to make the game more accessible for a bot.

Inspired by previous bots and bot frameworks, in particular TAEB ( http://taeb.github.io/ ) and Pogamut ( http://pogamut.cuni.cz/ ).

## First milestones reached

A basic GUI-less virtual terminal emulator is now working, implemented using the JTA Telnet/SSH/Terminal library ( http://www.javatelnet.org/ ).
Manual interaction with a remote telnet terminal is possible via the emulator, current frame for the terminal can be accessed programatically and displayed.

A simple event-driven script (src/anbf/bot/nao\_menu.clj) can now interact with the nethack.alt.org game menu and start a game.

## Milestones to reach

- Start and quit a game programmatically.
- Start interacting meaningfully with the game.

First rather difficult problem to solve: synchronization.  Discussed here: http://taeb-nethack.blogspot.cz/2009/06/synchronizing-with-nethack.html
