#ifndef PROTOCOL_H
#define PROTOCOL_H

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
    unsigned int sequence_number;
    unsigned int fid;
    unsigned int length;
    unsigned int offset;
    unsigned int payload_length;
    void *payload;
} PacketT;

PacketT *req_deserialize(void *buf, unsigned int length);
void *req_serialize(PacketT *request, unsigned int *length);

#endif