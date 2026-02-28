#ifndef THREADSAFE_LIST_S_H
#define THREADSAFE_LIST_S_H
#include <pthread.h>
#include <stdlib.h>
#include "utlist.h"

typedef struct SinglyElement {
    void *data;
    struct SinglyElement *next;
} ThreadsafeElementST;

typedef struct {
    pthread_mutex_t lock;
    int (*cmp)(void *, void *);
    ThreadsafeElementST *head;
} ThreadsafeListST;

/*
    Creates a thread safe singly linked
    list and returns a reference to it.

    Returns:
    a reference to the list - on success
    NULL - on error
*/
ThreadsafeListST *singly_create(int (*cmp) (void *, void *));

/*
    Appends an element to a singly 
    linked list.

    Returns:
    0 - on success
    -1 - on error
*/
int singly_append(ThreadsafeListST *list, void *data);

/*
    Finds an element in a singly linked
    list.

    Returns:
    the data of the element - on success
    NULL - on error
*/
void *singly_get(ThreadsafeListST *list, void *key);

/*
    Finds an element in a singly linked
    list, removes it and returns it.

    Returns:
    the data of the element - on success
    NULL - on error
*/
void *singly_pop(ThreadsafeListST *list, void *key);

/*
    Removes an element in the specified
    position in the singly linked list.

    Returns:
    the data of the element - on success
    NULL - on error
*/
void *singly_pop_pos(ThreadsafeListST *list, int position);

/*
    Removes an element from the list

    Returns:
    0 - on success
    -1 - on error
*/
int singly_remove(ThreadsafeListST *list, void *key);

#endif