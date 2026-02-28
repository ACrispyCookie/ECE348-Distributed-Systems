#include "threadsafe_list_d.h"

ThreadsafeListDT *doubly_create(int (*cmp) (void *, void *)) {
    ThreadsafeListDT *list = (ThreadsafeListDT *) malloc(sizeof(ThreadsafeListDT));
    if (list == NULL)
        return NULL;

    pthread_mutex_init(&list->lock, NULL);
    list->head = NULL;
    list->cmp = cmp;

    return list;
}

void doubly_destroy(ThreadsafeListDT *list) {
    ThreadsafeElementDT *elt, *tmp1, *tmp2;

    pthread_mutex_destroy(&list->lock);
    CDL_FOREACH_SAFE(list->head,elt,tmp1,tmp2) {
        CDL_DELETE(list->head,elt);
        free(elt->data);
        free(elt);
    }

    free(list);
}

int doubly_append(ThreadsafeListDT *list, void *data) {
    ThreadsafeElementDT *element = (ThreadsafeElementDT *) malloc(sizeof(ThreadsafeElementDT));
    if (element == NULL)
        return -1;
    
    element->data = data;

    pthread_mutex_lock(&list->lock);
    CDL_APPEND(list->head, element);
    pthread_mutex_unlock(&list->lock);

    return 0;
}

void *doubly_get(ThreadsafeListDT *list, void *key) {
    pthread_mutex_lock(&list->lock);

    ThreadsafeElementDT *elt = NULL;
    CDL_FOREACH(list->head, elt) {
        if (list->cmp(elt->data, key) == 0) {
            break;
        }
    }
    if (elt == NULL) {
        pthread_mutex_unlock(&list->lock);
        return NULL;
    } 
    void *data = elt->data;

    pthread_mutex_unlock(&list->lock);
    return data;
}

void *doubly_pop(ThreadsafeListDT *list, void *key) {
    pthread_mutex_lock(&list->lock);

    ThreadsafeElementDT *elt = NULL;
    CDL_FOREACH(list->head, elt) {
        if (list->cmp(elt->data, key) == 0) {
            break;
        }
    }
    if (elt == NULL) {
        pthread_mutex_unlock(&list->lock);
        return NULL;
    }
    CDL_DELETE(list->head, elt);

    pthread_mutex_unlock(&list->lock);

    void *data = elt->data;
    free(elt);

    return data;
}

void *doubly_pop_pos(ThreadsafeListDT *list, int position) {
    if (position < 0)
        return NULL;

    pthread_mutex_lock(&list->lock);

    ThreadsafeElementDT *elt = NULL;
    int current_pos = 0;
    
    CDL_FOREACH(list->head, elt) {
        if (current_pos == position) {
            break;
        }
        current_pos++;
    }
    if (elt == NULL) {
        pthread_mutex_unlock(&list->lock);
        return NULL;
    }

    CDL_DELETE(list->head, elt);
    pthread_mutex_unlock(&list->lock);

    void *data = elt->data;
    free(elt);

    return data;
}

int doubly_remove(ThreadsafeListDT *list, void *key) {
    void *data = doubly_pop(list, key);
    free(data);

    return data == NULL ? -1 : 0;
}