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

static unsigned int last_fid;
static FILE *memory_file = NULL;
static FileTableT *table = NULL;

RequestT *req_receive();
int req_send(RequestT *request);
RequestT *req_deserialize(void *buf, unsigned int length);
void *req_serialize(RequestT *request);

void handle_open(RequestT *open);
void handle_read(RequestT *read);
void handle_write(RequestT *write);

int get_next_id();

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
        if (getsockname(sockfd, (struct sockaddr *)&addr, &addrlen) == 0)
            port = ntohs(addr.sin_port);
    }

    // Initialize lastFid from memory.txt in the working directory or 0 if absent.
    memory_file = fopen("memory.txt", "r+");
    if (!memory_file) {
        memory_file = fopen("memory.txt", "w+");
        last_fid = 0;
    } else {
        unsigned int v = 0;
        if (fscanf(memory_file, "%u", &v) == 1) {
            last_fid = v;
        } else {
            last_fid = 0;
        }
    }

    return port;
}

void server_shutdown() {
    close(memory_file);
}

void server_handle_next() {
    RequestT *received = req_receive();
    FileTableT *found;
    HASH_FIND_INT(table, &(received->fid), found);

    switch (received->type) {
        case OPEN:
            break;
        case READ:
            break;
        case WRITE:
            break;
    }
}

RequestT *req_receive() {

}

int req_send(RequestT *request) {

}

RequestT *req_deserialize(void *buf, unsigned int length) {

}

void *req_serialize(RequestT *request) {

}

int get_next_id() {
    unsigned int next = last_fid + 1;

    if (memory_file) {
        if (fseek(memory_file, 0, SEEK_SET) == 0) {
            fprintf(memory_file, "%u\n", next);
            fflush(memory_file);
        }
    }

    last_fid = next;
    return next;
}