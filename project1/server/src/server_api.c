#include "server_api.h"
#include "internal_server.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <pthread.h>

#define MAX_BUFFER_SIZE (5 * sizeof(uint32_t) + MAX_PAYLOAD_SIZE)

int isShuttingDown = 0;
int socketFd;
char ip[INET_ADDRSTRLEN];
int port;

int nextReqId = 0;

// Singly-linked list for all services
ServiceRegistryT *registry = NULL;
pthread_mutex_t registryLock;

// Circular doubly-linked list for requests currently being processed
RequestElementT *activeRequests = NULL;
pthread_mutex_t activeRequestsLock;

// Circular doubly-linked list for requests already sent but not ack'ed
RequestElementT *inFlightRequests = NULL;
pthread_mutex_t inFlightRequestsLock;

pthread_t dispatcherThread;

void init() {
    struct sockaddr_in server_addr;
    socklen_t len = sizeof(server_addr);
    memset(&server_addr, 0, sizeof(server_addr)); // TODO figure out if this is needed
    
    socketFd = socket(AF_INET, SOCK_DGRAM, 0);
    server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    server_addr.sin_port = htons(25565);
    server_addr.sin_family = AF_INET;

    // Bind to port and get info on address
    if (bind(socketFd, (struct sockaddr *) &server_addr, len) < 0) {
        perror("Bind failed");
        close(socketFd);
        exit(EXIT_FAILURE);
    }

    if (getsockname(socketFd, (struct sockaddr *) &server_addr, &len) < 0) {
        perror("getsockname failed");
        close(socketFd);
        exit(EXIT_FAILURE);
    }

    if (inet_ntop(AF_INET, &(server_addr.sin_addr), ip, INET_ADDRSTRLEN) == NULL) {
        perror("inet_ntop failed");
        close(socketFd);
        exit(EXIT_FAILURE);
    }
    port = ntohs(server_addr.sin_port);

    // Initialize mutexes
    if (pthread_mutex_init(&registryLock, NULL) != 0) {
        perror("pthread_mutex_init failed");
        close(socketFd);
        exit(EXIT_FAILURE);
    }

    if (pthread_mutex_init(&activeRequestsLock, NULL) != 0) {
        perror("pthread_mutex_init failed");
        close(socketFd);
        exit(EXIT_FAILURE);
    }

    if (pthread_mutex_init(&inFlightRequestsLock, NULL) != 0) {
        perror("pthread_mutex_init failed");
        close(socketFd);
        exit(EXIT_FAILURE);
    }

    if (pthread_create(&dispatcherThread, NULL, runDispatcherThread, NULL)) {
        perror("pthread_create failed");
        close(socketFd);
        exit(EXIT_FAILURE);
    }

    printf("Started server at %s:%d\n", ip, port);
}

int registerService(int serviceId) {
    pthread_mutex_lock(&registryLock);

    // Check for existing registry with same ID
    ServiceRegistryT *el;
    LL_SEARCH_SCALAR(registry, el, serviceId, serviceId);
    if (el) {
        pthread_mutex_unlock(&registryLock);
        return -1;
    }

    // Allocate new node
    ServiceRegistryT *newNode = (ServiceRegistryT *) malloc(sizeof(ServiceRegistryT));
    if (newNode == NULL) {
        pthread_mutex_unlock(&registryLock);
        return -1;
    }
    
    // Initialize new node
    newNode->serviceId = serviceId;
    newNode->queue = NULL;
    pthread_mutex_init(&(newNode->queueLock), NULL);
    pthread_cond_init(&(newNode->queueCond), NULL);

    // Add to the registry
    LL_PREPEND(registry, newNode);

    pthread_mutex_unlock(&registryLock);
    return 0;
}

int unregisterService(int serviceId) {
    pthread_mutex_lock(&registryLock);

    // Search and remove the registry
    ServiceRegistryT *el;
    LL_SEARCH_SCALAR(registry, el, serviceId, serviceId);
    if (el) {
        LL_DELETE(registry,el);
        pthread_cond_destroy(&el->queueCond);
        pthread_mutex_destroy(&el->queueLock);
        cleanRequestList(el->queue);
        free(el);
        pthread_mutex_unlock(&registryLock);
        return 0;
    }

    pthread_mutex_unlock(&registryLock);
    return -1;
}

