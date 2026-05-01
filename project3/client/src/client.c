#include "client.h"
#include "cache.h"
#include <stdio.h>
#include <pthread.h>
#include <time.h>
#include <errno.h>
#include <unistd.h>

static volatile int shutting_down = 0;
static int last_fid = 0;
static int last_request_id = 0;
static int block_size;

static char *address;
static int port;

static int sockfd;
static struct sockaddr_in server_addr;
static socklen_t server_addrlen;

static ClientFileT *client_files = NULL;
static ServerFileT *server_files = NULL;
static RequestT *waiting = NULL;
static pthread_mutex_t lock;
static pthread_t receiver;

// Caller must hold lock
PacketT *req_send(PacketT *packet);
// Caller must hold lock
PacketT *req_send_with_reopen(PacketT *packet, ServerFileT *server_file);
void *run_receiver(void *args);
PacketT create_packet(PacketType type);
int get_next_req_id();
int get_next_fid();

int mynfs_init(char *server_address, int server_port, int block_s, int max_blocks, int fresh_time) {
    address = server_address;
    port = server_port;
    block_size = block_s;
    cache_init(max_blocks, block_s, fresh_time);

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

    cache_destroy();

    pthread_mutex_destroy(&lock);
}

int mynfs_open(char *fname) {
    pthread_mutex_lock(&lock);

    ServerFileT *found;
    HASH_FIND_STR(server_files, fname, found);
    if (found == NULL) {

        PacketT packet = create_packet(OPEN);
        strncpy(packet.body.open_req.fname, fname, 255);
        packet.body.open_req.fname[255] = '\0';
        PacketT *response = req_send(&packet);
        if (response == NULL || response->header.type != OPEN) {
            pthread_mutex_unlock(&lock);
            return -1;
        }

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
    ClientFileT *client_file;
    HASH_FIND_INT(client_files, &fd, client_file);
    if (client_file == NULL) {
        pthread_mutex_unlock(&lock);
        return -1;
    }
    
    unsigned int server_fid = client_file->server_file->fid;
    unsigned int block_index = client_file->pos / block_size;
    unsigned int block_offset = client_file->pos % block_size;
    long bytes_to_copy = (n > block_size - (long)block_offset)
                         ? block_size - block_offset
                         : n;
#ifdef DEBUG
    printf("[DEBUG] Read requested for file %d at block %d for %ld bytes\n", fd, block_index, bytes_to_copy);
#endif

    CacheBlockT *cached = cache_get(server_fid, block_index);
    if (cached && cache_is_fresh(cached)) {
#ifdef DEBUG
        printf("[DEBUG] Block is cached and fresh returning cached...\n");
#endif
        long available = (long)cached->data_len - block_offset;
        if (available <= 0) { 
            pthread_mutex_unlock(&lock); 
            return 0; 
        }
        if (bytes_to_copy > available) 
            bytes_to_copy = available;
        memcpy(buf, cached->data + block_offset, bytes_to_copy);
        client_file->pos += bytes_to_copy;
        pthread_mutex_unlock(&lock);
        return bytes_to_copy;
    }

    PacketT packet = create_packet(READ);
    packet.body.read_req.fid = client_file->server_file->fid;
    packet.body.read_req.cached_mtime = cached ? cached->mtime : 0;
    packet.body.read_req.length = block_size;
    packet.body.read_req.offset = block_index * block_size;
    PacketT *resp = req_send_with_reopen(&packet, client_file->server_file);
    if (resp == NULL || resp->header.type == ERROR) {
        if (resp)
            free(resp);
        pthread_mutex_unlock(&lock);
        return -1;
    }

    long bytes_read;
    if (cached && resp->body.read_resp.mtime == cached->mtime) { // Block still valid, refresh and serve
#ifdef DEBUG
        printf("[DEBUG] Block is cached, not fresh but still valid...\n");
#endif
        cache_revalidate(cached);

        long available = cached->data_len - block_offset;
        if (available <= 0) {
            free(resp);
            pthread_mutex_unlock(&lock);
            return 0;
        }
        if (bytes_to_copy > available)
            bytes_to_copy = available;

        memcpy(buf, cached->data + block_offset, bytes_to_copy);
        bytes_read = bytes_to_copy;
    } else { // Block was invalid, replace
#ifdef DEBUG
        printf("[DEBUG] Block invalid, caching it...\n");
#endif
        cache_put(server_fid, block_index,
                  resp->payload, resp->body.read_resp.length,
                  resp->body.read_resp.mtime);
        client_file->server_file->mtime = resp->body.read_resp.mtime;
        client_file->server_file->size = resp->body.read_resp.size;

        long available = (long)resp->body.read_resp.length - block_offset;
        if (available <= 0) {
            free(resp);
            pthread_mutex_unlock(&lock);
            return 0;
        }
        if (bytes_to_copy > available)
            bytes_to_copy = available;

        memcpy(buf, (unsigned char *)resp->payload + block_offset, bytes_to_copy);
        bytes_read = bytes_to_copy;
        free(resp->payload);
    }

    free(resp);
    client_file->pos += bytes_read;
    pthread_mutex_unlock(&lock);
    return bytes_read;
}

long mynfs_write(int fd, void *buf, long n) {
    pthread_mutex_lock(&lock);
    ClientFileT *client_file;
    HASH_FIND_INT(client_files, &fd, client_file);
    if (client_file == NULL) {
        pthread_mutex_unlock(&lock);
        return -1;
    }

#ifdef DEBUG
    printf("[DEBUG] Write requested for file %d at %ld for %ld bytes\n", fd, client_file->pos, n);
#endif

    PacketT packet = create_packet(WRITE);
    packet.body.write_req.fid = client_file->server_file->fid;
    packet.body.write_req.length = n;
    packet.body.write_req.offset = client_file->pos;
    packet.payload_length = n;
    packet.payload = malloc(n);
    if (packet.payload == NULL) {
        pthread_mutex_unlock(&lock);
        return -1;
    }

    memcpy(packet.payload, buf, n);
    PacketT *resp = req_send_with_reopen(&packet, client_file->server_file);
    free(packet.payload);
    if (resp == NULL || resp->header.type == ERROR) {
        if (resp)
            free(resp);
        pthread_mutex_unlock(&lock);
        return -1;
    }
    client_file->server_file->mtime = resp->body.open_resp.mtime;
    client_file->server_file->size = resp->body.open_resp.size;

    unsigned int server_fid = client_file->server_file->fid;
    long current_pos = client_file->pos;
    long remaining = n;
    long buf_offset = 0;

    while (remaining > 0) {
        unsigned int block_index = current_pos / block_size;
        unsigned int block_offset = current_pos % block_size;
        
        long bytes_in_block = block_size - block_offset;
        if (bytes_in_block > remaining)
            bytes_in_block = remaining;

        CacheBlockT *cached = cache_get(server_fid, block_index);
        if (cached) {
            memcpy(cached->data + block_offset, (unsigned char*)buf + buf_offset, bytes_in_block);
            if (block_offset + bytes_in_block > cached->data_len)
                cached->data_len = block_offset + bytes_in_block;
            
            cached->mtime = resp->body.open_resp.mtime;
            cache_revalidate(cached);

        } else if (block_offset == 0 && bytes_in_block == block_size) {
            cache_put(server_fid, block_index, 
                      (unsigned char*)buf + buf_offset, bytes_in_block, 
                      resp->body.open_resp.mtime);
        }
        current_pos += bytes_in_block;
        buf_offset += bytes_in_block;
        remaining -= bytes_in_block;
    }
    
    client_file->pos += n;
    free(resp);
    pthread_mutex_unlock(&lock);
    return n;
}

long mynfs_lseek(int fd, long pos, int whence) {
    pthread_mutex_lock(&lock);
    ClientFileT *client_file;
    HASH_FIND_INT(client_files, &fd, client_file);
    if (client_file == NULL) {
        pthread_mutex_unlock(&lock);
        return -1;
    }

    long before_pos = client_file->pos;
    switch (whence) {
    case SEEK_SET:
        client_file->pos = pos;
        break;
    case SEEK_CUR:
        client_file->pos += pos;
        break;
    case SEEK_END:
        client_file->pos = client_file->server_file->size + pos;
        break;
    default:
        pthread_mutex_unlock(&lock);
        return -1;
    }

    long return_value = client_file->pos < 0 ? -1 : client_file->pos;
    client_file->pos = client_file->pos < 0 ? before_pos : client_file->pos;
    pthread_mutex_unlock(&lock);

    return return_value;
}

int mynfs_truncate(int fd, long length) {
    if (length < 0) 
        return -1;
    
    pthread_mutex_lock(&lock);
    ClientFileT *client_file;
    HASH_FIND_INT(client_files, &fd, client_file);
    if (client_file == NULL) {
        pthread_mutex_unlock(&lock);
        return -1;
    }

    PacketT packet = create_packet(TRUNCATE);
    packet.body.trunc_req.fid = client_file->server_file->fid;
    packet.body.trunc_req.length = length;
    PacketT *response = req_send_with_reopen(&packet, client_file->server_file);
    if (response == NULL || response->header.type == ERROR) {
        if (response)
            free(response);
        pthread_mutex_unlock(&lock);
        return -1;
    }
    cache_invalidate_past_offset(client_file->server_file->fid, length);

    client_file->server_file->mtime = response->body.open_resp.mtime;
    client_file->server_file->size = response->body.open_resp.size;
    free(response);
    pthread_mutex_unlock(&lock);
    return 0;
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

PacketT *req_send_with_reopen(PacketT *packet, ServerFileT *server_file) {
    PacketT *response = req_send(packet);

    int reopen_attempts = 0;
    while (response != NULL && response->header.type == NOT_FOUND && reopen_attempts < MAX_REOPEN_RETRIES) {
#ifdef DEBUG
        printf("[DEBUG] File %d not found, reopening...\n", server_file->fid);
#endif
        reopen_attempts++;
        free(response->payload);
        free(response);

        PacketT reopen = create_packet(OPEN);
        strncpy(reopen.body.open_req.fname, server_file->filename, 255);
        reopen.body.open_req.fname[255] = '\0';
        PacketT *reopen_resp = req_send(&reopen);

        if (reopen_resp == NULL || reopen_resp->header.type != OPEN) {
            if (reopen_resp) {
                free(reopen_resp->payload);
                free(reopen_resp);
            }
            return NULL;
        }
#ifdef DEBUG
        printf("[DEBUG] Reopen success new ID: %d\n", reopen_resp->body.open_resp.fid);
#endif

        if (server_file->fid != reopen_resp->body.open_resp.fid)
            cache_remap(server_file->fid, reopen_resp->body.open_resp.fid);
        server_file->fid = reopen_resp->body.open_resp.fid;
        server_file->mtime = reopen_resp->body.open_resp.mtime;
        server_file->size = reopen_resp->body.open_resp.size;
        free(reopen_resp->payload);
        free(reopen_resp);

        switch (packet->header.type) {
            case READ: packet->body.read_req.fid = server_file->fid; break;
            case WRITE: packet->body.write_req.fid = server_file->fid; break;
            case TRUNCATE: packet->body.trunc_req.fid  = server_file->fid; break;
            default: break;
        }
        response = req_send(packet);
    }

    if (response != NULL && response->header.type == NOT_FOUND) {
        free(response->payload);
        free(response);
        return NULL;
    }
    return response;
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
    if (!buf || buf_len == 0) {
        pthread_cond_destroy(&req.cond);
        return NULL;
    }

    struct timespec ts;
    HASH_ADD(hh, waiting, id, sizeof(uint32_t), &req);

    int retries = 0;
    while (req.resp == NULL && retries < MAX_RETRIES && !shutting_down) {
        ssize_t sent = sendto(sockfd, buf, buf_len, 0,
                             (struct sockaddr *)&server_addr,
                             server_addrlen);
        if (sent != (ssize_t)buf_len) {
            HASH_DEL(waiting, &req);
            free(buf);
            pthread_cond_destroy(&req.cond);
            return NULL;
        }

        clock_gettime(CLOCK_REALTIME, &ts);
        ts.tv_sec += TIMEOUT_SEC;

        int rc = 0;
        while (req.resp == NULL && rc != ETIMEDOUT) {
            rc = pthread_cond_timedwait(&req.cond, &lock, &ts);
        }

        if (req.resp != NULL) break;
        if (rc == ETIMEDOUT) { 
            retries++;      
#ifdef DEBUG
            printf("[DEBUG] Request timeout retrying...\n");
#endif
        }
    }
    HASH_DEL(waiting, &req);

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
    return __atomic_fetch_add(&last_fid, 1, __ATOMIC_SEQ_CST) + 1;
}