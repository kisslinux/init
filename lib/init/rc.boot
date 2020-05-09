#!/bin/sh
# shellcheck disable=1090,1091

# Shared code between boot/shutdown.
. /usr/lib/init/rc.lib

log "Welcome to KISS!"

log "Mounting pseudo filesystems..."; {
    mnt nosuid,noexec,nodev    proc     proc /proc
    mnt nosuid,noexec,nodev    sysfs    sys  /sys
    mnt mode=0755,nosuid,nodev tmpfs    run  /run
    mnt mode=0755,nosuid       devtmpfs dev  /dev

    # Behavior is intentional and harmless if not.
    # shellcheck disable=2174
    mkdir -p /run/runit \
             /run/user  \
             /run/lock  \
             /run/log   \
             /dev/pts   \
             /dev/shm

    mnt mode=0620,gid=5,nosuid,noexec devpts devpts /dev/pts
    mnt mode=1777,nosuid,nodev        tmpfs  shm    /dev/shm

    # udev created these for us, however other device managers
    # don't. This is fine even when udev is in use.
    {
        ln -s /proc/self/fd /dev/fd
        ln -s fd/0          /dev/stdin
        ln -s fd/1          /dev/stdout
        ln -s fd/2          /dev/stderr
    } 2>/dev/null
}

log "Starting device manager..."; {
    if command -v udevd >/dev/null; then
        log "Starting udev..."

        udevd -d
        udevadm trigger -c add -t subsystems
        udevadm trigger -c add -t devices
        udevadm settle

    elif command -v mdev >/dev/null; then
        log "Starting mdev..."

        mdev -s
        mdev -df & mdev_pid=$!
    fi
}

log "Remounting rootfs as read-only..."; {
    mount -o remount,ro / || sos
}

log "Loading rc.conf settings..."; {
    [ -f /etc/rc.conf ] && . /etc/rc.conf
}

log "Checking filesystems..."; {
    fsck -ATat noopts=_netdev

    # It can't be assumed that success is 0
    # and failure is > 0.
    [ $? -gt 1 ] && sos
}

log "Mounting rootfs as read-write..."; {
    mount -o remount,rw / || sos
}

log "Mounting all local filesystems..."; {
    mount -a || sos
}

log "Enabling swap..."; {
    swapon -a || sos
}

log "Seeding random..."; {
    random_seed load
}

log "Setting up loopback..."; {
    ip link set up dev lo
}

log "Setting hostname..."; {
    read -r hostname < /etc/hostname
    printf %s "${hostname:-KISS}" > /proc/sys/kernel/hostname
} 2>/dev/null

log "Loading sysctl settings..."; {
    # This is a portable equivalent to 'sysctl --system'
    # following the exact same semantics.
    for conf in /run/sysctl.d/*.conf \
                /etc/sysctl.d/*.conf \
                /usr/lib/sysctl.d/*.conf \
                /etc/sysctl.conf; do

        [ -f "$conf" ] || continue

        # Skip conf files we have already seen (basename match).
        case $seen in *" ${conf##*/} "*) continue; esac
        seen=" $seen ${conf##*/} "

        sysctl -p "$conf"
    done
}

log "Killing device manager to make way for service..."; {
    if command -v udevd >/dev/null; then
        udevadm control --exit

    elif [ "$mdev_pid" ]; then
        kill "$mdev_pid"

        # Try to set the hotplug script to mdev.
        # This will silently fail if unavailable.
        #
        # The user should then run the mdev service
        # to enable hotplugging.
        printf /bin/mdev 2>/dev/null \
            > /proc/sys/kernel/hotplug
    fi
}

log "Running boot hooks..."; {
    for file in /usr/lib/init/rc.d/*.boot \
                /etc/rc.d/*.boot; do
        [ -f "$file" ] && . "$file"
    done
}

# Calculate how long the boot process took to
# complete. This entire process is too cheap!
IFS=. read -r boot_time _ < /proc/uptime

log "Boot stage completed in ${boot_time}s..."
