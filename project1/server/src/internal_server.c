#include "internal_server.h"
#include "utlist.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

char ip[INET_ADDRSTRLEN];
int port;
int socketFd;

int isShuttingDown = 0;
int nextReqId = 0;
pthread_t dispatcherThread;

AppServiceListT *serviceRegistry;
RequestListT *activeRequests;
RequestListT *inFlightRequests;

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

#ifdef DEBUG
        if (checkChance(REQUEST_LOSS)) {
            printf("!!! Randomly dropped request!\n");
            printf("-----------------------\n");
            free(req);
            continue;
        }
#endif
    
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

        // cleanOrphanedRequests(req->clientAddr.sin_addr.s_addr, packet->clientId);

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
        req->id = getNextRequestId();
        if (addToService(req) == -1) {
            free(packet->payload);
            free(req);
        }
    }

    // Broadcast wake up to all waiting threads
    pthread_mutex_lock(&serviceRegistry->lock);
    AppServiceElementT *el;
    LL_FOREACH(serviceRegistry->list, el) {
        pthread_mutex_lock(&el->service.queue->lock);
        pthread_cond_broadcast(&el->service.queue->cond);
        pthread_mutex_unlock(&el->service.queue->lock);
    }
    pthread_mutex_unlock(&serviceRegistry->lock);

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

int getNextRequestId() {
    return nextReqId++;
}

int checkChance(int chance) {
    return (rand() % 100) < chance;
}

RequestListT *reqlist_create() {
    RequestListT *list = (RequestListT *) malloc(sizeof(RequestListT));
    if (list == NULL)
        return NULL;

    list->list = NULL;
    pthread_mutex_init(&list->lock, NULL);
    pthread_cond_init(&list->cond, NULL);

    return list;
}

void reqlist_destroy(RequestListT *list) {
    RequestElementT *el, *tmp1, *tmp2;
    CDL_FOREACH_SAFE(list->list,el,tmp1,tmp2) {
        CDL_DELETE(list->list,el);
        free(el->req->packet.payload);
        free(el->req);
        free(el);
    }

    pthread_mutex_destroy(&list->lock);
    pthread_cond_destroy(&list->cond);
    free(list);
}

AppServiceListT *servicelist_create() {
    AppServiceListT *list = (AppServiceListT *) malloc(sizeof(AppServiceListT));
    if (list == NULL)
        return NULL;

    list->list = NULL;
    pthread_mutex_init(&list->lock, NULL);

    return list;
}

void servicelist_destroy(AppServiceListT *list) {
    AppServiceElementT *el, *tmp1;
    LL_FOREACH_SAFE(list->list,el,tmp1) {
        LL_DELETE(list->list,el);
        reqlist_destroy(el->service.queue);
        free(el);
    }

    pthread_mutex_destroy(&list->lock);
    free(list);
}

AppServiceT *getService(int serviceId) {
    pthread_mutex_lock(&serviceRegistry->lock);
    
    AppServiceElementT *el;
    LL_SEARCH_SCALAR(serviceRegistry->list, el, service.id, serviceId);
    if (!el) {
        pthread_mutex_unlock(&serviceRegistry->lock);
        return NULL;
    }

    AppServiceT *service = &(el->service);

    pthread_mutex_unlock(&serviceRegistry->lock);
    return service;
}

int addToService(RequestT *req) {
    AppServiceT *service = getService(req->packet.serviceId); 
    if (service == NULL)
        return -1;

    pthread_mutex_lock(&service->queue->lock);

    RequestElementT *el = (RequestElementT *) malloc(sizeof(RequestElementT));
    if (el == NULL) {
        pthread_mutex_unlock(&service->queue->lock);
        return -1;
    }

    el->req = req;
    CDL_APPEND(service->queue->list, el);
    pthread_cond_signal(&service->queue->cond);

    pthread_mutex_unlock(&service->queue->lock);
    return 0;
}

RequestT *getActiveRequest(in_addr_t clientAddr, int sequenceNumber) {
    pthread_mutex_lock(&activeRequests->lock);
    
    RequestElementT *el;
    RequestT* req = NULL;
    
    CDL_FOREACH(activeRequests->list, el) {
        if (el->req->clientAddr.sin_addr.s_addr == clientAddr && 
            el->req->packet.sequenceNumber == sequenceNumber) {
            req = el->req;
            break;
        }
    }

    pthread_mutex_unlock(&activeRequests->lock);
    return req;
}

RequestT *popActiveRequest(int requestId) {
    pthread_mutex_lock(&activeRequests->lock);
    
    RequestElementT *el;
    RequestT* req = NULL;
    CDL_SEARCH_SCALAR(activeRequests->list, el, req->id, requestId);
    if (el) {
        req = el->req;
        CDL_DELETE(activeRequests->list, el);
        free(el);
    }

    pthread_mutex_unlock(&activeRequests->lock);
    return req;
}

int addInFlightRequest(RequestT *req) {
    pthread_mutex_lock(&inFlightRequests->lock);

    RequestElementT *el = (RequestElementT *) malloc(sizeof(RequestElementT));
    if (el == NULL) {
        pthread_mutex_unlock(&inFlightRequests->lock);
        return -1;
    }

    el->req = req;
    CDL_APPEND(inFlightRequests->list, el);

    pthread_mutex_unlock(&inFlightRequests->lock);
    return 0;
}

int removeInFlightRequest(in_addr_t clientAddr, int sequenceNumber) {
    pthread_mutex_lock(&inFlightRequests->lock);
    
    RequestElementT *el, *tmp1, *tmp2;
    CDL_FOREACH_SAFE(inFlightRequests->list, el, tmp1, tmp2) {
        if (el->req->clientAddr.sin_addr.s_addr == clientAddr && 
            el->req->packet.sequenceNumber == sequenceNumber) {
            CDL_DELETE(inFlightRequests->list, el);
            free(el->req->packet.payload);
            free(el->req);
            free(el);
            pthread_mutex_unlock(&inFlightRequests->lock);
            return 0;
        }
    }

    pthread_mutex_unlock(&inFlightRequests->lock);
    return -1;
}

int cleanOrphanedRequests(in_addr_t clientAddr, int clientId) {
    pthread_mutex_lock(&inFlightRequests->lock);
    
    RequestElementT *el, *tmp1, *tmp2;
    CDL_FOREACH_SAFE(inFlightRequests->list, el, tmp1, tmp2) {
        if (el->req->clientAddr.sin_addr.s_addr == clientAddr && 
            el->req->packet.clientId != clientId) {
            CDL_DELETE(inFlightRequests->list, el);
            free(el->req->packet.payload);
            free(el->req);
            free(el);
            pthread_mutex_unlock(&inFlightRequests->lock);
            return 0;
        }
    }

    pthread_mutex_unlock(&inFlightRequests->lock);
    return -1;
}

RequestT *getInFlightRequest(in_addr_t clientAddr, int sequenceNumber) {
    pthread_mutex_lock(&inFlightRequests->lock);
    
    RequestElementT *el;
    RequestT* req = NULL;

    CDL_FOREACH(inFlightRequests->list, el) {
        if (el->req->clientAddr.sin_addr.s_addr == clientAddr && 
            el->req->packet.sequenceNumber == sequenceNumber) {
            req = el->req;
            break;
        }
    }

    pthread_mutex_unlock(&inFlightRequests->lock);
    return req;
}