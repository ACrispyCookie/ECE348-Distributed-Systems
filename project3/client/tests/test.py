import unittest
import subprocess

SERVER = "127.0.0.1"
PORT = "5000"
CLIENT = ["./build/client_app", SERVER, PORT, "4096", "16", "5"]

class ClientApp:
    def __init__(self):
        self.proc = subprocess.Popen(
            CLIENT,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1
        )
        self._read_until_prompt()

    def cmd(self, command):
        self.proc.stdin.write(command + "\n")
        self.proc.stdin.flush()
        return self._read_until_prompt()

    def _read_until_prompt(self):
        output = []
        current_line = ""
        while True:
            ch = self.proc.stdout.read(1)
            if not ch:
                break
            if ch == "\n":
                if current_line.strip():
                    output.append(current_line.strip())
                current_line = ""
            else:
                current_line += ch
                if current_line == "> ":
                    break
        return output

    def close(self):
        try:
            self.proc.stdin.write("quit\n")
            self.proc.stdin.flush()
            self.proc.wait(timeout=2)
        except Exception:
            self.proc.kill()
        finally:
            self.proc.stdin.close()
            self.proc.stdout.close()


def get_fd(output):
    for line in output:
        if line.startswith("fd = "):
            return int(line.split("=")[1].strip())
    return None

def get_value(output, prefix):
    for line in output:
        if line.startswith(prefix):
            return line[len(prefix):].strip()
    return None


