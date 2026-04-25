#ifndef CLIENT_H
#define CLIENT_H
#include "uthash.h"
#include "protocol.h"
#include <pthread.h>
#include <arpa/inet.h>

typedef struct {
    unsigned int fid;
    char *filename;
    uint64_t size;
    uint64_t mtime;
    unsigned int ref_count;
    UT_hash_handle hh_id;
    UT_hash_handle hh_name;
} ServerFileT;

typedef struct {
    unsigned int fid;
    unsigned int server_fid;
    long pos;
    UT_hash_handle hh;
} ClientFileT;

typedef struct {
    PacketT *packet;
    pthread_cond_t cond;
    UT_hash_handle hh;
} RequestT;


int mynfs_init(char *server_addr, int server_port, int block_size, int max_cached_blocks, int freshT);
int mynfs_open(char *fname);
long mynfs_read(int fd, void *buf, long n);
long mynfs_write(int fd, void *buf, long n);
long mynfs_lseek(int fd, long pos, int whence);
int mynfs_truncate(int fd, long length);
int mynfs_close(int fd);

#endif