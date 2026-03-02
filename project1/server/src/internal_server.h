#ifndef INTERNAL_SERVER_H
#define INTERNAL_SERVER_H
#include "server_api.h"
#include <pthread.h>
#include <arpa/inet.h>

#define MAX_BUFFER_SIZE (5 * sizeof(uint32_t) + MAX_PAYLOAD_SIZE)

typedef enum Message {
    REQUEST,
    REPLY,
    ACKNOWLEDGE
} MessageT;

typedef struct Packet {
    MessageT type;
    int clientId;
    int serviceId;
    unsigned int sequenceNumber;
    unsigned int payloadLength;
    void *payload;
} PacketT;

typedef struct Request {
    PacketT packet;

    int id;
    struct sockaddr_in clientAddr;
    socklen_t clientAddrLen;
} RequestT;

typedef struct RequestElement {
    RequestT *req;
    struct RequestElement *prev, *next;
} RequestElementT;

typedef struct RequestList {
    RequestElementT *list;
    pthread_mutex_t lock;
    pthread_cond_t cond;
} RequestListT;

typedef struct AppService {
    int id;
    RequestListT *queue;
} AppServiceT;

typedef struct AppServiceElement {
    AppServiceT service;
    struct AppServiceElement *next;
} AppServiceElementT;

typedef struct AppServiceList {
    AppServiceElementT *list;
    pthread_mutex_t lock;
} AppServiceListT;

extern char ip[INET_ADDRSTRLEN];
extern int port;
extern int socketFd;

extern int isShuttingDown;
extern int nextReqId;
extern pthread_t dispatcherThread;

// Linked list for all services
extern AppServiceListT *serviceRegistry;
// Linked list for requests currently being processed
extern RequestListT *activeRequests;
// Linked list for requests already sent but not ack'ed
extern RequestListT *inFlightRequests;

void *runDispatcherThread(void *args);
void *serializePacket(PacketT *packet, int *length);
void deserializePacket(void *buffer, PacketT *packet);
int getNextRequestId();

// RequestT list functions
RequestListT *reqlist_create();
void reqlist_destroy(RequestListT *list);

// AppServiceT list functions
AppServiceListT *servicelist_create();
void servicelist_destroy(AppServiceListT *list);

AppServiceT *getService(int serviceId);
int addToService(RequestT *req);

RequestT *getActiveRequest(in_addr_t clientAddr, int sequenceNumber);
RequestT *popActiveRequest(int requestId);

RequestT *getInFlightRequest(in_addr_t clientAddr, int sequenceNumber);
int removeInFlightRequest(in_addr_t clientAddr, int sequenceNumber);
int addInFlightRequest(RequestT *req);

int cleanOrphanedRequests(in_addr_t clientAddr, int clientId);

#endif