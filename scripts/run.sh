#!/bin/bash

# restore save (hardcoded), move old logs/ttyrecs, compile and run in REPL with
# hardcoded config.
# might want to run ttywatch.sh in another console to watch the bot play.

if [ $(basename `pwd`) = "scripts" ]; then
    cd ..
fi

cp -v save/1000wizard.gz-equipped-valk-3 /nh343/var/save/1000wizard.gz
mv *.log* *.ttyrec ~/tmp
# LD_LIBRARY_PATH is necessary for the local shell interface (not needed for
# ssh/telnet), the JNI interface must be compiled manually beforehand
lein compile && (echo '(-main "config/shell-wizmode-config.edn")'
                 cat <&0) | LD_LIBRARY_PATH=jta26/jni/linux/ lein repl
