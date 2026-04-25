#include <stdio.h>
#include <string.h>
#include <assert.h>
#include <stdlib.h>
#include "protocol.h"

void test_write_roundtrip() {
    PacketT pkt = {0};
    pkt.header.type        = WRITE;
    pkt.header.seq         = 42;
    pkt.header.is_response = 0;

    pkt.body.write_req.fid    = 7;
    pkt.body.write_req.offset = 100;
    pkt.body.write_req.length = 5;

    char data[] = "hello";
    pkt.payload        = data;
    pkt.payload_length = 5;

    unsigned int len;
    void *buf = req_serialize(&pkt, &len);
    assert(buf != NULL);

    PacketT *out = req_deserialize(buf, len);
    assert(out != NULL);

    assert(out->header.type           == WRITE);
    assert(out->header.seq            == 42);
    assert(out->header.is_response    == 0);
    assert(out->body.write_req.fid    == 7);
    assert(out->body.write_req.offset == 100);
    assert(out->body.write_req.length == 5);
    assert(out->payload_length        == 5);
    assert(memcmp(out->payload, "hello", 5) == 0);

    printf("WRITE request roundtrip: OK\n");
    free(buf); free(out->payload); free(out);
}

void test_read_req_roundtrip() {
    PacketT pkt = {0};
    pkt.header.type        = READ;
    pkt.header.seq         = 10;
    pkt.header.is_response = 0;

    pkt.body.read_req.fid          = 3;
    pkt.body.read_req.offset       = 512;
    pkt.body.read_req.length       = 1024;
    pkt.body.read_req.cached_mtime = 1700000000ULL;  /* non-zero: client has cached block */
    pkt.payload        = NULL;
    pkt.payload_length = 0;

    unsigned int len;
    void *buf = req_serialize(&pkt, &len);
    assert(buf != NULL);

    PacketT *out = req_deserialize(buf, len);
    assert(out != NULL);

    assert(out->header.type                == READ);
    assert(out->header.seq                 == 10);
    assert(out->header.is_response         == 0);
    assert(out->body.read_req.fid          == 3);
    assert(out->body.read_req.offset       == 512);
    assert(out->body.read_req.length       == 1024);
    assert(out->body.read_req.cached_mtime == 1700000000ULL);
    assert(out->payload_length             == 0);
    assert(out->payload                    == NULL);

    printf("READ request roundtrip: OK\n");
    free(buf); free(out);
}

void test_open_req_roundtrip() {
    PacketT pkt = {0};
    pkt.header.type        = OPEN;
    pkt.header.seq         = 1;
    pkt.header.is_response = 0;

    strncpy(pkt.body.open_req.fname, "/home/user/test.txt", sizeof(pkt.body.open_req.fname));
    pkt.payload        = NULL;
    pkt.payload_length = 0;

    unsigned int len;
    void *buf = req_serialize(&pkt, &len);
    assert(buf != NULL);

    PacketT *out = req_deserialize(buf, len);
    assert(out != NULL);

    assert(out->header.type        == OPEN);
    assert(out->header.seq         == 1);
    assert(out->header.is_response == 0);
    assert(strncmp(out->body.open_req.fname, "/home/user/test.txt",
                   sizeof(out->body.open_req.fname)) == 0);
    assert(out->payload_length == 0);
    assert(out->payload        == NULL);

    printf("OPEN request roundtrip: OK\n");
    free(buf); free(out);
}

void test_truncate_req_roundtrip() {
    PacketT pkt = {0};
    pkt.header.type        = TRUNCATE;
    pkt.header.seq         = 7;
    pkt.header.is_response = 0;

    pkt.body.trunc_req.fid    = 2;
    pkt.body.trunc_req.length = 4096;
    pkt.payload        = NULL;
    pkt.payload_length = 0;

    unsigned int len;
    void *buf = req_serialize(&pkt, &len);
    assert(buf != NULL);

    PacketT *out = req_deserialize(buf, len);
    assert(out != NULL);

    assert(out->header.type           == TRUNCATE);
    assert(out->header.seq            == 7);
    assert(out->header.is_response    == 0);
    assert(out->body.trunc_req.fid    == 2);
    assert(out->body.trunc_req.length == 4096);
    assert(out->payload_length        == 0);
    assert(out->payload               == NULL);

    printf("TRUNCATE request roundtrip: OK\n");
    free(buf); free(out);
}

