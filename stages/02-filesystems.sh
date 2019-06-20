#!/bin/sh

log "Remounting rootfs as ro..."
mount -o remount,ro /

log "Checking filesystems..."
fsck -ATat

log "Mounting rootfs rw..."
mount -o remount,rw /

log "Mounting all local filesystems..."
mount -at nosysfs,nonfs,nonfs4,nosmbfs,nocifs -O no_netdev

log "Enabling swap..."
swapon -a
