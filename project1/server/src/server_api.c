#include "server_api.h"
#include "internal_server.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <pthread.h>
#include "utlist.h"

void init() {
    struct sockaddr_in server_addr;
    socklen_t len = sizeof(server_addr);
    memset(&server_addr, 0, sizeof(server_addr)); // TODO figure out if this is needed
    srand(time(NULL));
    
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

    // Initialize lists
    if ((serviceRegistry = servicelist_create()) == NULL) {
        perror("servicelist_create failed");
        close(socketFd);
        exit(EXIT_FAILURE);
    }

    if ((activeRequests = reqlist_create()) == NULL) {
        perror("reqlist_create failed");
        close(socketFd);
        exit(EXIT_FAILURE);
    }

    if ((inFlightRequests = reqlist_create()) == NULL) {
        perror("reqlist_create failed");
        close(socketFd);
        exit(EXIT_FAILURE);
    }

    if (pthread_create(&dispatcherThread, NULL, runDispatcherThread, NULL)) {
        perror("pthread_create failed");
        close(socketFd);
        exit(EXIT_FAILURE);
    }

    printf("Started server at %s:%d\n", ip, port);
#ifdef DEBUG
    printf("Virtual packet loss: REQ=%d, REP=%d\n", REQUEST_LOSS, REPLY_LOSS);
#endif
}

int registerService(int serviceId) {
    pthread_mutex_lock(&serviceRegistry->lock);

    // Check for existing registry with same ID
    AppServiceElementT *el;
    LL_SEARCH_SCALAR(serviceRegistry->list, el, service.id, serviceId);
    if (el) {
        pthread_mutex_unlock(&serviceRegistry->lock);
        return -1;
    }

    // Allocate new node
    AppServiceElementT *newNode = (AppServiceElementT *) malloc(sizeof(AppServiceElementT));
    if (newNode == NULL) {
        pthread_mutex_unlock(&serviceRegistry->lock);
        return -1;
    }
    
    // Initialize new node
    newNode->service.id = serviceId;
    newNode->service.queue = reqlist_create();
    if (newNode->service.queue == NULL)
        return -1;

    // Add to the service registry
    LL_PREPEND(serviceRegistry->list, newNode);

    pthread_mutex_unlock(&serviceRegistry->lock);
    return 0;
}

int unregisterService(int serviceId) {
    pthread_mutex_lock(&serviceRegistry->lock);

    // Search and remove the registry
    AppServiceElementT *el;
    LL_SEARCH_SCALAR(serviceRegistry->list, el, service.id, serviceId);
    if (el) {
        LL_DELETE(serviceRegistry->list,el);
        reqlist_destroy(el->service.queue);
        free(el);
        pthread_mutex_unlock(&serviceRegistry->lock);
        return 0;
    }

    pthread_mutex_unlock(&serviceRegistry->lock);
    return -1;
}

int getRequest(int serviceId, void *buf, int *len) {
    AppServiceT *service = getService(serviceId);
    if (service == NULL) 
        return -1;

    pthread_mutex_lock(&service->queue->lock);
    // Wait for a packet to arrive
    while (service->queue->list == NULL && !isShuttingDown)
        pthread_cond_wait(&service->queue->cond, &service->queue->lock);

    if (isShuttingDown) {
        pthread_mutex_unlock(&service->queue->lock);
        return -1;
    }

    // Remove packet from queue
    RequestElementT *el = service->queue->list;
    CDL_DELETE(service->queue->list, el);
    pthread_mutex_unlock(&service->queue->lock);

    // Copy to user buffers and free payload
    PacketT *packet = &el->req->packet;
    memcpy(buf, packet->payload, packet->payloadLength);
    if (len)
        *len = packet->payloadLength;
    free(packet->payload);
    packet->payload = NULL;
    packet->payloadLength = 0;

    // Move packet to active requests
    pthread_mutex_lock(&activeRequests->lock);
    CDL_APPEND(activeRequests->list, el);
    pthread_mutex_unlock(&activeRequests->lock);

    return el->req->id;
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
    
#ifdef DEBUG
    if (checkChance(REPLY_LOSS)) {
        printf("\n!!! Randomly dropped reply!\n");
        free(req);
        return;
    }
#endif

    int serializedLength;
    void *serialized = serializePacket(packet, &serializedLength);
    sendto(socketFd, serialized, serializedLength, 0, (struct sockaddr *) &req->clientAddr, req->clientAddrLen);
    free(serialized);
}

void triggerShutdown() {
    isShuttingDown = 1;
    shutdown(socketFd, SHUT_RDWR);
    close(socketFd);
}

void cleanUp() {
    pthread_join(dispatcherThread, NULL);

    servicelist_destroy(serviceRegistry);
    reqlist_destroy(activeRequests);
    reqlist_destroy(inFlightRequests);
}