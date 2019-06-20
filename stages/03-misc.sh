#!/bin/sh

log "Seeding random..."
if [ -f /var/random.seed ]; then
    cat /var/random.seed > /dev/urandom
else
    dd count=1 bs=512 if=/dev/random of=/var/random.seed
fi

log "Setting up loopback..."
ip link set up dev lo

log "Setting hostname..."
read -r hostname < /etc/hostname &&
    printf '%s\n' "$hostname" > /proc/sys/kernel/hostname

