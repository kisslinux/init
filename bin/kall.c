#define _POSIX_SOURCE
#include <dirent.h>
#include <inttypes.h>
#include <unistd.h>
#include <signal.h>
#include <stdio.h>

int main(int argc, char *argv[]) {
    struct dirent *ent;
    DIR *dir;
    int pid, sig = SIGTERM;

    if (argc > 1)
        sig = strtoimax(argv[1], 0, 10);

    if (!(dir = opendir("/proc")))
        return 1;

    kill(-1, SIGSTOP);

    while ((ent = readdir(dir))) {
        pid = strtoimax(ent->d_name, 0, 10);

        if (pid < 2 || pid == getpid() ||
            getsid(pid) == getsid(0) || getsid(pid) == 0)
            continue;

        kill(pid, sig);
    }
    closedir(dir);

    kill(-1, SIGCONT);

    return 0;
}
