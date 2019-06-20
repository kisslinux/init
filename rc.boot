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

log "Welcome to KISS!"

log "Mounting pseudo filesystems..."
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

# TODO: Handle uevents (do we need to do this?)
log "Starting mdev..."
printf '%s\n' "/bin/mdev" > /proc/sys/kernel/hotplug
mdev -s

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
