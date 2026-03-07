#ifndef INTERNAL_SERVER_H
#define INTERNAL_SERVER_H
#include "server_api.h"
#include <stdatomic.h>
#include <pthread.h>
#include <arpa/inet.h>

// Percentage of virtual packet loss
#ifndef REQUEST_LOSS
#define REQUEST_LOSS 0
#endif

#ifndef REPLY_LOSS
#define REPLY_LOSS 0
#endif

#define MEMORY_FILE "memory.txt"
#define MAX_BUFFER_SIZE (5 * sizeof(uint32_t) + MAX_PAYLOAD_SIZE)

typedef enum ID {
    CLIENT_ID,
    REQUEST_ID
} IdentifierT;

typedef enum Message {
    REQUEST,
    REPLY,
    EXPIRED,
    NOT_SERVING,
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
    unsigned int refCount;
    unsigned int destroyed;
    RequestListT *queue;
} AppServiceT;

typedef struct AppServiceElement {
    AppServiceT *service;
    struct AppServiceElement *next;
} AppServiceElementT;

typedef struct AppServiceList {
    AppServiceElementT *list;
    pthread_mutex_t lock;
} AppServiceListT;

extern char ip[INET_ADDRSTRLEN];
extern int port, discoveryPort;
extern int mainSocket, discoverySocket;

extern int isShuttingDown;
extern int nextReqId, nextClientId;
extern pthread_t receiverThread, senderThread, discoveryThread;

// Linked list for all services
extern AppServiceListT *serviceRegistry;
// Linked list for requests currently being processed
extern RequestListT *activeRequests;
// Linked list for requests already sent but not ack'ed
extern RequestListT *cachedRequests; // TODO Make it timeout
// Linked list for all replies
extern RequestListT *replies;

// Handlers for receiver thread
void *runReceivingThread(void *args);
int receiveHandshake(RequestT *req);
int receiveRequest(RequestT *req);
int receiveAck(RequestT *req);
int receiveCancel(RequestT *req);

// Handlers for sender thread
void *runSenderThread(void *args);

// Handlers for discovery thread
void *runDiscoveryThread(void *args);

// Serialization functions
void *serializePacket(PacketT *packet, int *length);
void deserializePacket(void *buffer, PacketT *packet);
RequestT *deepCopy(RequestT *req);

#endif
