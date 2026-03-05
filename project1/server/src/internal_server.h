#ifndef INTERNAL_SERVER_H
#define INTERNAL_SERVER_H
#include "server_api.h"
#include <pthread.h>
#include <arpa/inet.h>

// Percentage of virtual packet loss
#define REQUEST_LOSS 0
#define REPLY_LOSS 0

#define MAX_BUFFER_SIZE (5 * sizeof(uint32_t) + MAX_PAYLOAD_SIZE)

typedef enum ID {
    CLIENT,
    REQ
} IdentifierT;

typedef enum Message {
    DISCOVER,
    HANDSHAKE,
    REQUEST,
    REPLY,
    ACKNOWLEDGE,
    CANCEL
} MessageT;

typedef struct Packet {
    MessageT type;
    unsigned int clientId;
    unsigned int serviceId;
    unsigned int sequenceNumber;
    unsigned int payloadLength;
    void *payload;
} PacketT;

typedef struct Request {
    PacketT packet;

    unsigned int id;
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
    unsigned int id;
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
extern int nextReqId, nextClientId;
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

// RequestT list functions
RequestListT *reqlist_create();
void reqlist_destroy(RequestListT *list);

// AppServiceT list functions
AppServiceListT *servicelist_create();
void servicelist_destroy(AppServiceListT *list);

AppServiceT *getService(unsigned int serviceId);
int addToService(RequestT *req);

RequestT *getActiveRequest(unsigned int clientId, unsigned int sequenceNumber);
RequestT *popActiveRequest(unsigned int requestId);

RequestT *getInFlightRequest(unsigned int clientId, unsigned int sequenceNumber);
int addInFlightRequest(RequestT *req);

int cleanAckedRequests(unsigned int clientId, unsigned int sequenceNumber);
int cleanOrphanedRequests(unsigned int clientId);

int generateId(IdentifierT type);
int checkChance(int chance);

#endif