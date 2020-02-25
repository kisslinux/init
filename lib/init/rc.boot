#!/bin/sh
# shellcheck disable=1090,1091

. /usr/lib/init/rc.lib

PATH=/usr/bin:/usr/sbin
old_ifs=$IFS

log "Welcome to KISS $(uname -sr)!"

log "Mounting pseudo filesystems..."; {
    mnt /proc -o nosuid,noexec,nodev    -t proc     proc
    mnt /sys  -o nosuid,noexec,nodev    -t sysfs    sys
    mnt /run  -o mode=0755,nosuid,nodev -t tmpfs    run
    mnt /dev  -o mode=0755,nosuid       -t devtmpfs dev

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
    ln -sf /proc/self/fd /dev/fd
    ln -sf fd/0 /dev/stdin
    ln -sf fd/1 /dev/stdout
    ln -sf fd/2 /dev/stderr

    # udev sometimes doesn't set this permission for whatever
    # reason. Until we figure out why, let's do it ourselves.
    chown root:video /dev/dri/*
}

log "Starting device manager..."; {
    if command -v udevd >/dev/null; then
        log "Starting udevd..."

        udevd --daemon
        udevadm trigger --action=add --type=subsystems
        udevadm trigger --action=add --type=devices
        udevadm trigger --action=change --type=devices
        udevadm settle

    elif command -v mdev >/dev/null; then
        log "Starting mdev..."

        printf '/bin/mdev\n' > /proc/sys/kernel/hotplug
        mdev -s

        # Create /dev/mapper nodes
        [ -x /bin/dmsetup ] && dmsetup mknodes

        # Handle Network interfaces.
        for file in /sys/class/net/*/uevent; do
            printf 'add\n' > "$file"
        done 2>/dev/null

        # Handle USB devices.
        for file in /sys/bus/usb/devices/*; do
            case ${file##*/} in
                [0-9]*-[0-9]*)
                    printf 'add\n' > "$file/uevent"
                ;;
            esac
        done
    fi
}

log "Remounting rootfs as ro..."; {
    mount -o remount,ro / || emergency_shell
}

log "Activating encrypted devices (if any exist)..."; {
    [ -e /etc/crypttab ] && [ -x /bin/cryptsetup ] && {
        exec 3<&0

        # shellcheck disable=2086
        while read -r name dev pass opts err; do
            # Skip comments.
            [ "${name##\#*}" ] || continue

            # Break on invalid crypttab.
            [ "$err" ] && {
                printf 'error: A valid crypttab has only 4 columns.\n'
                break
            }

            # Turn 'UUID=*' lines into device names.
            [ "${dev##UUID*}" ] || dev=$(blkid -l -o device -t "$dev")

            # Parse options by turning list into a pseudo array.
            IFS=,
            set -f
            set +f -- $opts
            IFS=$old_ifs

            copts="cryptsetup luksOpen"

            # Create an argument list (no other way to do this in sh).
            for opt; do case $opt in
                discard)            copts="$copts --allow-discards" ;;
                readonly|read-only) copts="$copts -r" ;;
                tries=*)            copts="$copts -T ${opt##*=}" ;;
            esac; done

            # If password is 'none', '-' or empty ask for it.
            case $pass in
                none|-|"") $copts "$dev" "$name" <&3 ;;
                *)         $copts -d "$pass" "$dev" "$name" ;;
            esac
        done < /etc/crypttab

        exec 3>&-

        [ "$copts" ] && [ -x /bin/vgchange ] && {
            log "Activating LVM devices for dm-crypt..."
            vgchange --sysinit -a y || emergency_shell
        }
    }
}

log "Loading rc.conf settings..."; {
    [ -f /etc/rc.conf ] && . /etc/rc.conf
}

log "Checking filesystems..."; {
    fsck -ATat noopts=_netdev
    [ $? -gt 1 ] && emergency_shell
}

log "Mounting rootfs rw..."; {
    mount -o remount,rw / || emergency_shell
}

log "Mounting all local filesystems..."; {
    mount -a || emergency_shell
}

log "Enabling swap..."; {
    swapon -a || emergency_shell
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
    printf '%s\n' "${hostname:-KISS}" > /proc/sys/kernel/hostname
} 2>/dev/null

log "Loading sysctl settings..."; {
    for conf in /run/sysctl.d/*.conf \
                /etc/sysctl.d/*.conf \
                /usr/lib/sysctl.d/*.conf \
                /etc/sysctl.conf; do

        [ -f "$conf" ] || break

        seen="$seen ${conf##*/}"

        case $seen in
            *" ${conf##*/} "*) ;;
            *) printf '%s\n' "* Applying $conf ..."
               sysctl -p "$conf" ;;
        esac
    done
}

command -v udevd >/dev/null &&
    udevadm control --exit

log "Running rc.d hooks..."; {
    for file in /etc/rc.d/*.boot; do
        [ -f "$file" ] && . "$file"
    done
}

log "Boot stage complete..."
