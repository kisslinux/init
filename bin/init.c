// Tiny init by Rich Felker.
// See: https://ewontfix.com/14/

#define _POSIX_C_SOURCE 200809L

#include <signal.h>
#include <unistd.h>
#include <sys/wait.h>

int main(void) {
    int status;
    sigset_t set;

    if (getpid() != 1) return 1;

    sigfillset(&set);
    sigprocmask(SIG_BLOCK, &set, 0);

    if (fork()) for (;;) wait(&status);

    sigprocmask(SIG_UNBLOCK, &set, 0);

    setsid();
    return execve("/lib/init/rc.boot", (char *[]){0}, (char *[]){0});
}
