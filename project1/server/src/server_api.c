#include "server_api.h"
#include "internal_server.h"
#include "utilities.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <pthread.h>
#include "utlist.h"

void init(int mainPort) {
    struct sockaddr_in server_addr;
    socklen_t len = sizeof(server_addr);
    memset(&server_addr, 0, sizeof(server_addr)); // TODO figure out if this is needed
    srand(time(NULL));
    
    mainSocket = socket(AF_INET, SOCK_DGRAM, 0);
    discoverySocket = socket(AF_INET, SOCK_DGRAM, 0);
    server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    server_addr.sin_port = htons(mainPort);
    server_addr.sin_family = AF_INET;

    // Bind to port and get info on address
    if (bind(mainSocket, (struct sockaddr *) &server_addr, len) < 0) {
        perror("Bind failed");
        close(mainSocket);
        exit(EXIT_FAILURE);
    }

    server_addr.sin_port = htons(DISCOVERY_PORT);
    if (bind(discoverySocket, (struct sockaddr *) &server_addr, len) < 0) {
        perror("Bind failed");
        close(discoverySocket);
        exit(EXIT_FAILURE);
    }

    if (getsockname(mainSocket, (struct sockaddr *) &server_addr, &len) < 0) {
        perror("getsockname failed");
        close(mainSocket);
        close(discoverySocket);
        exit(EXIT_FAILURE);
    }

    if (inet_ntop(AF_INET, &(server_addr.sin_addr), ip, INET_ADDRSTRLEN) == NULL) {
        perror("inet_ntop failed");
        close(mainSocket);
        close(discoverySocket);
        exit(EXIT_FAILURE);
    }
    port = ntohs(server_addr.sin_port);

    // Initialize lists
    if ((serviceRegistry = servicelist_create()) == NULL) {
        perror("servicelist_create failed");
        close(mainSocket);
        close(discoverySocket);
        exit(EXIT_FAILURE);
    }

    if ((activeRequests = reqlist_create()) == NULL) {
        perror("reqlist_create failed");
        close(mainSocket);
        close(discoverySocket);
        exit(EXIT_FAILURE);
    }

    if ((cachedRequests = reqlist_create()) == NULL) {
        perror("reqlist_create failed");
        close(mainSocket);
        close(discoverySocket);
        exit(EXIT_FAILURE);
    }

    if ((replies = reqlist_create()) == NULL) {
        perror("reqlist_create failed");
        close(mainSocket);
        close(discoverySocket);
        exit(EXIT_FAILURE);
    }

    if (fetchMemory(cachedRequests) == -1) {
        perror("fetchMemory failed");
        close(mainSocket);
        close(discoverySocket);
        exit(EXIT_FAILURE);
    }

    if (pthread_create(&receiverThread, NULL, runReceivingThread, NULL)) {
        perror("pthread_create failed");
        close(mainSocket);
        close(discoverySocket);
        exit(EXIT_FAILURE);
    }

    if (pthread_create(&senderThread, NULL, runSenderThread, NULL)) {
        perror("pthread_create failed");
        close(mainSocket);
        close(discoverySocket);
        exit(EXIT_FAILURE);
    }

    if (pthread_create(&discoveryThread, NULL, runDiscoveryThread, NULL)) {
        perror("pthread_create failed");
        close(mainSocket);
        close(discoverySocket);
        exit(EXIT_FAILURE);
    }

    printf("Started server at %s:%d\n", ip, port);
    printf("Discovery service running on %s:%d\n", ip, discoveryPort);
#ifdef DEBUG
    printf("Virtual packet loss: REQ=%d, REP=%d\n", REQUEST_LOSS, REPLY_LOSS);
#endif
}

int registerService(int serviceId) {
    pthread_mutex_lock(&serviceRegistry->lock);

    // Check for existing registry with same ID
    AppServiceElementT *el;
    LL_SEARCH_SCALAR(serviceRegistry->list, el, service->id, serviceId);
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
    
    // Allocate new service
    AppServiceT *service = (AppServiceT *) malloc(sizeof(AppServiceT));
    if (service == NULL) {
        pthread_mutex_unlock(&serviceRegistry->lock);
        return -1;
    }
    service->id = serviceId;
    service->refCount = 1;
    service->destroyed = 0;
    service->queue = reqlist_create();
    if (service->queue == NULL)
        return -1;

    newNode->service = service;

    // Add to the service registry
    LL_PREPEND(serviceRegistry->list, newNode);

    pthread_mutex_unlock(&serviceRegistry->lock);
    return 0;
}

int unregisterService(int serviceId) {
    pthread_mutex_lock(&serviceRegistry->lock);

    // Search and remove the registry
    AppServiceElementT *el;
    LL_SEARCH_SCALAR(serviceRegistry->list, el, service->id, serviceId);
    if (el) {
        LL_DELETE(serviceRegistry->list,el);
        AppServiceT *service = el->service;
        free(el);
        pthread_mutex_unlock(&serviceRegistry->lock);

        pthread_mutex_lock(&service->queue->lock);
        service->destroyed = 1;
        int destroy = --(service->refCount) == 0;
        pthread_cond_broadcast(&service->queue->cond);
        pthread_mutex_unlock(&service->queue->lock);

        if (destroy) {
            reqlist_destroy(service->queue);
            free(service);
        }
        return 0;
    }

    pthread_mutex_unlock(&serviceRegistry->lock);
    return -1;
}

int getRequest(int serviceId, void *buf, int *len) {
    RequestT *req = servicelist_popFirstBlocking(serviceRegistry, serviceId);
    if (req == NULL)
        return -1;

    // Copy to user buffers and free payload
    PacketT *packet = &req->packet;
    memcpy(buf, packet->payload, packet->payloadLength);
    if (len)
        *len = packet->payloadLength;
    free(packet->payload);
    packet->payload = NULL;
    packet->payloadLength = 0;

    // Move packet to active requests
    reqlist_add(activeRequests, req);

    return req->id;
}

void sendReply(int requestId, void *buf, int len) {
    RequestT *req = reqlist_popByReqId(activeRequests, requestId);
    if (req == NULL)
        return;

    // Copy user buffer to our packet buffer
    PacketT *packet = &req->packet;
    packet->type = REPLY;
    packet->payloadLength = len;
    packet->payload = malloc(len);
    memcpy(packet->payload, buf, len);

    if (reqlist_update(cachedRequests, req, packet, 0) == -1) {
        free(packet->payload);
        free(req);
        return;
    }
    updateMemory(cachedRequests);

    if (reqlist_add(replies, req) == -1) {
        free(packet->payload);
        free(req);
        return;
    }
}

void triggerShutdown() {
    isShuttingDown = 1;
    shutdown(mainSocket, SHUT_RDWR);
    shutdown(discoverySocket, SHUT_RDWR);
    close(mainSocket);
    close(discoverySocket);
}

void cleanUp() {
    pthread_join(receiverThread, NULL);
    pthread_join(senderThread, NULL);
    pthread_join(discoveryThread, NULL);

    servicelist_destroy(serviceRegistry);
    reqlist_destroy(activeRequests);
    reqlist_destroy(cachedRequests);
}