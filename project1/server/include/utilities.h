#ifndef UTILITIES_H
#define UTILITIES_H
#include "server_api.h"
#include "internal_server.h"

RequestListT *reqlist_create();
void reqlist_destroy(RequestListT *list);
int reqlist_add(RequestListT *list, RequestT *req);
int reqlist_remove(RequestListT *list, RequestT *key);
int reqlist_contains(RequestListT *list, RequestT *key, RequestT **copy);
int reqlist_containsClient(RequestListT *list, RequestT *key);
int reqlist_containsNewer(RequestListT *list, RequestT *key);
int reqlist_update(RequestListT *list, RequestT *key, RequestT *updated, int copyNewPayload);
RequestT *reqlist_pop(RequestListT *list, RequestT *key);
RequestT *reqlist_popByReqId(RequestListT *list, unsigned int requestId);
int updateCachedRequest(RequestT *req, int copyPayload);

AppServiceListT *servicelist_create();
void servicelist_destroy(AppServiceListT *list);
int servicelist_contains(AppServiceListT *list, unsigned int serviceId);
int servicelist_addReq(AppServiceListT *list, RequestT *req);
int servicelist_containsReq(AppServiceListT *list, RequestT *key);
RequestT *servicelist_popReq(AppServiceListT *list, RequestT *key);
RequestT *servicelist_popFirstBlocking(AppServiceListT *list, unsigned int serviceId);

int fetchMemory(RequestListT *cached);
int updateMemory(RequestListT *cached);

int generateId(IdentifierT type);
int checkChance(int chance);

#endif