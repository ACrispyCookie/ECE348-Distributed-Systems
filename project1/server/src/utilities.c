#include "utilities.h"
#include "utlist.h"
#include "internal_server.h"
#include <stdlib.h>
#include <stdio.h>

static pthread_mutex_t fileLock = PTHREAD_MUTEX_INITIALIZER;

int generateId(IdentifierT type) {
    switch (type) {
    case REQUEST_ID:
        return nextReqId++;
    case CLIENT_ID:
        return nextClientId++;
    default:
        return -1;
    }
}

int checkChance(int chance) {
    return (rand() % 100) < chance;
}

int fetchMemory(RequestListT *cached) {
    FILE *file = fopen(MEMORY_FILE, "a+");
    if (file == NULL) {
        return -1;
    }
    
    rewind(file);

    char line[256];
    unsigned int id, seqNum;
    while (fgets(line, sizeof(line), file) != NULL) {
        if (sscanf(line, " C %u %u", &id, &seqNum) == 2) {
            RequestT *req = malloc(sizeof(RequestT));
            if (!req)
                return -1;
            nextClientId = (nextClientId < id) ? id + 1 : nextClientId;
            req->packet.clientId = id;
            req->packet.sequenceNumber = seqNum;
            req->packet.type = REQUEST;
            req->packet.serviceId = 0;
            req->packet.payloadLength = 0;
            req->packet.payload = NULL;
            reqlist_add(cached, req);
        }
    }

    fclose(file);
    return 0;
}

int updateMemory(RequestListT *cached) {
    pthread_mutex_lock(&fileLock);
    FILE *file = fopen(MEMORY_FILE, "w+");
    if (file == NULL) {
        pthread_mutex_unlock(&fileLock);
        return -1;
    }

    pthread_mutex_lock(&cached->lock);
    RequestElementT *el;
    
    CDL_FOREACH(cached->list, el) {
        if (el->req != NULL) {
            fprintf(file, "C %u %u\n", 
                    el->req->packet.clientId, 
                    el->req->packet.sequenceNumber);
        }
    }
    pthread_mutex_unlock(&cached->lock);

    fclose(file);
    pthread_mutex_unlock(&fileLock);
    return 0;
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

int reqlist_add(RequestListT *list, RequestT *req) {
    pthread_mutex_lock(&list->lock);

    RequestElementT *el = (RequestElementT *) malloc(sizeof(RequestElementT));
    if (el == NULL) {
        pthread_mutex_unlock(&list->lock);
        return -1;
    }

    el->req = req;
    CDL_APPEND(list->list, el);
    pthread_cond_signal(&list->cond);

    pthread_mutex_unlock(&list->lock);
    return 0;
}

int reqlist_remove(RequestListT *list, RequestT *key) {
    pthread_mutex_lock(&list->lock);
    
    RequestElementT *el, *tmp1, *tmp2;
    int ret = -1;
    
    CDL_FOREACH_SAFE(list->list, el, tmp1, tmp2) {
        if (el->req->packet.clientId == key->packet.clientId && 
            el->req->packet.sequenceNumber == key->packet.sequenceNumber) {
            CDL_DELETE(list->list,el);
            free(el->req->packet.payload);
            free(el->req);
            free(el);
            ret = 0;
            break;
        }
    }

    pthread_mutex_unlock(&list->lock);
    return ret;
}

int reqlist_contains(RequestListT *list, RequestT *key, RequestT **copy) {
    pthread_mutex_lock(&list->lock);
    
    RequestElementT *el;
    int ret = 0;
    
    CDL_FOREACH(list->list, el) {
        if (el->req->packet.clientId == key->packet.clientId && 
            el->req->packet.sequenceNumber == key->packet.sequenceNumber) {
            if (copy)
                *copy = deepCopy(el->req);
            ret = 1;
            break;
        }
    }

    pthread_mutex_unlock(&list->lock);
    return ret;
}

int reqlist_containsNewer(RequestListT *list, RequestT *key) {
    pthread_mutex_lock(&list->lock);
    
    RequestElementT *el;
    int ret = 0;
    
    CDL_FOREACH(list->list, el) {
        if (el->req->packet.clientId == key->packet.clientId && 
            el->req->packet.sequenceNumber > key->packet.sequenceNumber) {
            ret = 1;
            break;
        }
    }

    pthread_mutex_unlock(&list->lock);
    return ret;
}

int reqlist_update(RequestListT *list, RequestT *key, PacketT *packet, int copyNewPayload) {
    pthread_mutex_lock(&list->lock);
    
    RequestElementT *el;
    
    CDL_FOREACH(list->list, el) {
        if (el->req->packet.clientId == key->packet.clientId) {
            free(el->req->packet.payload);
            el->req->packet.payloadLength = 0;
            el->req->packet.payload = NULL;

            el->req->packet = *packet;
            if (copyNewPayload) {
                el->req->packet.payload = malloc(packet->payloadLength);
                if (el->req->packet.payload == NULL) {
                    el = NULL;
                    break;
                }
                el->req->packet.payloadLength = packet->payloadLength;
            } else {
                el->req->packet.payloadLength = 0;
                el->req->packet.payload = NULL;
            }
            break;
        }
    }

    pthread_mutex_unlock(&list->lock);
    return el != NULL ? 0 : -1;
}

RequestT *reqlist_pop(RequestListT *list, RequestT *key) {
    pthread_mutex_lock(&list->lock);
    
    RequestElementT *el;
    RequestT* req = NULL;
    
    CDL_FOREACH(list->list, el) {
        if (el->req->packet.clientId == key->packet.clientId && 
            el->req->packet.sequenceNumber == key->packet.sequenceNumber) {
            req = el->req;
            CDL_DELETE(list->list,el);
            free(el);
            break;
        }
    }

    pthread_mutex_unlock(&list->lock);
    return req;
}

RequestT *reqlist_popByReqId(RequestListT *list, unsigned int requestId) {
    pthread_mutex_lock(&list->lock);
    
    RequestElementT *el;
    RequestT* req = NULL;
    
    CDL_FOREACH(list->list, el) {
        if (el->req->id == requestId) {
            req = el->req;
            CDL_DELETE(list->list,el);
            free(el);
            break;
        }
    }

    pthread_mutex_unlock(&list->lock);
    return req;
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
        reqlist_destroy(el->service->queue);
        free(el);
    }

    pthread_mutex_destroy(&list->lock);
    free(list);
}

