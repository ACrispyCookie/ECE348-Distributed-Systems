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