import subprocess
import sys
import random
import time
import os

SERVERS_CONFIG = """S 10.64.16.94 38574 0 1
S 10.64.16.94 42824 0 1
S 193.70.36.200 51946 0 1
"""
CONFIG_FILENAME = "config.txt"

def run_spam_test():
    global CONFIG_FILENAME, SERVERS_CONFIG

    print("Launching client_app.py in subprocess...")
    process = subprocess.Popen(
        [sys.executable, "-u", "../src/client_app.py"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        universal_newlines=True,
        bufsize=0
    )

    if not os.path.isfile(CONFIG_FILENAME):
        with open(CONFIG_FILENAME, "w") as f:
            f.write(SERVERS_CONFIG + "\n")
    
    passed = 0
    failed = 0
    errors = 0
    request_num = 0
    start_time = time.time()

    try:
        print("\nStarting Spam Test (Ctrl+C to stop)...")
        print("="*60)

        while True:
            request_num += 1

            # Small numbers only so server responds fast and we stress the protocol, not the primality check
            count = random.randint(1, 5)
            numbers = [random.randint(0, 10000) for _ in range(count)]
            expected = [1 if is_prime(n) else 0 for n in numbers]

            try:
                process.stdin.write(", ".join(map(str, numbers)) + "\n")
                process.stdin.flush()
            except BrokenPipeError:
                print("Client process crashed.")
                break

            actual = []
            while len(actual) < count:
                line = process.stdout.readline()

                if not line and process.poll() is not None:
                    raise RuntimeError("Client process terminated early")

                clean_line = line.strip()

                if "[APP] Failed" in clean_line or "Network Request Failed" in clean_line:
                    print(f"  Request #{request_num} ERROR: {clean_line}")
                    errors += 1
                    break

                if ":" in clean_line:
                    parts = clean_line.split(":")
                    if len(parts) == 2:
                        status_str = parts[1].strip()
                        if status_str == "Prime":
                            actual.append(1)
                        elif status_str == "Not Prime":
                            actual.append(0)

            if len(actual) == count:
                if actual == expected:
                    passed += 1
                else:
                    failed += 1
                    print(f"  Request #{request_num}: FAIL | input={numbers}")
                    print(f"    Expected: {expected}")
                    print(f"    Got:      {actual}")

            # Print throughput every 100 requests
            if request_num % 100 == 0:
                elapsed = time.time() - start_time
                rps = request_num / elapsed
                print(f"  [{request_num} requests] {passed} passed, {failed} failed, {errors} errors | {rps:.1f} req/s")

    except KeyboardInterrupt:
        print(f"\n\nSpam test interrupted.")
    except Exception as e:
        print(f"\nFATAL ERROR: {e}")
    finally:
        elapsed = time.time() - start_time
        rps = request_num / elapsed if elapsed > 0 else 0
        print(f"\nResults: {passed} passed, {failed} failed, {errors} errors out of {request_num} requests in {elapsed:.1f}s ({rps:.1f} req/s).")
        if process.poll() is None:
            process.stdin.write("q\n")
            process.stdin.flush()
            time.sleep(0.5)
            process.terminate()


def is_prime(n):
    if n <= 1:
        return False
    if n <= 3:
        return True
    if n % 2 == 0 or n % 3 == 0:
        return False
    i = 5
    while i * i <= n:
        if n % i == 0 or n % (i + 2) == 0:
            return False
        i += 6
    return True


if __name__ == "__main__":
    run_spam_test()