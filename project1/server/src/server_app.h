#ifndef SERVER_APP_H
#define SERVER_APP_H

typedef enum Service {
    PRIMALITY
} ServiceT;

typedef struct AppRequest {
    unsigned long long *numbers;
    int count;
} AppRequestT;

typedef struct AppResponse {
    int *booleans;
    int count;
} AppResponseT;

void *runWorker(void *args);
int isPrime(const unsigned long long num);

AppRequestT *deserializeAppPacket(void *request, int length);
void *serializeAppPacket(AppResponseT *response, int *length);

#endif