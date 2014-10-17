#!/bin/bash

# keeps playing the most recent (growing) ttyrec

if [ $(basename `pwd`) = "scripts" ]; then
    cd ..
fi

trap 'kill $(jobs -pr); exit 0' EXIT

tail -F anbf.log 2>/dev/null | egrep --color=auto 'ERROR|WARN' &
last=$(ls -1 *.ttyrec | tail -n 1)
clear; ttyplay -p "$last" &
while true; do
    sleep 3
    new=$(ls -1 *.ttyrec | tail -n 1)
    if [ "$new" != "$last" ]; then
        kill %%
        last="$new"
        clear; ttyplay -p "$new" &
    fi
done
