#ifndef SERVER_API_H
#define SERVER_API_H

#define MAX_PAYLOAD_SIZE 4096

// Initialize server
void init();

// Start shutdown process
void triggerShutdown();

// Clean up. Should be called only after the shutdown process
void cleanUp();

/*
    Registers the specified service.

    Returns:
    0 - If the registration is successful
    -1 - If the registration failed 
*/
int registerService(int serviceId);

/*
    Unregisters the specified service. Can only
    be called when every thread waiting on getRequest
    for the specific service has finished execution.
    TODO Make this thread-safe

    Returns:
    0 - If the registration is successful
    -1 - If the registration failed 
*/
int unregisterService(int serviceId);

/*
    Gets the next request.

    Returns:
    The request ID of the next request.
*/
int getRequest(int serviceId, void *buf, int *len);

/*
    Sends a reply for the specified request
*/
void sendReply(int requestId, void *buf, int len);

#endif