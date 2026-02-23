#ifndef SERVER_API_H
#define SERVER_API_H

#define MAX_PAYLOAD_SIZE 4096

typedef enum MessageType {
    REQ,
    ACK,
    RSP
} MessageTypeT;

typedef struct Packet {
    MessageTypeT type;
    int sequenceNumber;
    int serviceId;
    int payloadLength;
} PacketT;


// Initialize server
void init();

// Clean up and stop server
void cleanUp();

/*
    Registers the specified service

    Returns:
    0 - If the registration is successful
    -1 - If the registration failed 
*/
int registerService(int serviceId);

/*
    Unregisters the specified service

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
void sendReply(int reqid, void *buf, int len);

#endif