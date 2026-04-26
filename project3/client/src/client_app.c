#include "client.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MAX_BUF 4096

static void print_usage(char *prog) {
    fprintf(stderr, "Usage: %s <server_addr> <port> <block_size> <max_cached_blocks> <freshT>\n", prog);
}

static void print_commands() {
    printf("Commands:\n");
    printf("  open <filename>\n");
    printf("  read <fd> <n>\n");
    printf("  write <fd> <data>\n");
    printf("  lseek <fd> <pos> <whence>   (whence: 0=SET 1=CUR 2=END)\n");
    printf("  truncate <fd> <length>\n");
    printf("  close <fd>\n");
    printf("  help\n");
    printf("  quit\n");
}

int main(int argc, char *argv[]) {
    if (argc != 6) {
        print_usage(argv[0]);
        return 1;
    }

    char *server_addr      = argv[1];
    int   server_port      = atoi(argv[2]);
    int   block_size       = atoi(argv[3]);
    int   max_cached       = atoi(argv[4]);
    int   fresh_time       = atoi(argv[5]);

    if (mynfs_init(server_addr, server_port, block_size, max_cached, fresh_time) != 0) {
        fprintf(stderr, "mynfs_init failed\n");
        return 1;
    }
    printf("Connected to %s:%d\n", server_addr, server_port);
    print_commands();

    char line[MAX_BUF];
    char cmd[64];

    while (1) {
        printf("> ");
        fflush(stdout);

        if (!fgets(line, sizeof(line), stdin))
            break;

        /* strip newline */
        line[strcspn(line, "\n")] = '\0';
        if (strlen(line) == 0) continue;

        sscanf(line, "%63s", cmd);

        if (strcmp(cmd, "quit") == 0) {
            break;

        } else if (strcmp(cmd, "help") == 0) {
            print_commands();

        } else if (strcmp(cmd, "open") == 0) {
            char fname[256];
            if (sscanf(line, "%*s %255s", fname) != 1) {
                fprintf(stderr, "Usage: open <filename>\n");
                continue;
            }
            int fd = mynfs_open(fname);
            if (fd < 0)
                fprintf(stderr, "open failed\n");
            else
                printf("fd = %d\n", fd);

        } else if (strcmp(cmd, "read") == 0) {
            int fd;
            long n;
            if (sscanf(line, "%*s %d %ld", &fd, &n) != 2) {
                fprintf(stderr, "Usage: read <fd> <n>\n");
                continue;
            }
            if (n <= 0 || n > MAX_BUF) {
                fprintf(stderr, "n must be between 1 and %d\n", MAX_BUF);
                continue;
            }
            char *buf = malloc(n + 1);
            if (!buf) { fprintf(stderr, "malloc failed\n"); continue; }
            long bytes = mynfs_read(fd, buf, n);
            if (bytes < 0) {
                fprintf(stderr, "read failed\n");
            } else {
                buf[bytes] = '\0';
                printf("read %ld bytes: ", bytes);
                for (int i = 0; i < bytes; i++) {
                    putchar(buf[i]);
                }
                printf("\n");
            }
            free(buf);

        } else if (strcmp(cmd, "write") == 0) {
            int fd;
            char data[MAX_BUF];
            if (sscanf(line, "%*s %d %4095[^\n]", &fd, data) != 2) {
                fprintf(stderr, "Usage: write <fd> <data>\n");
                continue;
            }
            long bytes = mynfs_write(fd, data, strlen(data));
            if (bytes < 0)
                fprintf(stderr, "write failed\n");
            else
                printf("wrote %ld bytes\n", bytes);

        } else if (strcmp(cmd, "lseek") == 0) {
            int fd, whence;
            long pos;
            if (sscanf(line, "%*s %d %ld %d", &fd, &pos, &whence) != 3) {
                fprintf(stderr, "Usage: lseek <fd> <pos> <whence>\n");
                continue;
            }
            long result = mynfs_lseek(fd, pos, whence);
            if (result < 0)
                fprintf(stderr, "lseek failed\n");
            else
                printf("position = %ld\n", result);

        } else if (strcmp(cmd, "truncate") == 0) {
            int fd;
            long length;
            if (sscanf(line, "%*s %d %ld", &fd, &length) != 2) {
                fprintf(stderr, "Usage: truncate <fd> <length>\n");
                continue;
            }
            if (mynfs_truncate(fd, length) < 0)
                fprintf(stderr, "truncate failed\n");
            else
                printf("truncated to %ld bytes\n", length);

        } else if (strcmp(cmd, "close") == 0) {
            int fd;
            if (sscanf(line, "%*s %d", &fd) != 1) {
                fprintf(stderr, "Usage: close <fd>\n");
                continue;
            }
            if (mynfs_close(fd) < 0)
                fprintf(stderr, "close failed\n");
            else
                printf("closed fd %d\n", fd);

        } else {
            fprintf(stderr, "Unknown command: %s\n", cmd);
        }
    }

    mynfs_shutdown();
    return 0;
}