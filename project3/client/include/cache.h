#ifndef CACHE_H
#define CACHE_H
#include <stdint.h>
#include <time.h>
#include "uthash.h"

typedef struct {
    unsigned int server_fid;
    unsigned int block_index;
} CacheKey;

typedef struct CacheBlockT {
    CacheKey key;
    uint64_t mtime, mtimensec;
    time_t fetch_time;
    unsigned int data_len;
    unsigned char *data;
    struct CacheBlockT *lru_prev;
    struct CacheBlockT *lru_next;
    UT_hash_handle hh;
} CacheBlockT;

void cache_init(int max_blocks, int block_sz, int fresh_t);
CacheBlockT *cache_get(unsigned int server_fid, unsigned int block_index);
void cache_put(unsigned int server_fid, unsigned int block_index,
               unsigned char *data, unsigned int data_len, uint64_t mtime, uint64_t mtimensec);
int cache_is_fresh(CacheBlockT *block);

void cache_remap(unsigned int old_fid, unsigned int new_fid);
void cache_revalidate(CacheBlockT *block);
void cache_invalidate_past_offset(unsigned int server_fid, long new_length);
void cache_destroy();

#endif