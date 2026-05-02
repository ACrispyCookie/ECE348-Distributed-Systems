#include "protocol.h"
#include <stddef.h>
#include <arpa/inet.h>
#include <stdlib.h>
#include <string.h>

PacketT *req_deserialize(const void *buf, uint32_t length) {
    if (!buf || length < sizeof(PacketHeader)) return NULL;

    PacketT *packet = malloc(sizeof(PacketT));
    if (!packet) return NULL;

    const unsigned char *p = buf;

    /* Header */
    uint32_t tmp;
    memcpy(&tmp, p, 4); packet->header.type = (PacketType)ntohl(tmp); p += 4;
    memcpy(&tmp, p, 4); packet->header.id = ntohl(tmp); p += 4;
    packet->header.is_response = *(p++);
    packet->payload_length = 0;

    /* Body */
    if (!packet->header.is_response) {
        switch (packet->header.type) {
            case OPEN: {
                memcpy(packet->body.open_req.fname, p, sizeof(packet->body.open_req.fname));
                break;
            }
            case READ: {
                ReadReq *r = &packet->body.read_req;
                memcpy(&tmp, p,    4); r->fid    = ntohl(tmp);
                memcpy(&tmp, p+4,  4); r->offset = ntohl(tmp);
                memcpy(&tmp, p+8,  4); r->length = ntohl(tmp);
                uint64_t mtime; memcpy(&mtime, p+12, 8); r->cached_mtime = be64toh(mtime);
                uint64_t mtimensec; memcpy(&mtimensec, p+20, 8); r->cached_mtimensec = be64toh(mtimensec);
                break;
            }
            case WRITE: {
                WriteReq *w = &packet->body.write_req;
                memcpy(&tmp, p,   4); w->fid    = ntohl(tmp);
                memcpy(&tmp, p+4, 4); w->offset = ntohl(tmp);
                memcpy(&tmp, p+8, 4); w->length = ntohl(tmp);
                packet->payload_length = w->length;
                break;
            }
            case TRUNCATE: {
                TruncReq *t = &packet->body.trunc_req;
                memcpy(&tmp, p,   4); t->fid    = ntohl(tmp);
                memcpy(&tmp, p+4, 4); t->length = ntohl(tmp);
                break;
            }
            default: break;
        }
    } else {
        switch (packet->header.type) {
            case OPEN:
            case WRITE:
            case TRUNCATE: {
                OpenResp *r = &packet->body.open_resp;
                memcpy(&tmp, p, 4); r->fid = ntohl(tmp);
                uint64_t mtime, mtimensec, size;
                memcpy(&mtime, p+4, 8); r->mtime = be64toh(mtime);
                memcpy(&mtimensec, p+12, 8); r->mtimensec = be64toh(mtimensec);
                memcpy(&size,  p+20, 8); r->size  = be64toh(size);
                break;
            }
            case READ: {
                ReadResp *r = &packet->body.read_resp;
                memcpy(&tmp, p,    4); r->fid    = ntohl(tmp);
                memcpy(&tmp, p+4,  4); r->offset = ntohl(tmp);
                memcpy(&tmp, p+8,  4); r->length = ntohl(tmp);
                uint64_t mtime; memcpy(&mtime, p+12, 8); r->mtime = be64toh(mtime);
                uint64_t mtimensec; memcpy(&mtimensec, p+20, 8); r->mtimensec = be64toh(mtimensec);
                uint64_t size; memcpy(&size, p+28, 8); r->size = be64toh(size);
                packet->payload_length = r->length;
                break;
            }
            case NOT_FOUND:
            case ERROR:
            default: break;
        }
    }
    p += sizeof(packet->body);

    /* Payload */
    if (packet->payload_length > 0) {
        packet->payload = malloc(packet->payload_length);
        if (!packet->payload) { free(packet); return NULL; }
        memcpy(packet->payload, p, packet->payload_length);
    } else {
        packet->payload = NULL;
    }

    return packet;
}

void *req_serialize(const PacketT *packet, uint32_t *out_len) {
    if (!packet || !out_len) return NULL;

    uint32_t body_size = sizeof(packet->body);
    uint32_t total_len = sizeof(PacketHeader) + body_size + packet->payload_length;

    unsigned char *buf = malloc(total_len);
    if (!buf) return NULL;
    memset(buf, 0, total_len);
    unsigned char *p = buf;

    /* Header */
    uint32_t tmp = htonl((uint32_t)packet->header.type);
    memcpy(p, &tmp, 4); p += 4;
    tmp = htonl(packet->header.id);
    memcpy(p, &tmp, 4); p += 4;
    *(p++) = packet->header.is_response;

    /* Body */
    if (!packet->header.is_response) {
        switch (packet->header.type) {
            case OPEN: {
                memcpy(p, packet->body.open_req.fname, sizeof(packet->body.open_req.fname));
                break;
            }
            case READ: {
                const ReadReq *r = &packet->body.read_req;
                tmp = htonl(r->fid);    memcpy(p,    &tmp, 4);
                tmp = htonl(r->offset); memcpy(p+4,  &tmp, 4);
                tmp = htonl(r->length); memcpy(p+8,  &tmp, 4);
                uint64_t mtime = htobe64(r->cached_mtime);
                memcpy(p+12, &mtime, 8);
                uint64_t mtimensec = htobe64(r->cached_mtimensec);
                memcpy(p+20, &mtimensec, 8);
                break;
            }
            case WRITE: {
                const WriteReq *w = &packet->body.write_req;
                tmp = htonl(w->fid);    memcpy(p,   &tmp, 4);
                tmp = htonl(w->offset); memcpy(p+4, &tmp, 4);
                tmp = htonl(w->length); memcpy(p+8, &tmp, 4);
                break;
            }
            case TRUNCATE: {
                const TruncReq *t = &packet->body.trunc_req;
                tmp = htonl(t->fid);    memcpy(p,   &tmp, 4);
                tmp = htonl(t->length); memcpy(p+4, &tmp, 4);
                break;
            }
            default: break;
        }
    } else {
        switch (packet->header.type) {
            case OPEN:
            case WRITE:
            case TRUNCATE: {
                /* OpenResp is reused for WRITE and TRUNCATE responses */
                const OpenResp *r = &packet->body.open_resp;
                tmp = htonl(r->fid); memcpy(p, &tmp, 4);
                uint64_t mtime = htobe64(r->mtime);
                memcpy(p+4, &mtime, 8);
                uint64_t mtimensec = htobe64(r->mtimensec);
                memcpy(p+12, &mtimensec, 8);
                uint64_t size = htobe64(r->size);
                memcpy(p+20, &size, 8);
                break;
            }
            case READ: {
                const ReadResp *r = &packet->body.read_resp;
                tmp = htonl(r->fid);    memcpy(p,    &tmp, 4);
                tmp = htonl(r->offset); memcpy(p+4,  &tmp, 4);
                tmp = htonl(r->length); memcpy(p+8,  &tmp, 4);
                uint64_t mtime = htobe64(r->mtime);
                memcpy(p+12, &mtime, 8);
                uint64_t mtimensec = htobe64(r->mtimensec);
                memcpy(p+20, &mtimensec, 8);
                uint64_t size = htobe64(r->size);
                memcpy(p+28, &size, 8);
                break;
            }
            default: break;
        }
    }
    p += body_size;

    /* Payload */
    if (packet->payload_length > 0 && packet->payload)
        memcpy(p, packet->payload, packet->payload_length);

    *out_len = total_len;
    return buf;
}