int getRequest(int serviceId, void *buf, int *len) {
    ServiceRegistryT *service = getService(serviceId);
    if (service == NULL) 
        return -1;

    pthread_mutex_lock(&service->queueLock);
    // Wait for a packet to arrive
    while (service->queue == NULL && !isShuttingDown)
        pthread_cond_wait(&service->queueCond, &service->queueLock);

    if (isShuttingDown) {
        pthread_mutex_unlock(&service->queueLock);
        return -1;
    }

    // Remove packet from queue
    RequestElementT *el = service->queue;
    CDL_DELETE(service->queue, el);
    pthread_mutex_unlock(&service->queueLock);

    // Copy to user buffers and free payload
    PacketT *packet = &el->req->packet;
    memcpy(buf, packet->payload, packet->payloadLength);
    if (len)
        *len = packet->payloadLength;
    free(packet->payload);
    packet->payload = NULL;
    packet->payloadLength = 0;

    // Move packet to active requests
    pthread_mutex_lock(&activeRequestsLock);
    CDL_APPEND(activeRequests, el);
    pthread_mutex_unlock(&activeRequestsLock);

    return el->req->requestId;
}

void sendReply(int requestId, void *buf, int len) {
    RequestT *req = popActiveRequest(requestId);
    if (req == NULL)
        return;

    // Copy user buffer to our packet buffer
    PacketT *packet = &req->packet;
    packet->type = REPLY;
    packet->payloadLength = len;
    packet->payload = malloc(len);
    memcpy(packet->payload, buf, len);

    if (addInFlightRequest(req) == -1) {
        free(packet->payload);
        free(req);
        return;
    }
    
    int serializedLength;
    void *serialized = serializePacket(packet, &serializedLength);
    sendto(socketFd, serialized, serializedLength, 0, (struct sockaddr *) &req->clientAddr, req->clientAddrLen);
    free(serialized);
}

void *runDispatcherThread(void *args) {
    while (1) {
        RequestT *req = malloc(sizeof(RequestT));
        req->clientAddrLen = sizeof(req->clientAddr);
        char buf[MAX_BUFFER_SIZE];
#ifdef DEBUG
        printf("\n-----------------------\n");
        printf("Waiting for a packet...\n");
#endif
        if(recvfrom(socketFd, buf, MAX_BUFFER_SIZE, 0, (struct sockaddr *) &req->clientAddr, &req->clientAddrLen) <= 0) {
            free(req);
            break;
        }
    
        PacketT *packet = &req->packet;
        deserializePacket(buf, packet);

#ifdef DEBUG
        char clientIp[INET_ADDRSTRLEN];
        inet_ntop(AF_INET, &(req->clientAddr.sin_addr), clientIp, INET_ADDRSTRLEN);
        int clientPort = ntohs(req->clientAddr.sin_port);
        printf("\nReceived packet from %s:%d ->\n", clientIp, clientPort);
        printf("  Type: %d\n", packet->type);
        printf("  Client ID: %d\n", packet->clientId);
        printf("  Service ID: %d\n", packet->serviceId);
        printf("  Seq: %d\n", packet->sequenceNumber);
        printf("  Length: %d\n", packet->payloadLength);
        printf("\n");
#endif

        cleanOrphanedRequests(req->clientAddr.sin_addr.s_addr, packet->clientId);

        // Remove previous cached response and continue if request was just an ACK
        if (packet->type == ACKNOWLEDGE) {
#ifdef DEBUG
        printf("Was ACK so removing current from cache...\n");
        printf("-----------------------\n");
#endif
            removeInFlightRequest(req->clientAddr.sin_addr.s_addr, packet->sequenceNumber);
            free(packet->payload);
            free(req);
            continue;
        }

        // Invalid request
        if (packet->type == REPLY) {
#ifdef DEBUG
        printf("Invalid request...\n");
        printf("-----------------------\n");
#endif
            free(packet->payload);
            free(req);
            continue;
        }

        // Duplicate currently in process
        RequestT *active = getActiveRequest(req->clientAddr.sin_addr.s_addr, packet->sequenceNumber);
        if (active != NULL) {
#ifdef DEBUG
        printf("Request was active so dropping...\n");
        printf("-----------------------\n");
#endif
            free(packet->payload);
            free(req);
            continue;
        }

        // Duplicate with cached response
        RequestT *cached = getInFlightRequest(req->clientAddr.sin_addr.s_addr, packet->sequenceNumber);
        if (cached != NULL) {
            int serializedLength;
            void *serialized = serializePacket(&cached->packet, &serializedLength);
            sendto(socketFd, serialized, serializedLength, 0, (struct sockaddr *) &req->clientAddr, req->clientAddrLen);
            free(packet->payload);
            free(req);
            free(serialized);
#ifdef DEBUG
        printf("Reply was cached...\n");
        printf("-----------------------\n");
#endif
            continue;
        }

#ifdef DEBUG
        printf("Creating new request...\n");
        printf("-----------------------\n");
#endif
        removeInFlightRequest(req->clientAddr.sin_addr.s_addr, packet->sequenceNumber - 1);

        // New request
        req->requestId = getNextRequestId();
        if (addToService(req) == -1) {
            free(packet->payload);
            free(req);
        }
    }

    // Broadcast wake up to all waiting threads
    pthread_mutex_lock(&registryLock);
    ServiceRegistryT *el;
    LL_FOREACH(registry, el) {
        pthread_mutex_lock(&el->queueLock);
        pthread_cond_broadcast(&el->queueCond);
        pthread_mutex_unlock(&el->queueLock);
    }
    pthread_mutex_unlock(&registryLock);

    return NULL;
}

