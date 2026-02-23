#include <stddef.h>

// Initialize client
void init(char *config);

/*
    Sends a request and block waiting for a reply.

    Returns:
    0 - If the response was successfully received
    -1 - If it failed to receive a response
*/
int doRequestReply(int svcid, void *reqbuf, size_t reqlen, void *rspbuf, size_t *rsplen);