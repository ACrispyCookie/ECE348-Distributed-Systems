#include "threadsafe_list_s.h"

ThreadsafeListST *singly_create(int (*cmp) (void *, void *)) {
    ThreadsafeListST *list = (ThreadsafeListST *) malloc(sizeof(ThreadsafeListST));
    if (list == NULL)
        return NULL;

    pthread_mutex_init(&list->lock, NULL);
    list->head = NULL;
    list->cmp = cmp;

    return list;
}

void singly_destroy(ThreadsafeListST *list) {
    ThreadsafeElementST *elt, *tmp;

    pthread_mutex_destroy(&list->lock);
    LL_FOREACH_SAFE(list->head,elt,tmp) {
        LL_DELETE(list->head,elt);
        free(elt->data);
        free(elt);
    }

    free(list);
}

int singly_append(ThreadsafeListST *list, void *data) {
    ThreadsafeElementST *element = (ThreadsafeElementST *) malloc(sizeof(ThreadsafeElementST));
    if (element == NULL)
        return -1;
    
    element->data = data;

    pthread_mutex_lock(&list->lock);
    LL_APPEND(list->head, element);
    pthread_mutex_unlock(&list->lock);

    return 0;
}

void *singly_get(ThreadsafeListST *list, void *key) {
    pthread_mutex_lock(&list->lock);

    ThreadsafeElementST *elt = NULL;
    LL_FOREACH(list->head, elt) {
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

void *singly_pop(ThreadsafeListST *list, void *key) {
    pthread_mutex_lock(&list->lock);

    ThreadsafeElementST *elt = NULL;
    LL_FOREACH(list->head, elt) {
        if (list->cmp(elt->data, key) == 0) {
            break;
        }
    }
    if (elt == NULL) {
        pthread_mutex_unlock(&list->lock);
        return NULL;
    }
    LL_DELETE(list->head, elt);

    pthread_mutex_unlock(&list->lock);

    void *data = elt->data;
    free(elt);

    return data;
}

void *singly_pop_pos(ThreadsafeListST *list, int position) {
    if (position < 0)
        return NULL;

    pthread_mutex_lock(&list->lock);

    ThreadsafeElementST *elt = NULL;
    int current_pos = 0;
    
    LL_FOREACH(list->head, elt) {
        if (current_pos == position) {
            break;
        }
        current_pos++;
    }
    if (elt == NULL) {
        pthread_mutex_unlock(&list->lock);
        return NULL;
    }

    LL_DELETE(list->head, elt);
    pthread_mutex_unlock(&list->lock);

    void *data = elt->data;
    free(elt);

    return data;
}

int singly_remove(ThreadsafeListST *list, void *key) {
    void *data = singly_pop(list, key);
    free(data);

    return data == NULL ? -1 : 0;
}