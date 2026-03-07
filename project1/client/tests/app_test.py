import subprocess
import sys
import time

# --- Define the Test Suite ---
TEST_CASES = [
    {
        "name": "Edge Cases (0 and 1)",
        "input": [0, 1, 2],
        "expected": [0, 0, 1]  
    },
    {
        "name": "Small Basic Primes",
        "input": [3, 5, 7, 11, 13],
        "expected": [1, 1, 1, 1, 1]
    },
    {
        "name": "Small Composites",
        "input": [4, 6, 8, 9, 10, 15],
        "expected": [0, 0, 0, 0, 0, 0]
    },
    {
        "name": "Large 32-bit Primes",
        "input": [2147483647, 4294967291],
        "expected": [1, 1]
    },
    {
        "name": "Tricky 64-bit Composites",
        "input": [4294967297, 999999999999999999],
        "expected": [0, 0]
    },
    {
        "name": "Mid-Range 64-bit Prime (Triggers Timeout/Retry)",
        "input": [10089886811898868001], 
        "expected": [1]
    }
]

def run_integration_tests():
    print("Launching client_app.py in subprocess...")
    
    # Start the client application with unbuffered output (-u)
    process = subprocess.Popen(
        [sys.executable, "-u", "client_app.py"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=0
    )

    passed_tests = 0
    total_tests = len(TEST_CASES)
    
    try:
        print("\nStarting Automated Integration Test Suite...")
        print("="*60)

        for i, test in enumerate(TEST_CASES, 1):
            print(f"Test {i}/{total_tests}: {test['name']}")
            print(f"  Input:    {test['input']}")
            
            input_str = ", ".join(map(str, test['input']))
            
            try:
                process.stdin.write(input_str + "\n")
                process.stdin.flush()
            except BrokenPipeError:
                print("Error: Client process crashed or closed unexpectedly.")
                break

            actual_results = []
            expected_count = len(test['input'])
            
            while len(actual_results) < expected_count:
                line = process.stdout.readline()
                
                if not line and process.poll() is not None:
                    raise RuntimeError("Client process terminated early")

                clean_line = line.strip()
                
                if "[APP] Failed" in clean_line or "Network Request Failed" in clean_line:
                    print(f"  Client Error: {clean_line}")
                    break

                if ":" in clean_line:
                    parts = clean_line.split(":")
                    if len(parts) == 2:
                        status_str = parts[1].strip()
                        if status_str == "Prime":
                            actual_results.append(1)
                        elif status_str == "Not Prime":
                            actual_results.append(0)

            if actual_results == test['expected']:
                print("  Result:   [PASS]")
                passed_tests += 1
            else:
                print("  Result:   [FAIL]")
                print(f"    Expected: {test['expected']}")
                print(f"    Got:      {actual_results}")
            
            print("-" * 60)

        print(f"\nTest Summary: {passed_tests}/{total_tests} passed.")
        
    except Exception as e:
        print(f"\nFATAL TEST ERROR: {e}")
    
    finally:
        if process.poll() is None:
            print("Closing client application...")
            process.stdin.write("q\n")
            process.stdin.flush()
            time.sleep(0.5)
            process.terminate()

if __name__ == "__main__":
    run_integration_tests()