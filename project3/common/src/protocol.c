#include "protocol.h"
#include <stddef.h>
#include <arpa/inet.h>
#include <stdlib.h>
#include <string.h>

PacketT *req_deserialize(void *buf, unsigned int length) {
    if (!buf || length < 4 + 6*4) return NULL;
    unsigned char *p = (unsigned char *)buf;
    uint32_t tmp;

    memcpy(&tmp, p, 4); p += 4;
    uint32_t total = ntohl(tmp);
    if ((uint32_t)length < 4 + total) return NULL;

    memcpy(&tmp, p, 4); p += 4;
    PacketType type = (PacketType)ntohl(tmp);

    memcpy(&tmp, p, 4); p += 4;
    unsigned int sequence_number = ntohl(tmp);

    memcpy(&tmp, p, 4); p += 4;
    unsigned int fid = ntohl(tmp);

    memcpy(&tmp, p, 4); p += 4;
    unsigned int plen = ntohl(tmp);

    memcpy(&tmp, p, 4); p += 4;
    unsigned int offset = ntohl(tmp);

    memcpy(&tmp, p, 4); p += 4;
    unsigned int payload_length = ntohl(tmp);

    if (payload_length > total) return NULL;

    PacketT *packet = malloc(sizeof(PacketT));
    if (!packet) return NULL;
    memset(packet, 0, sizeof(PacketT));
    packet->type = type;
    packet->sequence_number = sequence_number;
    packet->length = plen;
    packet->offset = offset;
    packet->fid = fid;
    packet->payload_length = payload_length;
    if (payload_length > 0) {
        packet->payload = malloc((size_t)payload_length);
        if (!packet->payload) { free(packet); return NULL; }
        memcpy(packet->payload, p, payload_length);
    } else {
        packet->payload = NULL;
    }

    return packet;
}

void *req_serialize(PacketT *packet, unsigned int *length) {
    if (!packet || !length) return NULL;
    uint32_t payload_len = packet->payload_length;
    uint32_t total = 6*4 + payload_len; /* six 32-bit fields + payload */
    uint32_t bufsize = 4 + total;
    unsigned char *buf = malloc(bufsize);
    if (!buf) return NULL;
    unsigned char *p = buf;

    uint32_t tmp_net = htonl(total);
    memcpy(p, &tmp_net, 4); p += 4;

    tmp_net = htonl((uint32_t)packet->type); memcpy(p, &tmp_net, 4); p += 4;
    tmp_net = htonl((uint32_t)packet->sequence_number); memcpy(p, &tmp_net, 4); p += 4;
    tmp_net = htonl((uint32_t)packet->fid); memcpy(p, &tmp_net, 4); p += 4;
    tmp_net = htonl((uint32_t)packet->length); memcpy(p, &tmp_net, 4); p += 4;
    tmp_net = htonl((uint32_t)packet->offset); memcpy(p, &tmp_net, 4); p += 4;
    tmp_net = htonl((uint32_t)packet->payload_length); memcpy(p, &tmp_net, 4); p += 4;

    if (payload_len > 0 && packet->payload) memcpy(p, packet->payload, payload_len);

    *length = bufsize;
    return buf;
}