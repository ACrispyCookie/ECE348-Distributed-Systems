#ifndef INTERNAL_SERVER_H
#define INTERNAL_SERVER_H
#include "server_api.h"
#include <pthread.h>
#include <arpa/inet.h>

#define MAX_BUFFER_SIZE (5 * sizeof(uint32_t) + MAX_PAYLOAD_SIZE)

typedef enum MessageType {
    REQUEST,
    REPLY,
    ACKNOWLEDGE
} MessageTypeT;

typedef struct Packet {
    enum MessageType type;
    int clientId;
    int serviceId;
    unsigned int sequenceNumber;
    unsigned int payloadLength;
    void *payload;
} PacketT;

typedef struct Request {
    PacketT packet;

    int requestId;
    struct sockaddr_in clientAddr;
    socklen_t clientAddrLen;
} RequestT;

typedef struct RequestList {
    RequestT *req;
    struct RequestList *prev, *next;
} RequestElementT;

typedef struct ServiceRegistryElement {
    int serviceId;

    // Circular doubly-linked list
    RequestElementT *queue;

    pthread_mutex_t queueLock;
    pthread_cond_t queueCond;

    struct ServiceRegistryElement *next;
} ServiceRegistryT;

extern int isShuttingDown;
extern int socketFd;
extern int port;
extern char ip[INET_ADDRSTRLEN];

extern int nextReqId;

// Singly-linked list for all services
extern ServiceRegistryT *registry;
extern pthread_mutex_t registryLock;

// Circular doubly-linked list for requests currently being processed
extern RequestElementT *activeRequests;
extern pthread_mutex_t activeRequestsLock;

// Circular doubly-linked list for requests already sent but not ack'ed
extern RequestElementT *inFlightRequests;
extern pthread_mutex_t inFlightRequestsLock;

extern pthread_t dispatcherThread;

void *runDispatcherThread(void *args);

void *serializePacket(PacketT *packet, int *length);
void deserializePacket(void *buffer, PacketT *packet);
int getNextRequestId();

ServiceRegistryT *getService(int serviceId);
int addToService(RequestT *req);

RequestT *getActiveRequest(in_addr_t clientAddr, int sequenceNumber);
RequestT *popActiveRequest(int requestId);

RequestT *getInFlightRequest(in_addr_t clientAddr, int sequenceNumber);
int cleanOrphanedRequests(in_addr_t clientAddr, int clientId);
int removeInFlightRequest(in_addr_t clientAddr, int sequenceNumber);
int addInFlightRequest(RequestT *req);

void cleanRequestList(RequestElementT *list);
void cleanUp();

#endif