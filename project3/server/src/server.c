#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <stdint.h>
#include <fcntl.h>
#include <unistd.h>
#include "server.h"

static unsigned int last_fid;
static int sockfd;
static FILE *memory_file = NULL;
static FileTableT *table = NULL;

RequestT *req_receive();
int req_send(RequestT *request);

int handle_open(RequestT *open_req);
int handle_read(RequestT *read_req, FileTableT *file);
int handle_write(RequestT *write_req, FileTableT *file);
int handle_truncate(RequestT *truncate_req, FileTableT *file);
void handle_not_found(RequestT *req);

int get_next_id();

int server_init(char *mountpoint, unsigned int port) {
    sockfd = -1;
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

    // Initialize last_fid from memory.txt in the working directory or 0 if absent.
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
    if (memory_file)
        fclose(memory_file);
    close(sockfd);
}

void server_handle_next() {
    RequestT *received = req_receive();
    if (!received) return;

    FileTableT *found;
    HASH_FIND_INT(table, &(received->packet->fid), found);
    if (!found && received->packet->type != OPEN) {
        handle_not_found(received);
        return;
    }

    int return_code = 0;
    switch (received->packet->type) {
        case OPEN:
            return_code = handle_open(received);
            break;
        case READ:
            return_code = handle_read(received, found);
            break;
        case WRITE:
            return_code = handle_write(received, found);
            break;
        case TRUNCATE:
            return_code = handle_truncate(received, found);
            break;
        default:
            break;
    }

    if (return_code == -1) {
        received->packet->type = ERROR;
        free(received->packet->payload);
        received->packet->payload = NULL;
        received->packet->payload_length = 0;
        req_send(received);
    }
    free(received->packet);
    free(received);
}

int handle_open(RequestT *open_req) {
    if (open_req->packet->payload_length == 0)
        return -1;
    
    char *filename = malloc(open_req->packet->payload_length + 1);
    if (!filename)
        return -1;
    memcpy(filename, open_req->packet->payload, open_req->packet->payload_length);
    filename[open_req->packet->payload_length] = '\0';

    int fid = open(filename, O_RDWR|O_CREAT, 0666);
    if (fid < 0) {
        free(filename);
        return -1;
    }

    FileTableT *entry = malloc(sizeof(FileTableT));
    if (!entry) {
        free(filename);
        return -1;
    }
    entry->local_fid = fid;
    entry->global_fid = get_next_id();
    HASH_ADD_INT(table, global_fid, entry);

    free(filename);
    free(open_req->packet->payload);
    open_req->packet->payload_length = 0;
    open_req->packet->payload = NULL;
    open_req->packet->fid = entry->global_fid;
    req_send(open_req);
    return 0;
}

int handle_read(RequestT *read_req, FileTableT *file) {
    int local_fid = file->local_fid;
    if (read_req->packet->length == 0) {
        read_req->packet->payload = NULL;
        read_req->packet->payload_length = 0;
        req_send(read_req);
        return 0;
    }
    
    int return_code = lseek(local_fid, read_req->packet->offset, SEEK_SET);
    if (return_code < 0)
        return -1;
    void *buf = malloc(read_req->packet->length);
    if (!buf)
        return -1;

    ssize_t bytes_read;
    size_t total_bytes = 0;
    while (total_bytes < read_req->packet->length) { 
        bytes_read = read(local_fid, (char*)buf + total_bytes, read_req->packet->length - total_bytes); 
        if (bytes_read < 0) {
            free(buf);
            return -1;
        } else if (bytes_read == 0)
            break;

        total_bytes += bytes_read; 
    }

    read_req->packet->payload_length = total_bytes;
    read_req->packet->payload = buf;
    req_send(read_req);
    free(buf);
    return 0;
}

int handle_write(RequestT *write_req, FileTableT *file) {
    return 0;
}

int handle_truncate(RequestT *truncate_req, FileTableT *file) {
    int local_fid = file->local_fid;
    return ftruncate(local_fid, truncate_req->packet->length);
}

void handle_not_found(RequestT *req) {
    req->packet->type = NOT_FOUND;
    req_send(req);
    free(req);
}

RequestT *req_receive() {
    unsigned char *buf = NULL;
    ssize_t n = 0;
    struct sockaddr_in src_addr;
    socklen_t src_len = sizeof(src_addr);

    buf = malloc(65536);
    if (!buf) return NULL;

    n = recvfrom(sockfd, buf, 65536, 0, (struct sockaddr *)&src_addr, &src_len);
    if (n <= 0) {
        free(buf);
        return NULL;
    }

    RequestT *req = malloc(sizeof(RequestT));
    if (!req) {
        free(buf);
        return NULL;
    }

    req->packet = req_deserialize(buf, (unsigned int)n);
    free(buf);
    if (req->packet == NULL) {
        free(req);
        return NULL;
    }

    /* store sender address for replies */
    req->client_addr = src_addr;
    req->client_addrlen = src_len;

    return req;
}

int req_send(RequestT *request) {
    if (!request) return -1;

    unsigned int buf_len = 0;
    void *buf = req_serialize(request->packet, &buf_len);
    if (!buf || buf_len == 0) return -1;

    ssize_t sent = sendto(sockfd, buf, buf_len, 0,
                         (struct sockaddr *)&request->client_addr,
                         request->client_addrlen);
    free(buf);

    return (sent == (ssize_t)buf_len) ? 0 : -1;
}

int get_next_id() {
    unsigned int next = last_fid + 1;

    if (memory_file) {
        if (fseek(memory_file, 0, SEEK_SET) == 0) {
            fprintf(memory_file, "%u\n", next);
            fflush(memory_file);
            ftruncate(fileno(memory_file), ftell(memory_file));
        }
    }

    last_fid = next;
    return next;
}