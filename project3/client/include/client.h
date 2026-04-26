#ifndef CLIENT_H
#define CLIENT_H
#include "uthash.h"
#include "protocol.h"
#include <pthread.h>
#include <arpa/inet.h>

/** Maximum number of UDP retransmit attempts before req_send gives up. */
#define MAX_RETRIES 5

/** Seconds to wait for a response before retransmitting. */
#define TIMEOUT_SEC 2

/**
 * Maximum number of times req_send_with_reopen will attempt to reopen a file
 * after receiving a NOT_FOUND response, which indicates the server has lost
 * its file table (e.g. after a crash and reboot).
 */
#define MAX_REOPEN_RETRIES 5

/**
 * Represents a file that has been opened on the server.
 *
 * Multiple ClientFileT entries (i.e. multiple calls to mynfs_open for the
 * same filename) share a single ServerFileT. The ref_count tracks how many
 * client file descriptors are currently open for this file. When ref_count
 * drops to zero, the entry is removed from the hash table and freed.
 *
 * Keyed by filename in the server_files hash table.
 */
typedef struct {
    unsigned int fid;
    char *filename;
    uint64_t size;
    uint64_t mtime;
    unsigned int ref_count;
    UT_hash_handle hh;
} ServerFileT;

/**
 * Represents a file descriptor returned to the application by mynfs_open.
 *
 * Each call to mynfs_open produces a new ClientFileT with a unique fid,
 * even if the same file is already open. The read/write position is tracked
 * per ClientFileT so that multiple descriptors to the same file maintain
 * independent positions, consistent with POSIX semantics.
 *
 * Keyed by fid in the client_files hash table.
 */
typedef struct {
    unsigned int fid;
    long pos;
    ServerFileT *server_file;
    UT_hash_handle hh;
} ClientFileT;

/**
 * Represents an in-flight request waiting for a response from the server.
 *
 * RequestT instances are stack-allocated inside req_send and inserted into
 * the waiting hash table for the duration of the call. The receiver thread
 * matches incoming responses by id and signals the condition variable to
 * wake up the blocked req_send call.
 *
 * Keyed by id in the waiting hash table.
 */
typedef struct {
    uint32_t id; // Request identifier.
    PacketT *packet; // The outgoing request packet.
    PacketT *resp; // Set by the receiver thread when response arrives.
    pthread_cond_t cond; // Signaled by the receiver thread on response.
    UT_hash_handle hh;
} RequestT;

/**
 * Initialize the NFS client.
 *
 * Opens a UDP socket and connects it to the server. Starts the background
 * receiver thread that matches incoming responses to waiting requests.
 * Must be called before any other mynfs_* function.
 *
 * @param server_addr      IPv4 address of the server (e.g. "127.0.0.1").
 * @param server_port      UDP port the server is listening on.
 * @param block_size       Size in bytes of file blocks used for caching.
 * @param max_cached_blocks Maximum number of blocks to keep in the cache.
 * @param freshT           Cache freshness time in seconds. A cached block
 *                         older than this is revalidated against the server.
 * @return 0 on success, -1 on failure.
 */
int mynfs_init(char *server_addr, int server_port, int block_size, int max_cached_blocks, int freshT);

/**
 * Shut down the NFS client.
 *
 * Closes the socket, stops the receiver thread, and frees all internal
 * state. Any requests in flight will be woken up and will return NULL.
 * Must not be called concurrently with any other mynfs_* function.
 */
void mynfs_shutdown();

/**
 * Open a file on the remote server.
 *
 * If the file does not exist it is created. If the file is already open
 * by this client, a new independent file descriptor is returned sharing
 * the same server-side file entry.
 *
 * @param fname  Path to the file relative to the server's root directory.
 * @return A non-negative file descriptor on success, -1 on failure.
 */
int mynfs_open(char *fname);

/**
 * Read bytes from an open file.
 *
 * Reads up to n bytes from the file referenced by fd starting at the
 * current position. The position is advanced by the number of bytes read.
 * Uses the client-side block cache to avoid unnecessary network transfers.
 *
 * @param fd   File descriptor returned by mynfs_open.
 * @param buf  Buffer to read into. Must be at least n bytes.
 * @param n    Maximum number of bytes to read.
 * @return Number of bytes read (may be less than n at end of file),
 *         or -1 on error.
 */
long mynfs_read(int fd, void *buf, long n);

/**
 * Write bytes to an open file.
 *
 * Writes exactly n bytes to the file referenced by fd starting at the
 * current position. Uses a write-through policy: data is sent to the
 * server immediately and the cache is invalidated for the affected blocks.
 * The position is advanced by n bytes on success.
 *
 * @param fd   File descriptor returned by mynfs_open.
 * @param buf  Buffer containing the data to write.
 * @param n    Number of bytes to write.
 * @return Number of bytes written, or -1 on error.
 */
long mynfs_write(int fd, void *buf, long n);

/**
 * Move the read/write position of an open file.
 *
 * @param fd     File descriptor returned by mynfs_open.
 * @param pos    Byte offset, interpreted according to whence.
 * @param whence SEEK_SET: offset from start of file.
 *               SEEK_CUR: offset from current position.
 *               SEEK_END: offset from end of file.
 * @return The resulting position in bytes from the start of the file,
 *         or -1 if the resulting position would be negative or on error.
 */
long mynfs_lseek(int fd, long pos, int whence);

/**
 * Truncate an open file to a given length.
 *
 * If the file is larger than length, the extra data is discarded.
 * If the file is smaller, it is extended with zero bytes.
 * Updates the cached file size and modification timestamp on success.
 *
 * @param fd     File descriptor returned by mynfs_open.
 * @param length New file size in bytes.
 * @return 0 on success, -1 on error.
 */
int mynfs_truncate(int fd, long length);

/**
 * Close an open file descriptor.
 *
 * Decrements the reference count of the associated server file entry.
 * If this was the last descriptor referencing the file, the server file
 * entry is removed from the cache and freed. Does not notify the server.
 *
 * @param fd  File descriptor returned by mynfs_open.
 * @return 0 on success, -1 if fd was not found.
 */
int mynfs_close(int fd);

#endif