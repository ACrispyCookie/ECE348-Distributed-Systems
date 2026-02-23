#include "client_api.h"
#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <string.h>

static int port;
static char ip[INET_ADDRSTRLEN];

void init(char *config) {
    // Open the configuration file for reading
    FILE *file = fopen(config, "r");
    if (file == NULL) {
        perror("Error opening configuration file");
        exit(EXIT_FAILURE);
    }

    // Read the IP address from the first line. 
    // %15s ensures we don't overflow the INET_ADDRSTRLEN (16 bytes) buffer.
    if (fscanf(file, "%15s", ip) != 1) {
        fprintf(stderr, "Error: Could not read IP address from %s\n", config);
        fclose(file);
        exit(EXIT_FAILURE);
    }

    // Read the port number from the second line.
    if (fscanf(file, "%d", &port) != 1) {
        fprintf(stderr, "Error: Could not read port number from %s\n", config);
        fclose(file);
        exit(EXIT_FAILURE);
    }

    // Close the file
    fclose(file);
    
    // Optional: Print to verify (can be removed in production)
    printf("Initialized with IP: %s, Port: %d\n", ip, port);
}

int doRequestReply(int svcid, void *reqbuf, size_t reqlen, void *rspbuf, size_t *rsplen) {
    int sockfd;
    struct sockaddr_in server_addr;
    socklen_t addr_len = sizeof(server_addr);
    int bytes_sent, bytes_received;

    // 1. Create a UDP socket (SOCK_DGRAM)
    if ((sockfd = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
        perror("Socket creation failed");
        return -1;
    }

    // 2. Configure the server address structure
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(port); // Convert port to network byte order

    // Convert the IP string to a network address structure
    if (inet_pton(AF_INET, ip, &server_addr.sin_addr) <= 0) {
        perror("Invalid address or Address not supported");
        close(sockfd);
        return -1;
    }

    // 3. Send the request packet to the server
    bytes_sent = sendto(sockfd, reqbuf, reqlen, 0, 
                        (struct sockaddr *)&server_addr, sizeof(server_addr));
    if (bytes_sent < 0) {
        perror("Failed to send data");
        close(sockfd);
        return -1;
    }

    // 4. Wait for and receive the reply from the server
    // Note: We pass &server_addr and &addr_len to know who sent the reply.
    bytes_received = recvfrom(sockfd, rspbuf, 100, 0, 
                              (struct sockaddr *)&server_addr, &addr_len);
    if (bytes_received < 0) {
        perror("Failed to receive data");
        close(sockfd);
        return -1;
    }

    if (rsplen != NULL)
        *rsplen = bytes_received;

    // 5. Close the socket to clean up resources
    close(sockfd);

    // Return the number of bytes received in the reply
    return bytes_received;
}