void *serializePacket(PacketT *packet, int *length) {
    *length = 5 * sizeof(uint32_t) + packet->payloadLength;
    char *serialized = (char *) malloc(*length);
    if (!serialized)
        return NULL;

    uint32_t network_type = htonl(packet->type);
    uint32_t network_clientId = htonl(packet->clientId);
    uint32_t network_serviceId = htonl(packet->serviceId);
    uint32_t network_sequenceNumber = htonl(packet->sequenceNumber);
    uint32_t network_payloadLength = htonl(packet->payloadLength);

    int offset = 0;
    memcpy(serialized + offset, &network_type, sizeof(uint32_t));
    offset += sizeof(uint32_t);

    memcpy(serialized + offset, &network_clientId, sizeof(uint32_t));
    offset += sizeof(uint32_t);

    memcpy(serialized + offset, &network_serviceId, sizeof(uint32_t));
    offset += sizeof(uint32_t);

    memcpy(serialized + offset, &network_sequenceNumber, sizeof(uint32_t));
    offset += sizeof(uint32_t);

    memcpy(serialized + offset, &network_payloadLength, sizeof(uint32_t));
    offset += sizeof(uint32_t);
    
    if (packet->payload)
        memcpy(serialized + offset, packet->payload, packet->payloadLength);

    return (void *) serialized;
}

void deserializePacket(void *buffer, PacketT *packet) {
    int offset = 0;
    char *buf = (char *) buffer;

    packet->type = ntohl(*(uint32_t *)(buf + offset));
    offset += sizeof(uint32_t);

    packet->clientId = ntohl(*(uint32_t *)(buf + offset));
    offset += sizeof(uint32_t);

    packet->serviceId = ntohl(*(uint32_t *)(buf + offset));
    offset += sizeof(uint32_t);

    packet->sequenceNumber = ntohl(*(uint32_t *)(buf + offset));
    offset += sizeof(uint32_t);

    packet->payloadLength = ntohl(*(uint32_t *)(buf + offset));
    offset += sizeof(uint32_t);

    if (packet->payloadLength > 0) {
        packet->payload = malloc(packet->payloadLength);
        memcpy(packet->payload, buf + offset, packet->payloadLength);
    } else {
        packet->payload = NULL;
    }
}

ServiceRegistryT *getService(int serviceId) {
    pthread_mutex_lock(&registryLock);
    
    ServiceRegistryT *el;
    LL_SEARCH_SCALAR(registry, el, serviceId, serviceId);

    pthread_mutex_unlock(&registryLock);
    return el;
}

int addToService(RequestT *req) {
    ServiceRegistryT *service = getService(req->packet.serviceId); 
    if (service == NULL)
        return -1;

    pthread_mutex_lock(&service->queueLock);

    RequestElementT *el = (RequestElementT *) malloc(sizeof(RequestElementT));
    if (el == NULL) {
        pthread_mutex_unlock(&service->queueLock);
        return -1;
    }

    el->req = req;
    CDL_APPEND(service->queue, el);
    pthread_cond_signal(&service->queueCond);

    pthread_mutex_unlock(&service->queueLock);
    return 0;
}

