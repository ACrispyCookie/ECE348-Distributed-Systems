#include "server_api.h"
#include "server_app.h"
#include <sys/socket.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>

int main() {

    init();
    registerService(PRIMALITY);

    // while (0) {
        char buf[MAX_PAYLOAD_SIZE];
    //     for (int i = 0; i < 100; i++) buf[i] = 0;
        getRequest(PRIMALITY, buf, NULL);
    //     buf[99] = '\0';
    //     printf("Received %s\n", buf);
    //     printf("Sending %d\n", 1);

    //     int rep;
    //     sendReply(1, (void *) &rep, sizeof(rep));
    // }

    unregisterService(PRIMALITY);
    cleanUp();

    return 0;
}