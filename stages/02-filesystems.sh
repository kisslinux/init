#!/bin/sh

log "Remounting rootfs as ro..."; {
    mount -o remount,ro / || emergency_shell
}

log "Checking filesystems..."; {
    fsck -ATat
    [ $? -gt 1 ] && emergency_shell
}

log "Mounting rootfs rw..."; {
    mount -o remount,rw / || emergency_shell
}

log "Mounting all local filesystems..."; {
    mount -at nosysfs,nonfs,nonfs4,nosmbfs,nocifs -O no_netdev || emergency_shell
}

log "Enabling swap..."; {
    swapon -a || emergency_shell
}
