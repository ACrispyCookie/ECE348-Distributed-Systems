#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <stdint.h>
#include <fcntl.h>
#include <unistd.h>
#include <ctype.h>
#include "server.h"
#include <signal.h>
#include <time.h>

static volatile sig_atomic_t alarm_fired = 0;
static char mount[4096];
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
void print_request(RequestT *req);
void cleanup_stale_files();
void reschedule_alarm();

static void sigalrm_handler(int sig) {
    alarm_fired = 1;
}

int server_init(char *mountpoint, unsigned int port) {
    sockfd = -1;
    struct sockaddr_in addr;
    socklen_t addrlen = sizeof(addr);

    struct sigaction sa;
    sa.sa_handler = sigalrm_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;
    sigaction(SIGALRM, &sa, NULL);

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
    strcpy(mount, mountpoint);

    return port;
}

void server_shutdown() {
    FileTableT *entry, *tmp;
    HASH_ITER(hh, table, entry, tmp) {
        HASH_DEL(table, entry);
        close(entry->local_fid);
        free(entry);
    }
    
    if (memory_file)
        fclose(memory_file);
    close(sockfd);
}

void server_handle_next() {
    cleanup_stale_files();
    RequestT *received = req_receive();
    if (!received) return;

#ifdef DEBUG
    printf("Received new packet:\n");
    print_request(received);
#endif

    FileTableT *found;
    int return_code = 0;
    switch (received->packet->header.type) {
        case OPEN:
            return_code = handle_open(received);
            break;
        case READ:
            HASH_FIND_INT(table, &(received->packet->body.read_req.fid), found);
            if (!found) {
                handle_not_found(received);
                return;
            }
            found->last_access = time(NULL);
            return_code = handle_read(received, found);
            reschedule_alarm();
            break;
        case WRITE:
            HASH_FIND_INT(table, &(received->packet->body.write_req.fid), found);
            if (!found) {
                handle_not_found(received);
                return;
            }
            found->last_access = time(NULL);
            return_code = handle_write(received, found);
            reschedule_alarm();
            break;
        case TRUNCATE:
            HASH_FIND_INT(table, &(received->packet->body.trunc_req.fid), found);
            if (!found) {
                handle_not_found(received);
                return;
            }
            found->last_access = time(NULL);
            return_code = handle_truncate(received, found);
            reschedule_alarm();
            break;
        default:
            break;
    }

    if (return_code == -1) {
        received->packet->header.type = ERROR;
        free(received->packet->payload);
        received->packet->payload = NULL;
        received->packet->payload_length = 0;
        req_send(received);
    }
    free(received->packet);
    free(received);
}

int handle_open(RequestT *open_req) {
    char filename[4096];
    memcpy(filename, open_req->packet->body.open_req.fname, 4096);
    filename[4095] = '\0';
    if (strstr(filename, "..") != NULL)
        return -1;

    char path[4096];
    strcpy(path, mount);
    strcat(path, "/");
    strcat(path, filename);
    

    int fid = open(path, O_RDWR|O_CREAT, 0666);
    if (fid < 0)
        return -1;

    FileTableT *entry = malloc(sizeof(FileTableT));
    if (!entry)
        return -1;
    entry->local_fid = fid;
    entry->global_fid = get_next_id();
    entry->last_access = time(NULL);
    HASH_ADD_INT(table, global_fid, entry);

    struct stat attr;
    if (fstat(fid, &attr) < 0) {
        HASH_DEL(table, entry);
        free(entry);
        close(fid);
        return -1;
    }
    open_req->packet->body.open_resp.fid = entry->global_fid;
    open_req->packet->body.open_resp.size = attr.st_size;
    open_req->packet->body.open_resp.mtime = attr.st_mtim.tv_sec;
    open_req->packet->body.open_resp.mtimensec = attr.st_mtim.tv_nsec;
    req_send(open_req);
    reschedule_alarm();
    return 0;
}

