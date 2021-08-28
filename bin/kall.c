#define _POSIX_C_SOURCE 200809L
#include <dirent.h>
#include <inttypes.h>
#include <unistd.h>
#include <signal.h>
#include <stdio.h>

// This is a simple 'killall5' alternative to remove the dependency on a rather
// unportable and "rare" tool for the purposes of shutting down the machine.
int main(int argc, char *argv[]) {
    int sig = argc > 1 ? strtoimax(argv[1], 0, 10) : SIGTERM;
    DIR *dir = opendir("/proc");

    if (!dir) return 1;

    kill(-1, SIGSTOP);

    int pid = getpid();
    int sid = getsid(pid);

    for (struct dirent *ent; (ent = readdir(dir)); ) {
        int p = strtoimax(ent->d_name, 0, 10);
        int p_sid = getsid(p);

        if (p == 1 || p == pid || p_sid == 0 || p_sid == sid)
            continue;

        kill(p, sig);
    }

    closedir(dir);
    kill(-1, SIGCONT);
}
