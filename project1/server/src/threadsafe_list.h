#ifndef THREADSAFE_LIST_H
#define THREADSAFE_LIST_H
#include <pthread.h>
#include <stdlib.h>
#include "utlist.h"

typedef struct DoublyElement {
    void *data;
    struct DoublyElement *prev, *next;
} ThreadsafeElementT;

typedef struct {
    pthread_mutex_t lock;
    pthread_cond_t cond;
    int (*cmp)(void *, void *);
    ThreadsafeElementT *head;
} ThreadsafeListT;

/*
    Creates a thread safe doubly linked
    list and returns a reference to it.

    Returns:
    a reference to the list - on success
    NULL - on error
*/
ThreadsafeListT *tslist_create(int (*cmp) (void *, void *));

/*
    Destroy a doubly linked list.
*/
void tslist_destroy(ThreadsafeListT *list, void (*run) (ThreadsafeElementT *));

/*
    Appends an element to a doubly 
    linked list.

    Returns:
    0 - on success
    -1 - on error
*/
int tslist_append(ThreadsafeListT *list, void *data);

/*
    Finds an element in a doubly linked
    list.

    Returns:
    the data of the element - on success
    NULL - on error
*/
void *tslist_get(ThreadsafeListT *list, void *key);

/*
    Finds an element in a doubly linked
    list with a custom comparator.

    Returns:
    the data of the element - on success
    NULL - on error
*/
void *tslist_get_custom(ThreadsafeListT *list, void *key, int (*cmp) (void *, void *));

/*
    Executes the function run for each element
    in the doubly linked list.

    Returns:
    0 - on success
    -1 - on error
*/
int tslist_for_each(ThreadsafeListT *list, void (*run) (ThreadsafeElementT *));

/*
    Finds an element in a doubly linked
    list, removes it and returns it.

    Returns:
    the data of the element - on success
    NULL - on error
*/
void *tslist_pop(ThreadsafeListT *list, void *key);

/*
    Removes the first element of the list
    with the option to either fail if there
    is no elements on the list or wait
    until an element is appended.

    Returns:
    the data of the element - on success
    NULL - on error
*/
void *tslist_pop_head(ThreadsafeListT *list, int blocking);

/*
    Removes an element from the list

    Returns:
    0 - on success
    -1 - on error
*/
int tslist_remove(ThreadsafeListT *list, void *key);

#endif