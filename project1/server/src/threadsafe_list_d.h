#ifndef THREADSAFE_LIST_D_H
#define THREADSAFE_LIST_D_H
#include <pthread.h>
#include <stdlib.h>
#include "utlist.h"

typedef struct DoublyElement {
    void *data;
    struct DoublyElement *prev, *next;
} ThreadsafeElementDT;

typedef struct {
    pthread_mutex_t lock;
    int (*cmp)(void *, void *);
    ThreadsafeElementDT *head;
} ThreadsafeListDT;

/*
    Creates a thread safe doubly linked
    list and returns a reference to it.

    Returns:
    a reference to the list - on success
    NULL - on error
*/
ThreadsafeListDT *doubly_create(int (*cmp) (void *, void *));

/*
    Appends an element to a doubly 
    linked list.

    Returns:
    0 - on success
    -1 - on error
*/
int doubly_append(ThreadsafeListDT *list, void *data);

/*
    Finds an element in a doubly linked
    list.

    Returns:
    the data of the element - on success
    NULL - on error
*/
void *doubly_get(ThreadsafeListDT *list, void *key);

/*
    Finds an element in a doubly linked
    list, removes it and returns it.

    Returns:
    the data of the element - on success
    NULL - on error
*/
void *doubly_pop(ThreadsafeListDT *list, void *key);

/*
    Removes an element in the specified
    position in the doubly linked list.

    Returns:
    the data of the element - on success
    NULL - on error
*/
void *doubly_pop_pos(ThreadsafeListDT *list, int position);

/*
    Removes an element from the list

    Returns:
    0 - on success
    -1 - on error
*/
int doubly_remove(ThreadsafeListDT *list, void *key);

#endif