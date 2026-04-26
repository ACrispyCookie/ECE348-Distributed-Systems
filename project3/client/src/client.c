#include "client.h"
#include <pthread.h>
#include <time.h>
#include <errno.h>
#include <unistd.h>

static char *address;
static int port;
static int block_size;
static int max_cached_blocks;
static int freshT;

static int sockfd;
static struct sockaddr_in server_addr;
static socklen_t server_addrlen;

static ClientFileT *client_files = NULL;
static ServerFileT *server_files = NULL;
static ServerFileT *name_map = NULL;
static RequestT *waiting = NULL;

static pthread_mutex_t lock;
static pthread_t receiver;

PacketT *req_send(RequestT *req);
void *run_receiver(void *args);

int mynfs_init(char *server_address, int server_port, int block_s, int max_blocks, int fresh_time) {
    address = server_address;
    port = server_port;
    block_size = block_s;
    max_cached_blocks = max_blocks;
    freshT = fresh_time;

    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    if (sockfd < 0)
        return -1;

    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port   = htons((uint16_t)server_port);
    if (inet_pton(AF_INET, address, &server_addr.sin_addr) <= 0) {
        close(sockfd);
        return -1;
    }
    server_addrlen = sizeof(server_addr);

    int ret_code = pthread_mutex_init(&lock, NULL);
    if (ret_code != 0)
        return -1;

    ret_code = pthread_create(&receiver, NULL, run_receiver, NULL);
    return ret_code;
}

void mynfs_shutdown() {
    // stop receiver
    // close socket
    // clear waiting map
    // clear clientmap
    // clear servermap
    // clear namemap
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
    HASH_FIND_INT(client_files, &fd, found);

    if (!found) {
        pthread_mutex_unlock(&lock);
        return -1;
    }

    ServerFileT *server_file;
    HASH_FIND(hh_id, server_files, &found->server_fid, sizeof(int), server_file);
    server_file->ref_count--;

    if (server_file->ref_count == 0) {
        HASH_DELETE(hh_id, server_files, server_file);
        HASH_DELETE(hh_name, name_map, server_file);
        free(server_file->filename);
        free(server_file);
    }

    HASH_DEL(client_files, found);
    free(found);
    pthread_mutex_unlock(&lock);
    return 0;
}

PacketT *req_send(RequestT *req) {
    if (!req) return NULL;
    req->resp = NULL;
    req->id = req->packet->header.id;
    req->packet->header.is_response = 0;
    if (pthread_cond_init(&req->cond, NULL) != 0)
        return NULL;
    
    unsigned int buf_len = 0;
    void *buf = req_serialize(req->packet, &buf_len);
    if (!buf || buf_len == 0) return NULL;

    struct timespec ts;
    pthread_mutex_lock(&lock);
    HASH_ADD(hh, waiting, id, sizeof(uint32_t), req);

    int retries = 0;
    while (req->resp == NULL && retries < MAX_RETRIES) {
        ssize_t sent = sendto(sockfd, buf, buf_len, 0,
                             (struct sockaddr *)&server_addr,
                             server_addrlen);
        if (sent != (ssize_t)buf_len) {
            HASH_DEL(waiting, req);
            pthread_mutex_unlock(&lock);
            free(buf);
            pthread_cond_destroy(&req->cond);
            return NULL;
        }

        clock_gettime(CLOCK_REALTIME, &ts);
        ts.tv_sec += TIMEOUT_SEC;

        int rc = pthread_cond_timedwait(&req->cond, &lock, &ts);
        if (rc == 0) break;
        if (rc == ETIMEDOUT) { retries++; continue; }
        break;
    }
    
    HASH_DEL(waiting, req);
    pthread_mutex_unlock(&lock);

    free(buf);
    pthread_cond_destroy(&req->cond);
    return req->resp;
}

void *run_receiver(void *args) {
    unsigned char buf[65536];

    while (1) {
        ssize_t n = recvfrom(sockfd, buf, sizeof(buf), 0, NULL, NULL);
        if (n <= 0) continue;

        PacketT *resp = req_deserialize(buf, (unsigned int)n);
        if (!resp) continue;

        pthread_mutex_lock(&lock);

        RequestT *req;
        uint32_t id = resp->header.id;
        HASH_FIND(hh, waiting, &id, sizeof(uint32_t), req);

        if (req) {
            req->resp = resp;
            pthread_cond_signal(&req->cond);
        } else {
            free(resp->payload);
            free(resp);
        }

        pthread_mutex_unlock(&lock);
    }

    return NULL;
}