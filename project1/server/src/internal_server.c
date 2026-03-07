#include "internal_server.h"
#include "utilities.h"
#include "utlist.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

char ip[INET_ADDRSTRLEN];
int port, discoveryPort;
int mainSocket, discoverySocket;

int isShuttingDown = 0;
int nextReqId = 0, nextClientId = 1;
pthread_t receiverThread, senderThread, discoveryThread;

AppServiceListT *serviceRegistry;
RequestListT *activeRequests;
RequestListT *cachedRequests;
RequestListT *replies;

void *runReceivingThread(void *args) {
    while (1) {
        RequestT *req = malloc(sizeof(RequestT));
        req->clientAddrLen = sizeof(req->clientAddr);
        char buf[MAX_BUFFER_SIZE];
        if(recvfrom(mainSocket, buf, MAX_BUFFER_SIZE, 0, (struct sockaddr *) &req->clientAddr, &req->clientAddrLen) <= 0) {
            free(req);
            break;
        }

        deserializePacket(buf, &req->packet);

#ifdef DEBUG
        printf("\n==================== REQUEST ====================\n");
        printf("Received the following packet...\n");

        PacketT *packet = &req->packet;
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

        if (checkChance(REQUEST_LOSS)) {
            printf("!!! Randomly dropped request!\n");
            printf("=================================================\n");
            free(req);
            continue;
        }
#endif

        int clean = 0;
        switch (req->packet.type) {
            case REQUEST:
                clean = receiveRequest(req);
                break;
            case ACKNOWLEDGE:
#ifdef DEBUG
        printf("Was ACK so checking cached...\n");
#endif
                clean = receiveAck(req);
                break;
            case CANCEL:
#ifdef DEBUG
        printf("Was CANCEL so removing from everywhere...\n");
#endif
                clean = receiveCancel(req);
                break;
            default: { // Invalid request
#ifdef DEBUG
        printf("Invalid request...\n");
#endif
                clean = 1;
                break;
            }
        }

        if (clean) {
            free(req->packet.payload);
            free(req);
        }
#ifdef DEBUG
        printf("=================================================\n");
#endif
    }

    // Broadcast wake up to all waiting threads
    pthread_mutex_lock(&serviceRegistry->lock);
    AppServiceElementT *el;
    LL_FOREACH(serviceRegistry->list, el) {
        pthread_mutex_lock(&el->service->queue->lock);
        pthread_cond_broadcast(&el->service->queue->cond);
        pthread_mutex_unlock(&el->service->queue->lock);
    }
    pthread_mutex_unlock(&serviceRegistry->lock);

    return NULL;
}

int receiveRequest(RequestT *req) {
    // Request is a handshake
    if (req->packet.clientId == 0) 
        return receiveHandshake(req);

    // Duplicate already acknowledged
    if (reqlist_containsNewer(cachedRequests, req)){
#ifdef DEBUG
    printf("Request was duplicate and older...\n");
#endif
        return 1;
    }

    if (!servicelist_contains(serviceRegistry, req->packet.serviceId)){
#ifdef DEBUG
    printf("Request is for unregistered service...\n");
#endif
        free(req->packet.payload);
        req->packet.payloadLength = 0;
        req->packet.payload = NULL;
        req->packet.type = NOT_SERVING;

        reqlist_add(replies, req);
        return 0;
    }

    // Duplicate currently in process
    if (servicelist_containsReq(serviceRegistry, req) || reqlist_contains(activeRequests, req, NULL)) {
#ifdef DEBUG
    printf("Request was active so dropping...\n");
#endif
        return 1;
    }

    // Duplicate already processed
    RequestT *cached;
    int success = reqlist_contains(cachedRequests, req, &cached);
    if (success) {
#ifdef DEBUG
    printf("Reply was cached, resending...\n");
#endif
        if (cached->packet.payload == NULL)
            cached->packet.type = EXPIRED;
        reqlist_add(replies, cached);
        return 1;
    }

#ifdef DEBUG
    printf("Creating new request...\n");
#endif
    updateCachedRequest(req, 0);
    updateMemory(cachedRequests);

    // New request
    req->id = generateId(REQUEST_ID);
    if (servicelist_addReq(serviceRegistry, req) == -1)
        return 1;
    return 0;
}

