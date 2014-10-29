#!/bin/bash

# restore save (hardcoded), archive old logs/ttyrecs, compile and run in REPL with
# hardcoded config.
# might want to run ttywatch.sh in another console to watch the bot play.

ARCH_DIR=~/tmp

#CONFIG="config/shell-config.edn"
CONFIG="config/shell-wizmode-chobot-config.edn"
#SAVE="save/1000wizard.gz-equipped-valk-3"
#SAVE="save/1000wizard.gz-floating-blindfold"
SAVE="save/1000wizard.gz-throwing"

if [ $(basename `pwd`) = "scripts" ]; then
    cd ..
fi

rm /nh343/var/save/*
if [ -n "$SAVE" ]; then
    cp -v "$SAVE" /nh343/var/save/1000wizard.gz
fi
arch="$ARCH_DIR/$(date +%Y-%m-%d.%H-%M-%S)"
mkdir -pv "$arch"
mv *.log* *.ttyrec "$arch"
# LD_LIBRARY_PATH is necessary for the local shell interface (not needed for
# ssh/telnet), the JNI interface must be compiled manually beforehand
lein compile && (echo "(-main \"$CONFIG\")"
                 cat -) | LD_LIBRARY_PATH=jta26/jni/linux/ lein repl