int servicelist_addReq(AppServiceListT *list, RequestT *req) {
    pthread_mutex_lock(&list->lock);

    AppServiceElementT *found;
    LL_SEARCH_SCALAR(list->list, found, service->id, req->id);
    if (!found) {
        pthread_mutex_unlock(&list->lock);
        return -1;
    }

    AppServiceT *service = found->service;
    RequestListT *queue = service->queue;
    if (reqlist_add(queue, req) == -1) {
        pthread_mutex_unlock(&list->lock);
        return -1;    
    }

    pthread_mutex_unlock(&list->lock);
    return 0;
}

int servicelist_containsReq(AppServiceListT *list, RequestT *key) {
    pthread_mutex_lock(&list->lock);

    AppServiceElementT *found;
    LL_SEARCH_SCALAR(list->list, found, service->id, key->id);
    if (!found) {
        pthread_mutex_unlock(&list->lock);
        return 0;
    }

    AppServiceT *service = found->service;
    RequestListT *queue = service->queue;
    int contains = reqlist_contains(queue, key, NULL);

    pthread_mutex_unlock(&list->lock);
    return contains;
}

RequestT *servicelist_popReq(AppServiceListT *list, RequestT *key) {
    pthread_mutex_lock(&list->lock);

    AppServiceElementT *found;
    LL_SEARCH_SCALAR(list->list, found, service->id, key->id);
    if (!found) {
        pthread_mutex_unlock(&list->lock);
        return NULL;
    }

    AppServiceT *service = found->service;
    RequestListT *queue = service->queue;
    RequestT *req = reqlist_pop(queue, key);
    pthread_mutex_unlock(&list->lock);
    
    return req;
}