int handle_read(RequestT *read_req, FileTableT *file) {
    int local_fid = file->local_fid;
    struct stat attr;
    if (fstat(local_fid, &attr) < 0)
        return -1;

    ReadReq *req = &read_req->packet->body.read_req;
    ReadResp *resp = &read_req->packet->body.read_resp;
    if (req->length == 0 || (req->cached_mtime == attr.st_mtim.tv_sec && req->cached_mtimensec == attr.st_mtim.tv_nsec)) {
        uint32_t fid = req->fid;
        uint32_t offset = req->offset;
        resp->fid = fid;
        resp->length = 0;
        resp->offset = offset;
        resp->mtime = attr.st_mtim.tv_sec;
        resp->mtimensec = attr.st_mtim.tv_nsec;
        read_req->packet->payload = NULL;
        read_req->packet->payload_length = 0;
        req_send(read_req);
        return 0;
    }

    int return_code = lseek(local_fid, req->offset, SEEK_SET);
    if (return_code < 0)
        return -1;
    void *buf = malloc(req->length);
    if (!buf)
        return -1;

    ssize_t bytes_read;
    size_t total_bytes = 0;
    while (total_bytes < req->length) { 
        bytes_read = read(local_fid, (char*)buf + total_bytes, req->length - total_bytes); 
        if (bytes_read < 0) {
            free(buf);
            return -1;
        } else if (bytes_read == 0)
            break;

        total_bytes += bytes_read; 
    }

    read_req->packet->payload_length = total_bytes;
    read_req->packet->payload = buf;
    uint32_t fid = req->fid;
    uint32_t offset = req->offset;
    resp->fid = fid;
    resp->length = total_bytes;
    resp->offset = offset;
    resp->mtime = attr.st_mtim.tv_sec;
    resp->mtimensec = attr.st_mtim.tv_nsec;
    resp->size = attr.st_size;
    req_send(read_req);
    free(buf);
    return 0;
}

int handle_write(RequestT *write_req, FileTableT *file) {
    int local_fid = file->local_fid;
    struct stat attr;
    if (fstat(local_fid, &attr) < 0)
        return -1;

    WriteReq *req = &write_req->packet->body.write_req;
    OpenResp *resp = &write_req->packet->body.open_resp;
    if (req->length == 0) {
        uint32_t fid = req->fid;
        resp->fid = fid;
        resp->size = attr.st_size;
        resp->mtime = attr.st_mtim.tv_sec;
        resp->mtimensec = attr.st_mtim.tv_nsec;
        req_send(write_req);
        return 0;
    }

    int return_code = lseek(local_fid, req->offset, SEEK_SET);
    if (return_code < 0)
        return -1;

    ssize_t bytes_written;
    size_t total_bytes = 0;
    while (total_bytes < req->length) { 
        bytes_written = write(local_fid, (char*)write_req->packet->payload + total_bytes, req->length - total_bytes); 
        if (bytes_written < 0) {
            return -1;
        } else if (bytes_written == 0)
            break;

        total_bytes += bytes_written;
    }
    if (fstat(local_fid, &attr) < 0)
        return -1;

    free(write_req->packet->payload);
    write_req->packet->payload = NULL;
    write_req->packet->payload_length = 0;
    uint32_t fid = req->fid;
    resp->fid = fid;
    resp->mtime = attr.st_mtim.tv_sec;
    resp->mtimensec = attr.st_mtim.tv_nsec;
    resp->size = attr.st_size;
    req_send(write_req);
    return 0;
}

int handle_truncate(RequestT *truncate_req, FileTableT *file) {
    int local_fid = file->local_fid;
    if (ftruncate(local_fid, truncate_req->packet->body.trunc_req.length) < 0)
        return -1;
    
    struct stat attr;
    if (fstat(local_fid, &attr) < 0)
        return -1;
    
    uint32_t fid = truncate_req->packet->body.trunc_req.fid;
    truncate_req->packet->body.open_resp.fid = fid;
    truncate_req->packet->body.open_resp.mtime = attr.st_mtim.tv_sec;
    truncate_req->packet->body.open_resp.mtimensec = attr.st_mtim.tv_nsec;
    truncate_req->packet->body.open_resp.size = attr.st_size;
    truncate_req->packet->payload_length = 0;
    truncate_req->packet->payload = NULL;
    req_send(truncate_req);
    return 0;
}

