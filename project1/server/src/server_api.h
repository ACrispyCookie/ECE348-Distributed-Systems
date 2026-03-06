#ifndef SERVER_API_H
#define SERVER_API_H

#define MAX_PAYLOAD_SIZE 4096
#define DISCOVERY_PORT 12345

// Initialize server
void init(int port);

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
    Unregisters the specified service.

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