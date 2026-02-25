#ifndef SERVER_API_H
#define SERVER_API_H
#include <arpa/inet.h>
#include <pthread.h>

#define MAX_PAYLOAD_SIZE 4096

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

// Initialize server
void init();

// Start shutdown process
void triggerShutdown();

// Clean up
void cleanUp();

/*
    Registers the specified service

    Returns:
    0 - If the registration is successful
    -1 - If the registration failed 
*/
int registerService(int serviceId);

/*
    Unregisters the specified service

    Returns:
    0 - If the registration is successful
    -1 - If the registration failed 
*/
int unregisterService(int serviceId);

/*
    Gets the next request.

    Returns:
    The request ID of the next request.
*/
int getRequest(int serviceId, void *buf, int *len);

/*
    Sends a reply for the specified request
*/
void sendReply(int requestId, void *buf, int len);

#endif