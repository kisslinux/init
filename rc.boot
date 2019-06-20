#!/bin/sh

PATH=/usr/bin:/usr/sbin

log() {
    printf '\e[31;1m=>\e[m %s\n' "$@"
}

mnt() {
    mountpoint -q "$1" || {
        dir=$1
        shift
        mount "$@" "$dir"
    }
}

emergency_shell() {
    printf '%s\n' "" \
        "Init system encountered an error, starting emergency shell."
        "When ready, type 'exit' to continue the boot."

    /bin/sh -l
}

main() {
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

        mnt /dev/pts -o mode=0620,gid=5,nosuid,noexec -nt devpts     devpts
        mnt /dev/shm -o mode=1777,nosuid,nodev        -nt tmpfs      shm
        mnt /sys/kernel/security                      -nt securityfs securityfs
    }

    # TODO: Handle uevents (do we need to do this?)
    log "Starting mdev..."; {
        printf '/bin/mdev\n' > /proc/sys/kernel/hotplug
        mdev -s
    }

    log "Remounting rootfs as ro..."; {
        mount -o remount,ro /
    } || emergency_shell

    log "Checking filesystems..."; {
        fsck -ATat
        [ $? -gt 1 ] && emergency_shell
    }

    log "Mounting rootfs rw..."; {
        mount -o remount,rw /
    } || emergency_shell

    log "Mounting all local filesystems..."; {
        mount -at nosysfs,nonfs,nonfs4,nosmbfs,nocifs -O no_netdev
    } || emergency_shell

    log "Enabling swap..."; {
        swapon -a
    } || emergency_shell

    log "Seeding random..."; {
        if [ -f /var/random.seed ]; then
            cat /var/random.seed > /dev/urandom
        else
            dd count=1 bs=512 if=/dev/random of=/var/random.seed
        fi
    }

    log "Setting up loopback..."; {
        ip link set up dev lo
    }

    log "Setting hostname..."; {
        read -r hostname < /etc/hostname &&
            printf '%s\n' "$hostname" > /proc/sys/kernel/hostname
    }

    log "Loading sysctl settings..."; {
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
    }

    log "Restricting dmesg if enabled..."; {
        case $(sysctl -n kernel.dmesg_restrict) in
            1) chmod 0600 /var/log/dmesg.log ;;
            *) chmod 0644 /var/log/dmesg.log ;;
        esac
    }

    log "Boot stage complete..."
}

main
