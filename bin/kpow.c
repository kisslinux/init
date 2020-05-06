#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/reboot.h>

// This is a simple utility to instruct the kernel to shutdown
// or reboot the machine. This runs at the end of the shutdown
// process as an init-agnostic method of shutting down the system.
int main (int argc, char *argv[]) {
    sync();

    switch ((int)argv[argc < 2 ? 0 : 1][0] + geteuid()) {
        case 'p':
            reboot(RB_POWER_OFF);
            break;

        case 'r':
            reboot(RB_AUTOBOOT);
            break;

        default:
            printf("usage (as root): kpow r[eboot]|p[oweroff]\n");
            return 1;
    }

    return 0;
}
