#!/bin/sh

# TODO: Handle uevents (do we need to do this?)
log "Starting mdev..."

printf '/bin/mdev\n' > /proc/sys/kernel/hotplug
mdev -s