int receiveHandshake(RequestT *req) {

#ifdef DEBUG
        printf("Was handshake so generating new ID...\n");
#endif

    free(req->packet.payload);
    req->packet.type = REPLY;
    req->packet.serviceId = 0;
    req->packet.payloadLength = 0;
    req->packet.payload = NULL;
    req->packet.clientId = generateId(CLIENT_ID);
    reqlist_add(cachedRequests, deepCopy(req));
    updateMemory(cachedRequests);

    reqlist_add(replies, req);

    return 0;
}

int receiveAck(RequestT *req) {
    
    updateCachedRequest(req, 0);
    updateMemory(cachedRequests);

    return 1;
}

int receiveCancel(RequestT *req) {
    RequestT *existing = servicelist_popReq(serviceRegistry, req);
    if (existing) {
        free(existing->packet.payload);
        free(existing);

        req->packet.type = REPLY;
        reqlist_add(replies, req);
        return 0;
    }

    int error = reqlist_remove(activeRequests, req);
    if (!error) {
        req->packet.type = REPLY;
        reqlist_add(replies, req);
        return 0;
    }

    updateCachedRequest(req, 0);
    updateMemory(cachedRequests);
    req->packet.type = REPLY;
    reqlist_add(replies, req);

    return 0;
}

void *runSenderThread(void *args) {
    while (1) {
        pthread_mutex_lock(&replies->lock);
        while (replies->list == NULL && !isShuttingDown)
            pthread_cond_wait(&replies->cond, &replies->lock);

        if (isShuttingDown) {
            pthread_mutex_unlock(&replies->lock);
            break;
        }

        RequestElementT *el = replies->list;
        CDL_DELETE(replies->list, el);
        pthread_mutex_unlock(&replies->lock);

        RequestT *req = el->req;
        free(el);

#ifdef DEBUG
        printf("\n==================== REPLY ====================\n");
        printf("Sending the following reply...\n");

        PacketT *packet = &req->packet;
        char clientIp[INET_ADDRSTRLEN];
        inet_ntop(AF_INET, &(req->clientAddr.sin_addr), clientIp, INET_ADDRSTRLEN);
        int clientPort = ntohs(req->clientAddr.sin_port);
        printf("\nSending packet to %s:%d ->\n", clientIp, clientPort);
        printf("  Type: %d\n", packet->type);
        printf("  Client ID: %d\n", packet->clientId);
        printf("  Service ID: %d\n", packet->serviceId);
        printf("  Seq: %d\n", packet->sequenceNumber);
        printf("  Length: %d\n", packet->payloadLength);
        printf("\n");

        if (checkChance(REQUEST_LOSS)) {
            printf("!!! Randomly dropped reply!\n");
            printf("\n===============================================\n");
            continue;
        }
        printf("\n===============================================\n");
#endif

        int serializedLength;
        void *serialized = serializePacket(&req->packet, &serializedLength);
        sendto(mainSocket, serialized, serializedLength, 0, (struct sockaddr *) &req->clientAddr, req->clientAddrLen);
        free(serialized);
        free(req->packet.payload);
        free(req);
    }

    return NULL;
}

void *runDiscoveryThread(void *args) {
    while (1) {
        char buf[MAX_BUFFER_SIZE];
        struct sockaddr_in clientAddr;
        socklen_t clientAddrLen = sizeof(clientAddr);
        if(recvfrom(discoverySocket, buf, MAX_BUFFER_SIZE, 0, (struct sockaddr *) &clientAddr, &clientAddrLen) <= 0)
            break;

        if (strcmp(buf, "DISCOVER") != 0)
            continue;
        
        char *reply = "DISCOVER_RPL";
        strcpy(buf, reply);
        uint32_t portn = htonl(port);
        memcpy(buf + strlen(reply) + 1, &portn, sizeof(uint32_t));
        
        int size = strlen(reply) + 1 + sizeof(int);
        sendto(discoverySocket, buf, size, 0, (struct sockaddr *) &clientAddr, clientAddrLen);
    }

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

RequestT *deepCopy(RequestT *req) {
    RequestT *copy = malloc(sizeof(RequestT));
    if (!copy)
        return NULL;
    
    *copy = *req;
    if (copy->packet.payloadLength > 0) {
        copy->packet.payload = malloc(copy->packet.payloadLength);
        if (!copy->packet.payload) {
            free(copy);
            return NULL;
        }
        memcpy(copy->packet.payload, req->packet.payload, req->packet.payloadLength);
    }

    return copy;
}