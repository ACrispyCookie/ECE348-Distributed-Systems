#include "server_api.h"
#include "server_app.h"
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <string.h>

void handleSigInt(int sig) {
    printf("\nSIGINT received! Shutting down server...\n");
    triggerShutdown(); 
}

int main(int argc, char *argv[]) {
    int workerCount;
    pthread_t *workers;

    if (argc != 2 || sscanf(argv[1], "%d", &workerCount) != 1) {
        printf("Usage: %s <worker count>\n", argv[0]);
        return 1;
    }
    workers = (pthread_t *) malloc(workerCount * sizeof(pthread_t));

    signal(SIGINT, handleSigInt);

    init();
    registerService(PRIMALITY);

    for (int i = 0; i < workerCount; i++)
        pthread_create(&workers[i], NULL, runWorker, NULL);

    printf("Server running with %d workers. Press Ctrl+C to stop.\n", workerCount);

    for (int i = 0; i < workerCount; i++) {
        pthread_join(workers[i], NULL);
    }

    unregisterService(PRIMALITY);
    cleanUp();
    free(workers);

    return 0;
}

void *runWorker(void *args) {
    while (1) {
        char buf[MAX_PAYLOAD_SIZE];
        int length;
        int requestId = getRequest(PRIMALITY, buf, &length);
        if(requestId == -1)
            break;

        AppRequestT *req = deserializeAppPacket(buf, length);
        AppResponseT *res = (AppResponseT *) malloc(sizeof(AppResponseT));
        res->count = req->count;
        res->booleans = (int *) malloc(res->count * sizeof(int));

        for (int i = 0; i < req->count; i++)
            res->booleans[i] = isPrime(req->numbers[i]);

        int serializedLength;
        void *serialized = serializeAppPacket(res, &serializedLength);
        sendReply(requestId, serialized, serializedLength);

        free(serialized);
        free(res->booleans);
        free(req->numbers);
        free(res);
        free(req);
    }
    return NULL;
}

AppRequestT *deserializeAppPacket(void *request, int length) {
    AppRequestT *req = (AppRequestT *) malloc(sizeof(AppRequestT));
    char *buf = (char *) request;
    int offset = 0;

    req->count = ntohl(*(uint32_t *)(buf + offset));
    offset += sizeof(uint32_t);

    req->numbers = (int *) malloc(req->count * sizeof(int));
    for (int i = 0; i < req->count; i++) {
        req->numbers[i] = ntohl(*(uint32_t *)(buf + offset));
        offset += sizeof(uint32_t);
    }

    return req;
}

void *serializeAppPacket(AppResponseT *response, int *length) {
    *length = (1 + response->count) * sizeof(uint32_t);

    char *buffer = (char *) malloc(*length);
    if (buffer == NULL) return NULL;

    int offset = 0;
    uint32_t net_count = htonl(response->count);
    memcpy(buffer + offset, &net_count, sizeof(uint32_t));
    offset += sizeof(uint32_t);

    for (int i = 0; i < response->count; i++) {
        uint32_t net_bool = htonl(response->booleans[i]);
        memcpy(buffer + offset, &net_bool, sizeof(uint32_t));
        offset += sizeof(uint32_t);
    }

    return (void *) buffer;
}

int isPrime(int num) {
    if (num <= 1) return 0;
    for (int i = 2; i * i <= num; i++) {
        if (num % i == 0)
            return 0;
    }
    return 1;
}