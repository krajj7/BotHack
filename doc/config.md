## Watching bots run

Unless you disabled the :ttyrec option in the config, a [.ttyrec](http://en.wikipedia.org/wiki/Ttyrec) file will be created in the working directory for each run.

You can use ttyrec viewers like ttyplay or [IPBT](http://www.chiark.greenend.org.uk/~sgtatham/ipbt/) to replay these.

Use `ttyplay -p` to watch a growing ttyrec live.  You can use the bash script [scripts/ttywatch.sh](https://github.com/krajj7/BotHack/blob/master/scripts/ttywatch.sh) to keep playing the most recent ttyrec (needs ttyplay).

## Configuration options

You can change these keys in the config files:

* :javabot - the main class of a Java bot
* :bot - the main namespace of a Clojure bot
* :menubot - the namespace of a bot that handles server menu
* :host - hostname of a NetHack server
* :port - port of the NetHack server
* :dgl-login - server login for dgl-menubot
* :dgl-pass - server password for dgl-menubot
* :ttyrec - if true creates a ttyrec file for each run
* :interface - can be either :shell, :telnet or :ssh
* :nh-command - NetHack command (when :interface is set to :shell)
* :ssh-user - user for SSH login (when :interface is set to :ssh)
* :ssh-pass - password for SSH login (when :interface is set to :ssh)
* :no-exit - if set to true the program will not terminate when the game ends or when the bot gets stuck (useful for debugging from the repl)
* :quit-resumed - when set to true will quit the game if the bot is started with a saved game with T:100 or higher

## Logging

Detailed logs are generated in the working directory in `bothack.log` files.  These logs are rotated automatically.
