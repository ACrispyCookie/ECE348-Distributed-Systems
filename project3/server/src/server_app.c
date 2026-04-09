#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <unistd.h>
#include <pthread.h>
#include "server.h"

volatile sig_atomic_t shuttingDown = 0;
void handle_signal(int signo);

int main(int argc, char *argv[]) {
    int port = 0;
    char *mountpoint = NULL;

    if (argc < 2 || argc > 3) {
        fprintf(stderr, "Error: Invalid number of arguments.\n");
        fprintf(stderr, "Usage: %s <mountpoint> [port]\n", argv[0]);
        return 1;
    }
    mountpoint = argv[1];

    if (argc == 3) {
        port = atoi(argv[2]);
        
        if (port <= 0 || port > 65535) {
            fprintf(stderr, "Error: Invalid port number. Port must be between 1 and 65535.\n");
            return 1;
        }
    }

    port = server_init(mountpoint, port);
    if (port <= 0) {
        fprintf(stderr, "Failed to initialize server.\n");
        return 1;
    }
    printf("Using %s as mount point and running on %d\n", mountpoint, port);

    /* install signal handlers to initiate clean shutdown */
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = handle_signal;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;
    sigaction(SIGINT, &sa, NULL);
    sigaction(SIGTERM, &sa, NULL);

    while (!shuttingDown)
        server_handle_next();
    server_shutdown();

    return 0;
}

void handle_signal(int signo) {
    shuttingDown = 1;
}
