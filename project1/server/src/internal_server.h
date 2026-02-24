#ifndef INTERNAL_SERVER_H
#define INTERNAL_SERVER_H
#include <pthread.h>
#include <arpa/inet.h>
#include "server_api.h"
#include "utlist.h"

extern int socketFd;
extern int port;
extern char ip[INET_ADDRSTRLEN];

extern ServiceRegistryT *registry;
extern pthread_mutex_t registryLock;

extern RequestElementT *activeRequests;
extern pthread_mutex_t activeRequestsLock;

void runNetworkThread();

ServiceRegistryT *getService(int serviceId);
RequestT *popActiveRequest(int requestId);
void cleanRequestList(RequestElementT *list);
void cleanUp();

#endif