int addInFlightRequest(RequestT *req) {
    pthread_mutex_lock(&inFlightRequestsLock);

    RequestElementT *el = (RequestElementT *) malloc(sizeof(RequestElementT));
    if (el == NULL) {
        pthread_mutex_unlock(&inFlightRequestsLock);
        return -1;
    }

    el->req = req;
    CDL_APPEND(inFlightRequests, el);

    pthread_mutex_unlock(&inFlightRequestsLock);
    return 0;
}

int removeInFlightRequest(in_addr_t clientAddr, int sequenceNumber) {
    pthread_mutex_lock(&inFlightRequestsLock);
    
    RequestElementT *el, *tmp1, *tmp2;
    CDL_FOREACH_SAFE(inFlightRequests, el, tmp1, tmp2) {
        if (el->req->clientAddr.sin_addr.s_addr == clientAddr && 
            el->req->packet.sequenceNumber == sequenceNumber) {
            CDL_DELETE(inFlightRequests, el);
            free(el->req->packet.payload);
            free(el->req);
            free(el);
            pthread_mutex_unlock(&inFlightRequestsLock);
            return 0;
        }
    }

    pthread_mutex_unlock(&inFlightRequestsLock);
    return -1;
}



int cleanOrphanedRequests(in_addr_t clientAddr, int clientId) {
    pthread_mutex_lock(&inFlightRequestsLock);
    
    RequestElementT *el, *tmp1, *tmp2;
    CDL_FOREACH_SAFE(inFlightRequests, el, tmp1, tmp2) {
        if (el->req->clientAddr.sin_addr.s_addr == clientAddr && 
            el->req->packet.clientId != clientId) {
            CDL_DELETE(inFlightRequests, el);
            free(el->req->packet.payload);
            free(el->req);
            free(el);
            pthread_mutex_unlock(&inFlightRequestsLock);
            return 0;
        }
    }

    pthread_mutex_unlock(&inFlightRequestsLock);
    return -1;
}

RequestT *getInFlightRequest(in_addr_t clientAddr, int sequenceNumber) {
    pthread_mutex_lock(&inFlightRequestsLock);
    
    RequestElementT *el;
    RequestT* req = NULL;

    CDL_FOREACH(inFlightRequests, el) {
        if (el->req->clientAddr.sin_addr.s_addr == clientAddr && 
            el->req->packet.sequenceNumber == sequenceNumber) {
            req = el->req;
            break;
        }
    }

    pthread_mutex_unlock(&inFlightRequestsLock);
    return req;
}

RequestT *popActiveRequest(int requestId) {
    pthread_mutex_lock(&activeRequestsLock);
    
    RequestElementT *el;
    RequestT* req = NULL;
    CDL_SEARCH_SCALAR(activeRequests, el, req->requestId, requestId);
    if (el) {
        req = el->req;
        CDL_DELETE(activeRequests, el);
        free(el);
    }

    pthread_mutex_unlock(&activeRequestsLock);
    return req;
}

RequestT *getActiveRequest(in_addr_t clientAddr, int sequenceNumber) {
    pthread_mutex_lock(&activeRequestsLock);
    
    RequestElementT *el;
    RequestT* req = NULL;
    
    CDL_FOREACH(activeRequests, el) {
        if (el->req->clientAddr.sin_addr.s_addr == clientAddr && 
            el->req->packet.sequenceNumber == sequenceNumber) {
            req = el->req;
            break;
        }
    }

    pthread_mutex_unlock(&activeRequestsLock);
    return req;
}

int getNextRequestId() {
    return nextReqId++;
}

void cleanRequestList(RequestElementT *list) {
    RequestElementT *el, *tmp1, *tmp2;
    CDL_FOREACH_SAFE(list,el,tmp1,tmp2) {
        CDL_DELETE(list,el);
        free(el->req->packet.payload);
        free(el->req);
        free(el);
    }
}

void triggerShutdown() {
    isShuttingDown = 1;
    shutdown(socketFd, SHUT_RDWR);
    close(socketFd);
}

void cleanUp() {
    pthread_join(dispatcherThread, NULL);

    ServiceRegistryT *el, *tmp;
    LL_FOREACH_SAFE(registry,el,tmp) {
        LL_DELETE(registry,el);
        pthread_cond_destroy(&el->queueCond);
        pthread_mutex_destroy(&el->queueLock);
        cleanRequestList(el->queue);
        free(el);
    }

    cleanRequestList(activeRequests);
    cleanRequestList(inFlightRequests);

    pthread_mutex_destroy(&registryLock);
    pthread_mutex_destroy(&activeRequestsLock);
    pthread_mutex_destroy(&inFlightRequestsLock);
}