#!/bin/sh
# shellcheck disable=1090,1091

# Shared code between boot/shutdown.
. /usr/lib/init/rc.lib

log "Welcome to KISS!"

log "Mounting pseudo filesystems..."; {
    mnt /proc -o nosuid,noexec,nodev    -t proc     proc
    mnt /sys  -o nosuid,noexec,nodev    -t sysfs    sys
    mnt /run  -o mode=0755,nosuid,nodev -t tmpfs    run
    mnt /dev  -o mode=0755,nosuid       -t devtmpfs dev

    # Behavior is intentional and harmless if not.
    # shellcheck disable=2174
    mkdir -pm 0755 /run/runit \
                   /run/lvm   \
                   /run/user  \
                   /run/lock  \
                   /run/log   \
                   /dev/pts   \
                   /dev/shm

    mnt /dev/pts -o mode=0620,gid=5,nosuid,noexec -nt devpts devpts
    mnt /dev/shm -o mode=1777,nosuid,nodev        -nt tmpfs  shm

    # udev created these for us, however other device managers
    # don't. This is fine even when udev is in use.
    #
    # Check that these don't already exist as symlinks to
    # avoid issues when KISS is used with Bedrock Linux.
    [ -h /dev/fd     ] || ln -sf /proc/self/fd /dev/fd
    [ -h /dev/stdin  ] || ln -sf fd/0 /dev/stdin
    [ -h /dev/stdout ] || ln -sf fd/1 /dev/stdout
    [ -h /dev/stderr ] || ln -sf fd/2 /dev/stderr
}

log "Starting device manager..."; {
    if command -v udevd >/dev/null; then
        log "Starting udevd..."

        udevd -d
        udevadm trigger -c add    -t subsystems
        udevadm trigger -c add    -t devices
        udevadm settle

    elif command -v mdev >/dev/null; then
        log "Starting mdev..."

        mdev -s
        mdev -df & mdev_pid=$!

        # Create /dev/mapper nodes.
        [ -x /bin/dmsetup ] && dmsetup mknodes
    fi
}

log "Remounting rootfs as ro..."; {
    mount -o remount,ro / || sos
}

log "Activating encrypted devices (if any exist)..."; {
    [ -e /etc/crypttab ] && [ -x /bin/cryptsetup ] &&
        parse_crypttab
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

log "Mounting rootfs rw..."; {
    mount -o remount,rw / || sos
}

log "Mounting all local filesystems..."; {
    mount -a || sos
}

log "Enabling swap..."; {
    swapon -a || sos
}

log "Seeding random..."; {
    if [ -f /var/random.seed ]; then
        cat /var/random.seed > /dev/urandom
    else
        log "This may hang."
        log "Mash the keyboard to generate entropy..."

        dd count=1 bs=512 if=/dev/random of=/var/random.seed
    fi
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

        seen="$seen ${conf##*/}"

        case $seen in
            *" ${conf##*/} "*) ;;
            *) sysctl -p "$conf" ;;
        esac
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

log "Running rc.d hooks..."; {
    for file in /etc/rc.d/*.boot; do
        [ -f "$file" ] && . "$file"
    done
}

log "Boot stage complete..."
