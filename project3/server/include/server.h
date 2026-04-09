#ifndef SERVER_H
#define SERVER_H
#include "uthash.h"

typedef enum {
    OPEN,
    READ,
    WRITE,
    NO_FILE_FOUND
} RequestType;

typedef struct {
    RequestType type;
    unsigned int sequence_number;
    unsigned int length;
    unsigned int offset;
    unsigned int fid;
    unsigned int payload_length;
    void *payload;
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