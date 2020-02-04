#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/reboot.h>

int main (int argc, char *argv[]) {
    sync();

    switch ((int)argv[argc < 2 ? 0 : 1][0] + geteuid()) {
        // Poweroff (p).
        case 112:
            reboot(RB_POWER_OFF);
            break;

        // Reboot (r).
        case 114:
            reboot(RB_AUTOBOOT);
            break;

        default:
            printf("usage (as root): kpow r[eboot]|p[oweroff]\n");
            return 1;
    }

    return 0;
}