RequestT *servicelist_popFirstBlocking(AppServiceListT *list, unsigned int serviceId) {
    pthread_mutex_lock(&serviceRegistry->lock);
    
    AppServiceElementT *el;
    LL_SEARCH_SCALAR(serviceRegistry->list, el, service->id, serviceId);
    if (!el) {
        pthread_mutex_unlock(&serviceRegistry->lock);
        return NULL;
    }

    AppServiceT *service = el->service;
    RequestListT *queue = service->queue;
    pthread_mutex_lock(&service->queue->lock);
    service->refCount++; 
    pthread_mutex_unlock(&service->queue->lock);
    pthread_mutex_unlock(&serviceRegistry->lock);

    pthread_mutex_lock(&queue->lock);
    // Wait for a packet to arrive
    while (queue->list == NULL && !isShuttingDown && !service->destroyed)
        pthread_cond_wait(&queue->cond, &queue->lock);

    if (isShuttingDown || service->destroyed) {
        int destroy = --(service->refCount) == 0;
        pthread_mutex_unlock(&queue->lock);
        if (destroy) {
            reqlist_destroy(service->queue);
            free(service);
        }
        return NULL;
    }

    // Remove packet from queue
    RequestElementT *reqEl = queue->list;
    CDL_DELETE(queue->list, reqEl);
    RequestT *req = reqEl->req;
    free(reqEl);

    int destroy = --(service->refCount) == 0;
    pthread_mutex_unlock(&queue->lock);
    if (destroy) {
        reqlist_destroy(service->queue);
        free(service);
    }
    
    return req;
}

// AppServiceT *getService(unsigned int serviceId) {
//     pthread_mutex_lock(&serviceRegistry->lock);
    
//     AppServiceElementT *el;
//     LL_SEARCH_SCALAR(serviceRegistry->list, el, service->id, serviceId);
//     if (!el) {
//         pthread_mutex_unlock(&serviceRegistry->lock);
//         return NULL;
//     }

//     AppServiceT *service = el->service;
//     pthread_mutex_lock(&service->queue->lock);
//     service->refCount++; 
//     pthread_mutex_unlock(&service->queue->lock);

//     pthread_mutex_unlock(&serviceRegistry->lock);
//     return service;
// }

// int addToService(RequestT *req) {
//     AppServiceT *service = getService(req->packet.serviceId); 
//     if (service == NULL)
//         return -1;

//     RequestListT *queue = service->queue;
//     pthread_mutex_lock(&queue->lock);

//     RequestElementT *el = (RequestElementT *) malloc(sizeof(RequestElementT));
//     if (el == NULL) {
//         pthread_mutex_unlock(&queue->lock);
//         return -1;
//     }

//     el->req = req;
//     CDL_APPEND(queue->list, el);
//     pthread_cond_signal(&queue->cond);

//     int destroy = --(service->refCount) == 0;
//     pthread_mutex_unlock(&queue->lock);

//     if (destroy) {
//         reqlist_destroy(service->queue);
//         free(service);
//     }
//     return 0;
// }

// RequestT *popActiveRequest(unsigned int requestId) {
//     pthread_mutex_lock(&activeRequests->lock);
    
//     RequestElementT *el;
//     RequestT* req = NULL;
//     CDL_SEARCH_SCALAR(activeRequests->list, el, req->id, requestId);
//     if (el) {
//         req = el->req;
//         CDL_DELETE(activeRequests->list, el);
//         free(el);
//     }

//     pthread_mutex_unlock(&activeRequests->lock);
//     return req;
// }

// int cleanAckedRequests(unsigned int clientId, unsigned int sequenceNumber) {
//     pthread_mutex_lock(&cachedRequests->lock);
    
//     RequestElementT *el, *tmp1, *tmp2;
//     CDL_FOREACH_SAFE(cachedRequests->list, el, tmp1, tmp2) {
//         if (el->req->packet.clientId == clientId && 
//             el->req->packet.sequenceNumber <= sequenceNumber) {
//             CDL_DELETE(cachedRequests->list, el);
//             free(el->req->packet.payload);
//             free(el->req);
//             free(el);
//             pthread_mutex_unlock(&cachedRequests->lock);
//             return 0;
//         }
//     }

//     pthread_mutex_unlock(&cachedRequests->lock);
//     return -1;
// }

// int cleanOrphanedRequests(unsigned int clientId) {
//     pthread_mutex_lock(&cachedRequests->lock);
    
//     RequestElementT *el, *tmp1, *tmp2;
//     CDL_FOREACH_SAFE(cachedRequests->list, el, tmp1, tmp2) {
//         if (el->req->packet.clientId != clientId) {
//             CDL_DELETE(cachedRequests->list, el);
//             free(el->req->packet.payload);
//             free(el->req);
//             free(el);
//             pthread_mutex_unlock(&cachedRequests->lock);
//             return 0;
//         }
//     }

//     pthread_mutex_unlock(&cachedRequests->lock);
//     return -1;
// }