#include "client.h"
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

// Extern the stats function
void mynfs_get_stats(unsigned long*, unsigned long*, unsigned long*, unsigned long*);
void mynfs_reset_stats();

double get_time_diff(struct timespec start, struct timespec end) {
    return (end.tv_sec - start.tv_sec) + (end.tv_nsec - start.tv_nsec) / 1e9;
}

int main(int argc, char *argv[]) {
    if (argc != 7) return 1;
    // Args: ip port block_size max_cache freshT filename

    int blk_sz = atoi(argv[3]);
    int max_c = atoi(argv[4]);
    int fresh = atoi(argv[5]);
    char *fname = argv[6];

    mynfs_init(argv[1], atoi(argv[2]), blk_sz, max_c, fresh);
    int fd = mynfs_open(fname);

    long read_size = 65536; // Read in 64KB chunks from the API
    char *buf = malloc(read_size);
    struct timespec start, end;

    mynfs_reset_stats();
    clock_gettime(CLOCK_MONOTONIC, &start);

    // SCENARIO: Read a 10MB file 5 times in a row
    long total_bytes_read = 0;
    for (int i = 0; i < 5; i++) {
        mynfs_lseek(fd, 0, SEEK_SET);
        long bytes;
        while ((bytes = mynfs_read(fd, buf, read_size)) > 0) {
            total_bytes_read += bytes;
        }
    }

    clock_gettime(CLOCK_MONOTONIC, &end);
    double elapsed = get_time_diff(start, end);
    double mbps = (total_bytes_read / 1024.0 / 1024.0) / elapsed;

    unsigned long p_s, p_r, b_s, b_r;
    mynfs_get_stats(&p_s, &p_r, &b_s, &b_r);

    // Output CSV format: BlockSize,CacheBlocks,FreshT,Time,MBps,PktsSent,PktsRecv,BytesSent,BytesRecv
    printf("%d,%d,%d,%.4f,%.2f,%lu,%lu,%lu,%lu\n", 
           blk_sz, max_c, fresh, elapsed, mbps, p_s, p_r, b_s, b_r);

    mynfs_close(fd);
    mynfs_shutdown();
    return 0;
}