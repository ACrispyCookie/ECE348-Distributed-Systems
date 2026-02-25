#ifndef INTERNAL_SERVER_H
#define INTERNAL_SERVER_H
#include <pthread.h>
#include <arpa/inet.h>
#include "server_api.h"
#include "utlist.h"

extern int isShuttingDown;
extern int socketFd;
extern int port;
extern char ip[INET_ADDRSTRLEN];

extern int nextReqId;

extern ServiceRegistryT *registry;
extern pthread_mutex_t registryLock;

extern RequestElementT *activeRequests;
extern pthread_mutex_t activeRequestsLock;

extern RequestElementT *inFlightRequests;
extern pthread_mutex_t inFlightRequestsLock;

extern pthread_t dispatcherThread;

void *runDispatcherThread(void *args);

void *serializePacket(PacketT *packet, int *length);
void deserializePacket(void *buffer, PacketT *packet);
int getNextRequestId();

ServiceRegistryT *getService(int serviceId);
int addToService(RequestT *req);
RequestT *popActiveRequest(int requestId);
RequestT *getInFlightRequest(int clientId, int sequenceNumber);
int removeInFlightRequest(int clientId, int sequenceNumber);
int addInFlightRequest(RequestT *req);

void cleanRequestList(RequestElementT *list);
void cleanUp();

#endif