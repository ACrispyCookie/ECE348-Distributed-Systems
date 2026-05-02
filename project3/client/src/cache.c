#include "cache.h"

static CacheBlockT *cache = NULL;
static CacheBlockT *lru_head = NULL;
static CacheBlockT *lru_tail = NULL;
static int cache_count = 0;

static int max_cached_blocks;
static int block_size;
static int freshT;

void cache_init(int max_blocks, int block_sz, int fresh_t) {
    max_cached_blocks = max_blocks;
    block_size = block_sz;
    freshT = fresh_t;
}

static void lru_remove(CacheBlockT *b) {
    if (b->lru_prev) 
        b->lru_prev->lru_next = b->lru_next;
    else 
        lru_head = b->lru_next;
    
    if (b->lru_next) 
        b->lru_next->lru_prev = b->lru_prev;
    else 
        lru_tail = b->lru_prev;
    
    b->lru_prev = b->lru_next = NULL;
}

static void lru_push_front(CacheBlockT *b) {
    b->lru_prev = NULL;
    b->lru_next = lru_head;

    if (lru_head) 
        lru_head->lru_prev = b;
    else
        lru_tail = b;
    lru_head = b;
}

static void cache_evict_lru() {
    CacheBlockT *b = lru_tail;
    if (!b) 
        return;
    lru_remove(b);
    HASH_DELETE(hh, cache, b);
    free(b->data);
    free(b);
    cache_count--;
}

CacheBlockT *cache_get(unsigned int server_fid, unsigned int block_index) {
    CacheKey key = { server_fid, block_index };
    CacheBlockT *b;
    HASH_FIND(hh, cache, &key, sizeof(CacheKey), b);
    if (b) {
        lru_remove(b);
        lru_push_front(b);
    }
    return b;
}

void cache_put(unsigned int server_fid, unsigned int block_index,
               unsigned char *data, unsigned int data_len, uint64_t mtime, uint64_t mtimensec) {
    CacheKey key = { server_fid, block_index };
    CacheBlockT *b;
    HASH_FIND(hh, cache, &key, sizeof(CacheKey), b);
    if (b) {
        memcpy(b->data, data, data_len);
        b->data_len = data_len;
        b->mtime = mtime;
        b->mtimensec = mtimensec;
        b->fetch_time = time(NULL);
        lru_remove(b);
        lru_push_front(b);
        return;
    }

    while (cache_count >= max_cached_blocks)
        cache_evict_lru();

    b = malloc(sizeof(CacheBlockT));
    b->key = key;
    b->mtime = mtime;
    b->mtimensec = mtimensec;
    b->fetch_time = time(NULL);
    b->data_len = data_len;
    b->data = malloc(block_size);
    memcpy(b->data, data, data_len);
    b->lru_prev = b->lru_next = NULL;
    HASH_ADD(hh, cache, key, sizeof(CacheKey), b);
    lru_push_front(b);
    cache_count++;
}

int cache_is_fresh(CacheBlockT *block) {
    return (time(NULL) - block->fetch_time) < freshT;
}

void cache_revalidate(CacheBlockT *block) {
    block->fetch_time = time(NULL);
}

void cache_invalidate_past_offset(unsigned int server_fid, long new_length) {
    unsigned int starting_block_to_delete = new_length / block_size;
    
    CacheBlockT *b, *tmp;
    HASH_ITER(hh, cache, b, tmp) {
        if (b->key.server_fid == server_fid && b->key.block_index >= starting_block_to_delete) {
            lru_remove(b);
            HASH_DELETE(hh, cache, b);
            free(b->data);
            free(b);
            cache_count--;
        }
    }
}

void cache_remap(unsigned int old_fid, unsigned int new_fid) {
    CacheBlockT *b, *tmp;
    
    HASH_ITER(hh, cache, b, tmp) {
        if (b->key.server_fid == old_fid) {
            HASH_DELETE(hh, cache, b);
            b->key.server_fid = new_fid;
            HASH_ADD(hh, cache, key, sizeof(CacheKey), b);
        }
    }
}

void cache_destroy() {
    CacheBlockT *b, *tmp;
    HASH_ITER(hh, cache, b, tmp) {
        HASH_DELETE(hh, cache, b);
        free(b->data);
        free(b);
    }
    cache_count = 0;
    lru_head = lru_tail = NULL;
}