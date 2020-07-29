#define _POSIX_C_SOURCE 200809L
#include <dirent.h>
#include <inttypes.h>
#include <unistd.h>
#include <signal.h>
#include <stdio.h>

// This is a simple 'killall5' alternative to remove the
// dependency on a rather unportable and "rare" tool for
// the purposes of shutting down the machine.
int main(int argc, char *argv[]) {
    struct dirent *ent;
    DIR *dir;
    int pid;
    int sig = SIGTERM;

    if (argc > 1) {
        sig = strtoimax(argv[1], 0, 10);
    }

    dir = opendir("/proc");

    if (!dir) {
        return 1;
    }

    kill(-1, SIGSTOP);

    while ((ent = readdir(dir))) {
        pid = strtoimax(ent->d_name, 0, 10);

        if (pid < 2 || pid == getpid() ||
            getsid(pid) == getsid(0) ||
            getsid(pid) == 0) {
            continue;
        }

        kill(pid, sig);
    }

    closedir(dir);
    kill(-1, SIGCONT);

    return 0;
}
