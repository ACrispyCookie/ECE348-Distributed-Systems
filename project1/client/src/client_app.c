#include "client_api.h"
#include <stdio.h>

int main(int argc, char *argv[]) {

    init("config.txt");

    char *req = "Hello world!";
    int rsp;
    doRequestReply(0, req, sizeof(req), (void *) &rsp, NULL);
    printf("Reply was %d\n", rsp);

    return 0;
}