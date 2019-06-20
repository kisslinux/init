#!/bin/sh

PATH=/usr/bin:/usr/sbin

log() {
    printf '\e[31;1m=>\e[m %s\n' "$@"
}

log "Welcome to KISS!"

for stage in /etc/runit/stages/*.sh; do
    # shellcheck disable=1090
    . "$stage"
done