void handle_not_found(RequestT *req) {
    req->packet->header.type = NOT_FOUND;
    req_send(req);
    free(req->packet);
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
    request->packet->header.is_response = 1;

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

void print_request(RequestT *req) {
    if (!req) {
        printf("[RequestT] NULL\n");
        return;
    }

    printf("[RequestT]\n");
    printf("  client_addr: %s:%d\n",
           inet_ntoa(req->client_addr.sin_addr),
           ntohs(req->client_addr.sin_port));

    if (!req->packet) {
        printf("  packet: NULL\n");
        return;
    }

    PacketT *p = req->packet;
    const char *type_str;
    switch (p->header.type) {
        case OPEN:     type_str = "OPEN";     break;
        case READ:     type_str = "READ";     break;
        case WRITE:    type_str = "WRITE";    break;
        case TRUNCATE: type_str = "TRUNCATE"; break;
        case NOT_FOUND:type_str = "NOT_FOUND";break;
        case ERROR:    type_str = "ERROR";    break;
        default:       type_str = "UNKNOWN";  break;
    }
    printf("  header.type:        %s\n", type_str);
    printf("  header.id:          %u\n", p->header.id);
    printf("  header.is_response: %d\n", p->header.is_response);
    printf("  payload_length:     %u\n", p->payload_length);

    printf("  body:\n");
    if (!p->header.is_response) {
        switch (p->header.type) {
            case OPEN:
                printf("    fname: %.*s\n", 255, p->body.open_req.fname);
                break;
            case READ:
                printf("    fid:          %u\n", p->body.read_req.fid);
                printf("    offset:       %u\n", p->body.read_req.offset);
                printf("    length:       %u\n", p->body.read_req.length);
                printf("    cached_mtime: %lu\n", p->body.read_req.cached_mtime);
                printf("    cached_mtimensec: %lu\n", p->body.read_req.cached_mtimensec);
                break;
            case WRITE:
                printf("    fid:    %u\n", p->body.write_req.fid);
                printf("    offset: %u\n", p->body.write_req.offset);
                printf("    length: %u\n", p->body.write_req.length);
                break;
            case TRUNCATE:
                printf("    fid:    %u\n", p->body.trunc_req.fid);
                printf("    length: %u\n", p->body.trunc_req.length);
                break;
            default:
                break;
        }
    } else {
        switch (p->header.type) {
            case OPEN:
            case WRITE:
            case TRUNCATE:
                printf("    fid:   %u\n",  p->body.open_resp.fid);
                printf("    mtime: %lu\n", p->body.open_resp.mtime);
                printf("    mtimensec: %lu\n", p->body.open_resp.mtimensec);
                printf("    size:  %lu\n", p->body.open_resp.size);
                break;
            case READ:
                printf("    fid:    %u\n",  p->body.read_resp.fid);
                printf("    offset: %u\n",  p->body.read_resp.offset);
                printf("    length: %u\n",  p->body.read_resp.length);
                printf("    mtime:  %lu\n", p->body.read_resp.mtime);
                printf("    mtimensec:  %lu\n", p->body.read_resp.mtimensec);
                printf("    size:  %lu\n", p->body.read_resp.size);
                break;
            default:
                break;
        }
    }

    if (p->payload_length > 0 && p->payload) {
        printf("  payload (%u bytes): ", p->payload_length);
        /* print as text if printable, otherwise hex */
        int printable = 1;
        for (unsigned int i = 0; i < p->payload_length; i++) {
            if (!isprint(((unsigned char *)p->payload)[i])) {
                printable = 0;
                break;
            }
        }
        if (printable) {
            printf("%.*s\n", p->payload_length, (char *)p->payload);
        } else {
            for (unsigned int i = 0; i < p->payload_length; i++)
                printf("%02x ", ((unsigned char *)p->payload)[i]);
            printf("\n");
        }
    }
}

void cleanup_stale_files() {
    if (!alarm_fired) return;
    alarm_fired = 0;

    time_t now = time(NULL);
    FileTableT *entry, *tmp;
    HASH_ITER(hh, table, entry, tmp) {
        if (now - entry->last_access >= FILE_TIMEOUT_SEC) {
#ifdef DEBUG
            printf("Cleaning up file %u\n", entry->global_fid);
#endif
            close(entry->local_fid);
            HASH_DEL(table, entry);
            free(entry);
        }
    }

    reschedule_alarm();
}

void reschedule_alarm() {
    time_t now = time(NULL);
    time_t earliest = 0;

    FileTableT *entry, *tmp;
    HASH_ITER(hh, table, entry, tmp) {
        time_t expiry = entry->last_access + FILE_TIMEOUT_SEC;
        if (earliest == 0 || expiry < earliest)
            earliest = expiry;
    }

    if (earliest > 0) {
        unsigned int secs = earliest > now ? (unsigned int)(earliest - now) : 1;
#ifdef DEBUG
        printf("Running cleanup in %u seconds...\n", secs);
#endif
        alarm(secs);
    }
}