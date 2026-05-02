import unittest
import subprocess
import time

SERVER = "127.0.0.1"
PORT = "10100"
# Using a 5-second freshT for our expiration tests
CLIENT_ARGS = ["./build/client_app", SERVER, PORT, "4096", "16", "5"]

class ClientApp:
    def __init__(self, name="Client"):
        self.name = name
        self.proc = subprocess.Popen(
            CLIENT_ARGS,
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


class TestMultiClientNFS(unittest.TestCase):

    def setUp(self):
        # Spawn TWO independent clients for every test
        self.c1 = ClientApp("Client1")
        self.c2 = ClientApp("Client2")

    def tearDown(self):
        self.c1.close()
        self.c2.close()

    def test_01_independent_client_state(self):
        """Test that two clients opening the same file have independent file pointers."""
        fd1 = get_fd(self.c1.cmd("open /test_multi_pos.txt"))
        fd2 = get_fd(self.c2.cmd("open /test_multi_pos.txt"))
        
        self.c1.cmd(f"write {fd1} HELLOWORLD")
        
        # C1 seeks to 0, C2 seeks to 5
        self.c1.cmd(f"lseek {fd1} 0 0")
        self.c2.cmd(f"lseek {fd2} 5 0")
        
        out1 = self.c1.cmd(f"read {fd1} 5")
        out2 = self.c2.cmd(f"read {fd2} 5")
        
        self.assertEqual(get_value(out1, "read 5 bytes: "), "HELLO")
        self.assertEqual(get_value(out2, "read 5 bytes: "), "WORLD")

    def test_02_write_through_visibility(self):
        """Test that a write by C1 is immediately visible to C2 (if C2 hasn't cached it yet)."""
        fd1 = get_fd(self.c1.cmd("open /test_multi_wt.txt"))
        fd2 = get_fd(self.c2.cmd("open /test_multi_wt.txt"))
        
        self.c1.cmd(f"write {fd1} SHARED_DATA")
        
        # C2 reads immediately. Because C2's cache is empty, it MUST hit the server.
        self.c2.cmd(f"lseek {fd2} 0 0")
        out2 = self.c2.cmd(f"read {fd2} 11")
        
        self.assertEqual(get_value(out2, "read 11 bytes: "), "SHARED_DATA")

    def test_03_stale_cache_within_fresht(self):
        """Test that C1 reads stale data if C2 overwrites it before freshT expires."""
        fd1 = get_fd(self.c1.cmd("open /test_multi_stale.txt"))
        fd2 = get_fd(self.c2.cmd("open /test_multi_stale.txt"))
        
        # C1 writes and reads to populate its local cache
        self.c1.cmd(f"write {fd1} VERSION_1_")
        self.c1.cmd(f"lseek {fd1} 0 0")
        self.c1.cmd(f"read {fd1} 10") 
        
        # C2 aggressively overwrites the data on the server
        self.c2.cmd(f"lseek {fd2} 0 0")
        self.c2.cmd(f"write {fd2} VERSION_2_")
        
        # C1 reads again IMMEDIATELY. 
        # freshT (5s) has NOT expired. C1 should serve the stale cached data!
        self.c1.cmd(f"lseek {fd1} 0 0")
        out1 = self.c1.cmd(f"read {fd1} 10")
        
        self.assertEqual(get_value(out1, "read 10 bytes: "), "VERSION_1_")

    def test_04_cache_refresh_after_fresht(self):
        """Test that C1 fetches fresh data if C2 overwrites and freshT expires."""
        fd1 = get_fd(self.c1.cmd("open /test_multi_expire.txt"))
        fd2 = get_fd(self.c2.cmd("open /test_multi_expire.txt"))
        
        # C1 writes and reads to populate its local cache
        self.c1.cmd(f"write {fd1} OLD_DATA")
        self.c1.cmd(f"lseek {fd1} 0 0")
        self.c1.cmd(f"read {fd1} 8") 
        
        # C2 overwrites the data on the server
        self.c2.cmd(f"lseek {fd2} 0 0")
        self.c2.cmd(f"write {fd2} NEW_DATA")
        
        # We wait for the cache to expire (> 5 seconds)
        print("\nWaiting 6 seconds for freshT to expire...")
        time.sleep(6)
        
        # C1 reads again.
        self.c1.cmd(f"lseek {fd1} 0 0")
        out1 = self.c1.cmd(f"read {fd1} 8")
        
        self.assertEqual(get_value(out1, "read 8 bytes: "), "NEW_DATA")

    def test_05_concurrent_truncate_visibility(self):
        """Test that if C1 caches data, and C2 truncates it, C1 sees the truncate after freshT."""
        fd1 = get_fd(self.c1.cmd("open /test_multi_trunc.txt"))
        fd2 = get_fd(self.c2.cmd("open /test_multi_trunc.txt"))
        
        self.c1.cmd(f"write {fd1} THIS_WILL_BE_DELETED")
        self.c1.cmd(f"lseek {fd1} 0 0")
        self.c1.cmd(f"read {fd1} 20") # C1 caches it
        
        # C2 deletes the file contents on the server
        self.c2.cmd(f"truncate {fd2} 0")
        
        time.sleep(6) # Let C1's cache expire
        
        self.c1.cmd(f"lseek {fd1} 0 0")
        out1 = self.c1.cmd(f"read {fd1} 20")
        
        read_val = get_value(out1, "read ")
        if read_val:
            self.assertNotIn("DELETED", get_value(out1, f"read {read_val.split(' ')[0]} bytes: ") or "")


if __name__ == "__main__":
    unittest.main(verbosity=2)