void test_open_resp_roundtrip() {
    PacketT pkt = {0};
    pkt.header.type        = OPEN;
    pkt.header.seq         = 1;
    pkt.header.is_response = 1;

    pkt.body.open_resp.fid   = 5;
    pkt.body.open_resp.mtime = 1700000000ULL;
    pkt.body.open_resp.size  = 8192ULL;
    pkt.payload        = NULL;
    pkt.payload_length = 0;

    unsigned int len;
    void *buf = req_serialize(&pkt, &len);
    assert(buf != NULL);

    PacketT *out = req_deserialize(buf, len);
    assert(out != NULL);

    assert(out->header.type          == OPEN);
    assert(out->header.seq           == 1);
    assert(out->header.is_response   == 1);
    assert(out->body.open_resp.fid   == 5);
    assert(out->body.open_resp.mtime == 1700000000ULL);
    assert(out->body.open_resp.size  == 8192ULL);
    assert(out->payload_length       == 0);
    assert(out->payload              == NULL);

    printf("OPEN response roundtrip: OK\n");
    free(buf); free(out);
}

void test_read_resp_roundtrip() {
    PacketT pkt = {0};
    pkt.header.type        = READ;
    pkt.header.seq         = 10;
    pkt.header.is_response = 1;

    pkt.body.read_resp.fid    = 3;
    pkt.body.read_resp.offset = 512;
    pkt.body.read_resp.length = 5;
    pkt.body.read_resp.mtime  = 1700000000ULL;

    char data[] = "world";
    pkt.payload        = data;
    pkt.payload_length = 5;

    unsigned int len;
    void *buf = req_serialize(&pkt, &len);
    assert(buf != NULL);

    PacketT *out = req_deserialize(buf, len);
    assert(out != NULL);

    assert(out->header.type           == READ);
    assert(out->header.seq            == 10);
    assert(out->header.is_response    == 1);
    assert(out->body.read_resp.fid    == 3);
    assert(out->body.read_resp.offset == 512);
    assert(out->body.read_resp.length == 5);
    assert(out->body.read_resp.mtime  == 1700000000ULL);
    assert(out->payload_length        == 5);
    assert(memcmp(out->payload, "world", 5) == 0);

    printf("READ response roundtrip: OK\n");
    free(buf); free(out->payload); free(out);
}

void test_write_resp_roundtrip() {
    PacketT pkt = {0};
    pkt.header.type        = WRITE;
    pkt.header.seq         = 42;
    pkt.header.is_response = 1;

    pkt.body.open_resp.fid   = 7;
    pkt.body.open_resp.mtime = 1700000001ULL;
    pkt.body.open_resp.size  = 5ULL;
    pkt.payload        = NULL;
    pkt.payload_length = 0;

    unsigned int len;
    void *buf = req_serialize(&pkt, &len);
    assert(buf != NULL);

    PacketT *out = req_deserialize(buf, len);
    assert(out != NULL);

    assert(out->header.type          == WRITE);
    assert(out->header.seq           == 42);
    assert(out->header.is_response   == 1);
    assert(out->body.open_resp.fid   == 7);
    assert(out->body.open_resp.mtime == 1700000001ULL);
    assert(out->body.open_resp.size  == 5ULL);
    assert(out->payload_length       == 0);
    assert(out->payload              == NULL);

    printf("WRITE response roundtrip: OK\n");
    free(buf); free(out);
}

void test_error_roundtrip() {
    PacketT pkt = {0};
    pkt.header.type        = ERROR;
    pkt.header.seq         = 99;
    pkt.header.is_response = 1;
    pkt.payload        = NULL;
    pkt.payload_length = 0;

    unsigned int len;
    void *buf = req_serialize(&pkt, &len);
    assert(buf != NULL);

    PacketT *out = req_deserialize(buf, len);
    assert(out != NULL);

    assert(out->header.type        == ERROR);
    assert(out->header.seq         == 99);
    assert(out->header.is_response == 1);
    assert(out->payload_length     == 0);
    assert(out->payload            == NULL);

    printf("ERROR response roundtrip: OK\n");
    free(buf); free(out);
}

void test_not_found_roundtrip() {
    PacketT pkt = {0};
    pkt.header.type        = NOT_FOUND;
    pkt.header.seq         = 3;
    pkt.header.is_response = 1;
    pkt.payload        = NULL;
    pkt.payload_length = 0;

    unsigned int len;
    void *buf = req_serialize(&pkt, &len);
    assert(buf != NULL);

    PacketT *out = req_deserialize(buf, len);
    assert(out != NULL);

    assert(out->header.type        == NOT_FOUND);
    assert(out->header.seq         == 3);
    assert(out->header.is_response == 1);
    assert(out->payload_length     == 0);
    assert(out->payload            == NULL);

    printf("NOT_FOUND response roundtrip: OK\n");
    free(buf); free(out);
}

int main() {
    test_write_roundtrip();
    test_read_req_roundtrip();
    test_open_req_roundtrip();
    test_truncate_req_roundtrip();
    test_open_resp_roundtrip();
    test_read_resp_roundtrip();
    test_write_resp_roundtrip();
    test_error_roundtrip();
    test_not_found_roundtrip();
    printf("All tests passed.\n");
    return 0;
}