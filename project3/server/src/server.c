#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include "server.h"

int server_init(char *mountpoint, unsigned int port) {
    int sockfd = -1;
    struct sockaddr_in addr;
    socklen_t addrlen = sizeof(addr);

    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    if (sockfd < 0) {
        fprintf(stderr, "socket() failed: %s\n", strerror(errno));
        return -1;
    }

    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons((uint16_t)port);

    if (bind(sockfd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        if (errno == EADDRINUSE)
            fprintf(stderr, "bind() failed: port %u already in use\n", port);
        else
            fprintf(stderr, "bind() failed: %s\n", strerror(errno));
        close(sockfd);
        return -1;
    }

    if (port == 0) {
        if (getsockname(sockfd, (struct sockaddr *)&addr, &addrlen) == 0) {
            port = ntohs(addr.sin_port);
        }
    }

    return port;
}