class TestNFS(unittest.TestCase):

    def setUp(self):
        self.client = ClientApp()

    def tearDown(self):
        self.client.close()
        self.client.proc.stdin.close()
        self.client.proc.stdout.close()
        self.client.proc.kill()
        self.client.proc.wait()

    def test_01_open(self):
        out = self.client.cmd("open /test_open.txt")
        fd = get_fd(out)
        self.assertIsNotNone(fd, "open did not return a fd")
        self.assertGreater(fd, 0)

    def test_02_write_read(self):
        fd = get_fd(self.client.cmd("open /test_rw.txt"))
        out = self.client.cmd(f"write {fd} hello world")
        self.assertIn("wrote 11 bytes", out[0])
        self.client.cmd(f"lseek {fd} 0 0")
        out = self.client.cmd(f"read {fd} 11")
        self.assertEqual(get_value(out, "read 11 bytes: "), "hello world")

    def test_03_lseek_end(self):
        fd = get_fd(self.client.cmd("open /test_seek.txt"))
        self.client.cmd(f"write {fd} hello")
        out = self.client.cmd(f"lseek {fd} 0 2")
        self.assertEqual(get_value(out, "position = "), "5")

    def test_04_lseek_cur(self):
        fd = get_fd(self.client.cmd("open /test_seekcur.txt"))
        self.client.cmd(f"write {fd} hello world")
        self.client.cmd(f"lseek {fd} 0 0")
        self.client.cmd(f"lseek {fd} 3 1")
        out = self.client.cmd(f"read {fd} 5")
        self.assertEqual(get_value(out, "read 5 bytes: "), "lo wo")

    def test_05_truncate_size(self):
        fd = get_fd(self.client.cmd("open /test_trunc.txt"))
        self.client.cmd(f"write {fd} hello world")
        self.client.cmd(f"truncate {fd} 5")
        out = self.client.cmd(f"lseek {fd} 0 2")
        self.assertEqual(get_value(out, "position = "), "5")

    def test_06_truncate_read(self):
        fd = get_fd(self.client.cmd("open /test_trunc2.txt"))
        self.client.cmd(f"write {fd} hello world")
        self.client.cmd(f"truncate {fd} 5")
        self.client.cmd(f"lseek {fd} 0 0")
        out = self.client.cmd(f"read {fd} 5")
        self.assertEqual(get_value(out, "read 5 bytes: "), "hello")

    def test_07_two_fds_independent_positions(self):
        fd1 = get_fd(self.client.cmd("open /test_pos.txt"))
        fd2 = get_fd(self.client.cmd("open /test_pos.txt"))
        self.client.cmd(f"write {fd1} hello world")
        self.client.cmd(f"lseek {fd1} 3 0")
        self.client.cmd(f"lseek {fd2} 0 0")
        out1 = self.client.cmd(f"read {fd1} 4")
        out2 = self.client.cmd(f"read {fd2} 5")
        self.assertEqual(get_value(out1, "read 4 bytes: "), "lo w")
        self.assertEqual(get_value(out2, "read 5 bytes: "), "hello")

    def test_08_close_reopen(self):
        fd1 = get_fd(self.client.cmd("open /test_persist.txt"))
        self.client.cmd(f"write {fd1} persistent")
        self.client.cmd(f"close {fd1}")
        fd2 = get_fd(self.client.cmd("open /test_persist.txt"))
        self.client.cmd(f"lseek {fd2} 0 0")
        out = self.client.cmd(f"read {fd2} 10")
        self.assertEqual(get_value(out, "read 10 bytes: "), "persistent")

    def test_09_lseek_negative(self):
        fd = get_fd(self.client.cmd("open /test_lseek_neg.txt"))
        out = self.client.cmd(f"lseek {fd} -100 0")
        self.assertTrue(any("failed" in l for l in out))

    def test_10_invalid_fd(self):
        out = self.client.cmd("read 9999 10")
        self.assertTrue(any("failed" in l for l in out))
        out = self.client.cmd("write 9999 hello")
        self.assertTrue(any("failed" in l for l in out))

    def test_11_read_past_eof(self):
        fd = get_fd(self.client.cmd("open /test_eof.txt"))
        self.client.cmd(f"write {fd} short")
        self.client.cmd(f"lseek {fd} 0 0")
        
        out = self.client.cmd(f"read {fd} 100")
        self.assertEqual(get_value(out, "read 5 bytes: "), "short")

    def test_12_write_past_eof_sparse(self):
        fd = get_fd(self.client.cmd("open /test_sparse.txt"))
        self.client.cmd(f"write {fd} A")
        self.client.cmd(f"lseek {fd} 4 0")
        self.client.cmd(f"write {fd} B")
        
        self.client.cmd(f"lseek {fd} 0 0")
        out = self.client.cmd(f"read {fd} 5")
        
        result = get_value(out, "read 5 bytes: ")
        self.assertEqual(result, "A\x00\x00\x00B")

    def test_13_double_close(self):
        fd = get_fd(self.client.cmd("open /test_double_close.txt"))
        self.client.cmd(f"close {fd}")
        out = self.client.cmd(f"close {fd}")
        
        self.assertTrue(any("failed" in l for l in out))

    def test_14_cache_update_on_partial_write(self):
        fd = get_fd(self.client.cmd("open /test_cache_update.txt"))
        
        # Write 20 bytes and read them back to force the block into the cache
        self.client.cmd(f"write {fd} AAAAAAAAAAAAAAAAAAAA")
        self.client.cmd(f"lseek {fd} 0 0")
        self.client.cmd(f"read {fd} 20") 
        
        # Seek to the middle and do a partial write
        self.client.cmd(f"lseek {fd} 5 0")
        self.client.cmd(f"write {fd} BBBBB")
        
        # Read back across the boundary. 
        # If cache_put logic is broken, this will return the old 'A's.
        self.client.cmd(f"lseek {fd} 0 0")
        out = self.client.cmd(f"read {fd} 20")
        self.assertEqual(get_value(out, "read 20 bytes: "), "AAAAABBBBBAAAAAAAAAA")

    def test_15_truncate_invalidates_cache(self):
        fd = get_fd(self.client.cmd("open /test_trunc_cache.txt"))
        
        # Write 20 bytes and read them to cache the block
        self.client.cmd(f"write {fd} " + "X" * 20)
        self.client.cmd(f"lseek {fd} 0 0")
        self.client.cmd(f"read {fd} 20") 
        
        # Truncate to 5 bytes
        self.client.cmd(f"truncate {fd} 5")
        
        # Attempt to read 20 bytes again
        self.client.cmd(f"lseek {fd} 0 0")
        out = self.client.cmd(f"read {fd} 20")
        
        # If the cache wasn't invalidated, it will serve 20 'X's.
        # If correct, it should only return the 5 remaining bytes.
        self.assertEqual(get_value(out, "read 5 bytes: "), "XXXXX")

    def test_16_cross_block_boundary(self):
        fd = get_fd(self.client.cmd("open /test_boundary.txt"))
        
        # Seek to 6 bytes before the end of the first block (4096 - 6 = 4090)
        self.client.cmd(f"lseek {fd} 4090 0")
        
        # Write 12 bytes. mynfs_write loops internally, so all 12 bytes are written.
        self.client.cmd(f"write {fd} HELLOWORLD12") 
        
        # Seek back to read across the boundary
        self.client.cmd(f"lseek {fd} 4090 0")
        
        # First read asks for 12, but hits the block boundary after 6 bytes.
        out1 = self.client.cmd(f"read {fd} 12") 
        self.assertEqual(get_value(out1, "read 6 bytes: "), "HELLOW")
        
        # The file pointer is now at 4096. Issue a second read for the remaining data.
        out2 = self.client.cmd(f"read {fd} 6") 
        self.assertEqual(get_value(out2, "read 6 bytes: "), "ORLD12")

    def test_17_cache_eviction_lru(self):
        fd = get_fd(self.client.cmd("open /test_evict.txt"))
        
        # Write data into 20 different blocks (forcing 4 LRU evictions)
        for i in range(20):
            # Block 0 is at 0, Block 1 is at 4096, Block 2 is at 8192, etc.
            self.client.cmd(f"lseek {fd} {i * 4096} 0")
            
            # Write exactly 6 bytes (e.g., DATA00, DATA01... DATA19)
            self.client.cmd(f"write {fd} DATA{i:02d}") 
            
        # Read them all back to verify data integrity
        for i in range(20):
            self.client.cmd(f"lseek {fd} {i * 4096} 0")
            out = self.client.cmd(f"read {fd} 6")
            self.assertEqual(get_value(out, "read 6 bytes: "), f"DATA{i:02d}")

    def test_18_multi_block_large_payload(self):
        fd = get_fd(self.client.cmd("open /test_large.txt"))
        
        # 10,000 bytes spans across Block 0, Block 1, and Block 2 (since block_size is 4096)
        large_data = "A" * 4000 + "B" * 4000 + "C" * 2000
        self.client.cmd(f"write {fd} {large_data}")
        
        self.client.cmd(f"lseek {fd} 0 0")

        read_data = ""
        while len(read_data) < 10000:
            out = self.client.cmd(f"read {fd} {10000 - len(read_data)}")
            # Extract just the string payload from "read X bytes: YYYY"
            chunk = get_value(out, f"read {get_value(out, 'read ').split(' ')[0]} bytes: ")
            if not chunk: 
                break
            read_data += chunk
            
        self.assertEqual(read_data, large_data)

    def test_19_mid_file_overwrite(self):
        fd = get_fd(self.client.cmd("open /test_overwrite.txt"))
        
        # Write 20 bytes
        self.client.cmd(f"write {fd} AAAAAAAAAAAAAAAAAAAA")
        
        # Seek to middle and overwrite 5 bytes
        self.client.cmd(f"lseek {fd} 5 0")
        self.client.cmd(f"write {fd} BBBBB")
        
        # Seek to end to check file size (Should still be 20, not 10!)
        out = self.client.cmd(f"lseek {fd} 0 2")
        self.assertEqual(get_value(out, "position = "), "20")
        
        # Read the whole thing to verify contents
        self.client.cmd(f"lseek {fd} 0 0")
        out = self.client.cmd(f"read {fd} 20")
        self.assertEqual(get_value(out, "read 20 bytes: "), "AAAAABBBBBAAAAAAAAAA")


if __name__ == "__main__":
    unittest.main(verbosity=2)