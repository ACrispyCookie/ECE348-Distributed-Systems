#include "client.h"
#include <pthread.h>

static char *address;
static int port;
static int block_size;
static int max_cached_blocks;
static int freshT;

static ClientFileT *client_files = NULL;
static ServerFileT *server_files = NULL;
static ServerFileT *name_map = NULL;
static RequestT *waiting = NULL;

static pthread_mutex_t lock;
static pthread_t receiver;

int mynfs_init(char *server_addr, int server_port, int block_s, int max_blocks, int fresh_time) {
    address = server_addr;
    port = server_port;
    block_size = block_s;
    max_cached_blocks = max_blocks;
    freshT = fresh_time;

    int ret_code = pthread_mutex_init(&lock, NULL);
    if (ret_code < 0)
        return -1;

    ret_code = pthread_create(&receiver, NULL, run_receiver, NULL);
    return ret_code;
}

int mynfs_open(char *fname) {
    pthread_mutex_lock(&lock);
    pthread_mutex_unlock(&lock);
}

long mynfs_read(int fd, void *buf, long n) {
    pthread_mutex_lock(&lock);
    pthread_mutex_unlock(&lock);
}

long mynfs_write(int fd, void *buf, long n) {
    pthread_mutex_lock(&lock);
    pthread_mutex_unlock(&lock);
}

long mynfs_lseek(int fd, long pos, int whence) {
    pthread_mutex_lock(&lock);
    pthread_mutex_unlock(&lock);
}

int mynfs_truncate(int fd, long length) {
    pthread_mutex_lock(&lock);
    pthread_mutex_unlock(&lock);
}

int mynfs_close(int fd) {
    pthread_mutex_lock(&lock);
    ClientFileT *found;
    HASH_FIND_INT(client_files, fd, found);

    if (!found)
        return -1;

    ServerFileT *server_file;
    HASH_FIND(hh_id, server_files, found->server_fid, sizeof(int), server_file);
    server_file->ref_count--;

    if (server_file->ref_count == 0) {
        HASH_DELETE(hh_id, server_files, server_file);
        HASH_DELETE(hh_name, name_map, server_file);
    }

    HASH_DEL(client_files, found);
    pthread_mutex_unlock(&lock);
}

int req_send(RequestT *req) {
    pthread_mutex_lock(&lock);
    
    pthread_mutex_unlock(&lock);
    return 0;
}

void *run_receiver(void *args) {

}