#ifndef SERVER_API_H
#define SERVER_API_H

typedef struct Packet {
    
} PacketT;

extern int sockfd;

// Initialize server
void init();

/*
    Registers the specified service

    Returns:
    0 - If the registration is successful
    -1 - If the registration failed 
*/
int registerService(int svcid);

/*
    Unregisters the specified service

    Returns:
    0 - If the registration is successful
    -1 - If the registration failed 
*/
int unregisterService(int svcid);

/*
    Gets the next request.

    Returns:
    The request ID of the next request.
*/
int getRequest(int svcid, void *buf, int *len);

/*
    Sends a reply for the specified request
*/
void sendReply(int reqid, void *buf, int len);

#endif