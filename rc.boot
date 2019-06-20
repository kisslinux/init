#!/bin/sh

PATH=/usr/bin:/usr/sbin

log() {
    printf '\e[31;1m=>\e[m %s\n' "$@"
}

emergency_shell() {
    printf '%s\n' "" \
        "Init system encountered an error, starting emergency shell."
        "When ready, type 'exit' to continue the boot."

    /bin/sh -l
}

main() {
    log "Welcome to KISS $(uname -sr)!"

    for stage in /etc/runit/stages/*.sh; do
        # shellcheck disable=1090
        [ -r "$stage" ] && . "$stage"
    done

    log "Boot stage complete..."
}

main
