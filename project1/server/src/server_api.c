#include "server_api.h"
#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <pthread.h>

static int socketFd;
static int port;
static char ip[INET_ADDRSTRLEN];

static ServiceRegistryT *registry;
static pthread_mutex_t registryLock;

void init() {
    struct sockaddr_in server_addr;
    socklen_t len = sizeof(server_addr);
    
    socketFd = socket(AF_INET, SOCK_DGRAM, 0);
    server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    server_addr.sin_port = htons(0);
    server_addr.sin_family = AF_INET;

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

    if (pthread_mutex_init(&registryLock, NULL) != 0) {
        perror("pthread_mutex_init failed");
        close(socketFd);
        exit(EXIT_FAILURE);
    }

    printf("Started server at %s:%d\n", ip, port);
}

void cleanUp() {
    pthread_mutex_destroy(&registryLock);

    ServiceRegistryT *next;
    for (ServiceRegistryT *t = registry; t != NULL; t = next) {
        next = t->next;
        free(t);
    }
}

int registerService(int serviceId) {
    pthread_mutex_lock(&registryLock);
    
    for (ServiceRegistryT *t = registry; t != NULL; t = t->next) {
        if (t->serviceId == serviceId) {
            pthread_mutex_unlock(&registryLock);
            return -1;
        }
    }

    ServiceRegistryT *newNode = (ServiceRegistryT *) malloc(sizeof(ServiceRegistryT));
    if (newNode == NULL) {
        pthread_mutex_unlock(&registryLock);
        return -1;
    }
    
    newNode->serviceId = serviceId;
    newNode->queueHead = NULL;
    newNode->queueTail = NULL;
    pthread_mutex_init(&(newNode->queueLock), NULL);
    pthread_cond_init(&(newNode->queueCond), NULL);
    newNode->next = registry;
    registry = newNode;

    pthread_mutex_unlock(&registryLock);
    return 0;
}

int unregisterService(int serviceId) {
    pthread_mutex_lock(&registryLock);

    ServiceRegistryT *prev = NULL;

    for (ServiceRegistryT *t = registry; t != NULL; t = t->next) {
        if (t->serviceId == serviceId) {
            if (prev == NULL)
                registry = t->next;
            else
                prev->next = t->next;
            
            free(t);
            pthread_mutex_unlock(&registryLock);
            return 0;
        }

        prev = t;
    }

    pthread_mutex_unlock(&registryLock);
    return -1;
}

ServiceRegistryT *getService(int serviceId) {
    pthread_mutex_lock(&registryLock);
    
    for (ServiceRegistryT *t = registry; t != NULL; t = t->next)
        if (t->serviceId == serviceId) {
            pthread_mutex_unlock(&registryLock);
            return t;
        }

    pthread_mutex_unlock(&registryLock);
    return NULL;
}

int getRequest(int serviceId, void *buf, int *len) {
    ServiceRegistryT *service = getService(serviceId);
    if (service == NULL) 
        return -1;

    int requestId;
    pthread_mutex_lock(&service->queueLock);
    
    if (service->queueHead == NULL) {
        pthread_cond_wait(&service->queueCond, &service->queueLock);
        pthread_mutex_lock(&service->queueLock);
    }

    PendingRequestT *req = service->queueHead;
    requestId = req->requestId;
    service->queueHead = req->next;
    free(req);

    pthread_mutex_unlock(&service->queueLock);
    return req->requestId;
}

void sendReply(int requestId, void *buf, int len) {

}