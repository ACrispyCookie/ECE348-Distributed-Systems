import subprocess
import sys
import time
import random

def run_stress_test():
    print("Launching client_app.py in subprocess...")
    process = subprocess.Popen(
        [sys.executable, "-u", "../src/client_app.py"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=0
    )

    CONFIG_FILENAME = "config.txt"
    open(CONFIG_FILENAME, "a").close()

    passed = 0
    failed = 0
    errors = 0
    request_num = 0

    try:
        print("\nStarting Stress Test (Ctrl+C to stop)...")
        print("="*60)

        while True:
            request_num += 1

            # Random batch of 1-5 numbers up to 64-bit max
            count = random.randint(1, 5)
            numbers = [random.randint(0, 2**62) for _ in range(count)]
            expected = [1 if is_prime(n) else 0 for n in numbers]

            input_str = ", ".join(map(str, numbers))

            try:
                process.stdin.write(input_str + "\n")
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
                    print(f"  Request #{request_num}: PASS | input={numbers}")
                else:
                    failed += 1
                    print(f"  Request #{request_num}: FAIL | input={numbers}")
                    print(f"    Expected: {expected}")
                    print(f"    Got:      {actual}")

            # Random delay between 0 and 2 seconds
            time.sleep(random.uniform(0, 2))

    except KeyboardInterrupt:
        print(f"\n\nStress test interrupted.")
    except Exception as e:
        print(f"\nFATAL ERROR: {e}")
    finally:
        print(f"\nResults: {passed} passed, {failed} failed, {errors} errors out of {request_num} requests.")
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
    run_stress_test()