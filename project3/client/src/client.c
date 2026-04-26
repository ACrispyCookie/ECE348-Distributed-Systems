#include "client.h"
#include <pthread.h>
#include <time.h>
#include <errno.h>
#include <unistd.h>

static volatile int shutting_down = 0;
static int last_fid = 0;
static int last_request_id = 0;

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
static RequestT *waiting = NULL;

static pthread_mutex_t lock;
static pthread_t receiver;

PacketT *req_send(PacketT *packet);
void *run_receiver(void *args);
PacketT create_packet(PacketType type);
int get_next_req_id();
int get_next_fid();

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
    shutting_down = 1;
    shutdown(sockfd, SHUT_RDWR);
    close(sockfd);
    pthread_join(receiver, NULL);

    RequestT *current, *tmp;
    HASH_ITER(hh, waiting, current, tmp) {
        HASH_DEL(waiting, current);
        pthread_cond_signal(&current->cond);
    }

    ClientFileT *current_c, *tmp_c;
    HASH_ITER(hh, client_files, current_c, tmp_c) {
        HASH_DEL(client_files, current_c);
        free(current_c);
    }

    ServerFileT *current_s, *tmp_s;
    HASH_ITER(hh, server_files, current_s, tmp_s) {
        HASH_DEL(server_files, current_s);
        free(current_s->filename);
        free(current_s);
    }

    pthread_mutex_destroy(&lock);
}

int mynfs_open(char *fname) {
    pthread_mutex_lock(&lock);

    ServerFileT *found;
    HASH_FIND_STR(server_files, fname, found);
    if (found == NULL) {
        pthread_mutex_unlock(&lock);

        PacketT packet = create_packet(OPEN);
        strncpy(packet.body.open_req.fname, fname, 255);
        packet.body.open_req.fname[255] = '\0';
        PacketT *response = req_send(&packet);
        if (response == NULL || response->header.type != OPEN)
            return -1;

        pthread_mutex_lock(&lock);
        HASH_FIND_STR(server_files, fname, found);
        if (found == NULL) {
            found = (ServerFileT *) malloc(sizeof(ServerFileT));
            if (found == NULL) {
                pthread_mutex_unlock(&lock);
                return -1;
            }
            found->filename = strdup(fname);
            found->fid = response->body.open_resp.fid;
            found->mtime = response->body.open_resp.mtime;
            found->size = response->body.open_resp.size;
            found->ref_count = 0;
            HASH_ADD_KEYPTR(hh, server_files, found->filename, strlen(found->filename), found);
        }
        free(response);
    }

    int fid = get_next_fid();
    ClientFileT *new_file = (ClientFileT *) malloc(sizeof(ClientFileT));
    if (new_file == NULL) {
        pthread_mutex_unlock(&lock);
        return -1;
    }

    found->ref_count++;
    new_file->server_file = found;
    new_file->fid = fid;
    new_file->pos = 0;
    HASH_ADD_INT(client_files, fid, new_file);
    pthread_mutex_unlock(&lock);
    return fid;
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

    ServerFileT *server_file = found->server_file;
    server_file->ref_count--;

    if (server_file->ref_count == 0) {
        HASH_DEL(server_files, server_file);
        free(server_file->filename);
        free(server_file);
    }

    HASH_DEL(client_files, found);
    free(found);
    pthread_mutex_unlock(&lock);
    return 0;
}

PacketT *req_send(PacketT *packet) {
    if (!packet) return NULL;
    RequestT req;
    req.packet = packet;
    req.resp = NULL;
    req.id = req.packet->header.id = get_next_req_id();
    if (pthread_cond_init(&req.cond, NULL) != 0)
        return NULL;
    
    unsigned int buf_len = 0;
    void *buf = req_serialize(req.packet, &buf_len);
    if (!buf || buf_len == 0) return NULL;

    struct timespec ts;
    pthread_mutex_lock(&lock);
    HASH_ADD(hh, waiting, id, sizeof(uint32_t), &req);

    int retries = 0;
    while (req.resp == NULL && retries < MAX_RETRIES && !shutting_down) {
        ssize_t sent = sendto(sockfd, buf, buf_len, 0,
                             (struct sockaddr *)&server_addr,
                             server_addrlen);
        if (sent != (ssize_t)buf_len) {
            HASH_DEL(waiting, &req);
            pthread_mutex_unlock(&lock);
            free(buf);
            pthread_cond_destroy(&req.cond);
            return NULL;
        }

        clock_gettime(CLOCK_REALTIME, &ts);
        ts.tv_sec += TIMEOUT_SEC;

        int rc = pthread_cond_timedwait(&req.cond, &lock, &ts);
        if (rc == 0) break;
        if (rc == ETIMEDOUT) { retries++; continue; }
    }
    
    HASH_DEL(waiting, &req);
    pthread_mutex_unlock(&lock);

    free(buf);
    pthread_cond_destroy(&req.cond);
    return req.resp;
}

void *run_receiver(void *args) {
    unsigned char buf[65536];

    while (1) {
        ssize_t n = recvfrom(sockfd, buf, sizeof(buf), 0, NULL, NULL);
        if (n <= 0 && shutting_down) break;
        else if (n <= 0) continue;

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

PacketT create_packet(PacketType type) {
    PacketT packet = {0};
    packet.header.type = type;
    packet.header.is_response = 0;
    packet.payload = NULL;
    packet.payload_length = 0;
    return packet;
}

int get_next_req_id() {
    return __atomic_fetch_add(&last_request_id, 1, __ATOMIC_SEQ_CST) + 1;
}

int get_next_fid() {
    return ++last_fid;
}