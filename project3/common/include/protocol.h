#ifndef PROTOCOL_H
#define PROTOCOL_H
#include <stdint.h>

typedef enum {
    OPEN,
    READ,
    WRITE,
    TRUNCATE,
    NOT_FOUND,
    ERROR
} PacketType;

typedef struct {
    PacketType type;
    uint32_t id;
    uint8_t is_response;
} PacketHeader;

// Requests
typedef struct { 
    char fname[4096];
} OpenReq;

typedef struct {
    uint32_t fid, offset, length;
    uint64_t cached_mtime, cached_mtimensec;
} ReadReq;  // cached_mtime=0 if not cached

typedef struct {
    uint32_t fid, offset, length;
} WriteReq;

typedef struct {
    uint32_t fid, length;
} TruncReq;

// Responses
typedef struct {
    uint32_t fid; 
    uint64_t mtime, mtimensec, size;
} OpenResp;  // also for WRITE/TRUNC

typedef struct {
    uint32_t fid, offset, length; 
    uint64_t mtime, mtimensec, size;
} ReadResp;

typedef struct {
    PacketHeader header;
    union {
        OpenReq   open_req;
        ReadReq   read_req;
        WriteReq  write_req;
        TruncReq  trunc_req;
        OpenResp  open_resp;   // reuse for WRITE/TRUNC responses too
        ReadResp  read_resp;
    } body;
    uint32_t payload_length;
    void *payload;
} PacketT;

PacketT *req_deserialize(const void *buf, uint32_t length);
void *req_serialize(const PacketT *request, uint32_t *length);

#endif