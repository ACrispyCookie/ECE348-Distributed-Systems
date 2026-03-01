#include "threadsafe_list.h"

ThreadsafeListT *tslist_create(int (*cmp) (void *, void *)) {
    ThreadsafeListT *list = (ThreadsafeListT *) malloc(sizeof(ThreadsafeListT));
    if (list == NULL)
        return NULL;

    pthread_mutex_init(&list->lock, NULL);
    pthread_cond_init(&list->cond, NULL);
    list->head = NULL;
    list->cmp = cmp;

    return list;
}

void tslist_destroy(ThreadsafeListT *list, void (*clean) (ThreadsafeElementT *)) {
    ThreadsafeElementT *elt, *tmp1, *tmp2;

    pthread_mutex_destroy(&list->lock);
    CDL_FOREACH_SAFE(list->head,elt,tmp1,tmp2) {
        CDL_DELETE(list->head,elt);
        clean(elt);
        free(elt);
    }

    free(list);
}

int tslist_append(ThreadsafeListT *list, void *data) {
    ThreadsafeElementT *element = (ThreadsafeElementT *) malloc(sizeof(ThreadsafeElementT));
    if (element == NULL)
        return -1;
    
    element->data = data;

    pthread_mutex_lock(&list->lock);
    CDL_APPEND(list->head, element);
    pthread_mutex_unlock(&list->lock);

    return 0;
}

void *tslist_get(ThreadsafeListT *list, void *key) {
    return tslist_get_custom(list, key, list->cmp);
}

void *tslist_get_custom(ThreadsafeListT *list, void *key, int (*cmp) (void *, void *)) {
    pthread_mutex_lock(&list->lock);

    ThreadsafeElementT *elt = NULL;
    CDL_FOREACH(list->head, elt) {
        if (cmp(elt->data, key) == 0) {
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

int tslist_for_each(ThreadsafeListT *list, void (*run) (ThreadsafeElementT *)) {
    pthread_mutex_lock(&list->lock);

    ThreadsafeElementT *elt, *tmp1, *tmp2;
    CDL_FOREACH_SAFE(list->head, elt, tmp1, tmp2) {
        run(elt->data);
    }

    pthread_mutex_unlock(&list->lock);
    return 0;
}

void *tslist_pop(ThreadsafeListT *list, void *key) {
    pthread_mutex_lock(&list->lock);

    ThreadsafeElementT *elt = NULL;
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

void *tslist_pop_head(ThreadsafeListT *list, int blocking) {
    pthread_mutex_lock(&list->lock);

    ThreadsafeElementT *elt = NULL;
    if (list->head == NULL || !blocking)
        return NULL;
    else if (list->head == NULL)
        while (list->head == NULL)
            pthread_cond_wait(&list->cond, &list->lock);

    elt = list->head;
    CDL_DELETE(list->head, elt);
    pthread_mutex_unlock(&list->lock);

    void *data = elt->data;
    free(elt);

    return data;
}

int tslist_remove(ThreadsafeListT *list, void *key) {
    void *data = tslist_pop(list, key);
    free(data);

    return data == NULL ? -1 : 0;
}