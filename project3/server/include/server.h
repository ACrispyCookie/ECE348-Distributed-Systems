#ifndef SERVER_H
#define SERVER_H
#include "uthash.h"
#include <arpa/inet.h>

typedef enum {
    OPEN,
    READ,
    WRITE,
    NOT_FOUND,
    ERROR
} PacketType;

typedef struct {
    PacketType type;
    unsigned int sequence_number;
    unsigned int fid;
    unsigned int length;
    unsigned int offset;
    unsigned int payload_length;
    void *payload;
} PacketT;

typedef struct {
    PacketT packet;
    struct sockaddr_in client_addr;
    socklen_t client_addrlen;
} RequestT;

typedef struct {
    unsigned int local_fid;
    unsigned int global_fid;
    UT_hash_handle hh;
} FileTableT;

int server_init(char *mountpoint, unsigned int port);
void server_handle_next();
void server_shutdown();

#endif