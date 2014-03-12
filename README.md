ANBF
====

A Nethack Bot Framework

An attempt at an implementation of a NetHack bot framework as a base for my master's thesis.

Written in Clojure and expected to be accessible for AI plugins written in Java.  No game modifications will be used to make the game more accessible for a bot.

Inspired by previous bots and bot frameworks, in particular TAEB ( http://taeb-nethack.blogspot.com/ ) and Pogamut ( http://pogamut.cuni.cz/ ).

## First milestones to reach

- Connect to nethack.alt.org via telnet, interact with the menu.

First task: implement a GUI-less virtual terminal emulation using the JTA Telnet/SSH/Terminal library ( http://www.javatelnet.org/ ).

- Connect to nethack.alt.org via telnet, start and quit a game.
- Start a local game and quit.
- Interact meaningfully with the game.

First rather difficult problem to solve: synchronization.  Discussed here: http://taeb-nethack.blogspot.cz/2009/06/synchronizing-with-nethack.html
