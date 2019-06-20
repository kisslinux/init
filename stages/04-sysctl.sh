#!/bin/sh

log "Loading sysctl settings..."

 # 'sysctl --system' implementation using 'find'.
find /run/sysctl.d \
     /etc/sysctl.d \
     /usr/local/lib/sysctl.d \
     /usr/lib/sysctl.d \
     /lib/sysctl.d \
     /etc/sysctl.conf \
     -name \*.conf -type f 2>/dev/null \
| while read -r conf; do
    seen="$seen ${conf##*/}"

    case $seen in
        *" ${conf##*/} "*) ;;
        *) printf '%s\n' "* Applying $conf ..."
           sysctl -p "$conf" ;;
    esac
done
