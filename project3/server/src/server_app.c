#include <stdio.h>
#include <stdlib.h>
#include "server.h"

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

    return 0;
}
