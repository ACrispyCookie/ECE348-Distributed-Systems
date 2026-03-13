import sys
import time
import struct
import multiprocessing
import os
import csv
import statistics
from datetime import datetime
from collections import Counter
import demo_lib

# Default configurations. You can modify these to match your environment.
SINGLE_SERVER_CONFIG = "S 10.0.0.8 8080 0 0\n"
MULTI_SERVER_CONFIG = """S 10.0.0.8 8080 0 0
S 10.0.0.8 8081 0 0
S 10.0.0.3 8080 0 0
"""

NULL_PAYLOAD = struct.pack("!I", 0)  # Payload indicating 0 numbers to test (null request)

def get_payload(use_big_primes=False):
    """Generates a payload of numbers to test."""
    if use_big_primes:
        # 64-bit primes that will make the server's O(sqrt(N)) primality test work hard
        primes = [999999999989, 999999999989, 999999999989]*2
        count = len(primes)
        fmt = f"!I{count}Q"
        return struct.pack(fmt, count, *primes)
    else:
        # Small primes for standard throughput testing without CPU bottlenecking
        primes = [9973]
        count = len(primes)
        fmt = f"!I{count}Q"
        return struct.pack(fmt, count, *primes)

def measure_null_delay(iterations=1000, server_type="single"):
    import client_api
    print(f"\n--- Measuring Null Request Delay ({iterations} iterations) ---")
    
    raw_delays = []
    for i in range(iterations):
        start_time = time.perf_counter()
        error, response = client_api.doRequestReply(0, NULL_PAYLOAD)
        end_time = time.perf_counter()
        
        if error:
            print(f"Request {i+1} failed with error: {error}")
            continue
            
        # Store delay_in_ms
        raw_delays.append((end_time - start_time) * 1000)
        
    if raw_delays:
        # Discard Min and Max Outliers for the summary metrics
        if len(raw_delays) > 2:
            delay_values = sorted(raw_delays)[1:-1]
            print("  * Discarded min and max delay outliers.")
        else:
            delay_values = raw_delays

        avg_delay = sum(delay_values) / len(delay_values)
        std_dev = statistics.stdev(delay_values) if len(delay_values) > 1 else 0.0
        min_delay = min(delay_values)
        max_delay = max(delay_values)
        
        print(f"Average Delay: {avg_delay:.3f} ms")
        print(f"Std Deviation: {std_dev:.3f} ms")
        print(f"Min Delay:     {min_delay:.3f} ms")
        print(f"Max Delay:     {max_delay:.3f} ms")
        
        # Save final summary to CSV
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"null_delay_{server_type}_{timestamp}.csv"
        
        with open(filename, mode='w', newline='') as file:
            writer = csv.writer(file)
            # Write Header
            writer.writerow([
                "Server Type", "Iterations Attempted", "Successful Requests", 
                "Average Delay (ms)", "Standard Dev (ms)", "Min Delay (ms)", "Max Delay (ms)"
            ])
            # Write Values
            writer.writerow([
                server_type, iterations, len(raw_delays), 
                f"{avg_delay:.3f}", f"{std_dev:.3f}", f"{min_delay:.3f}", f"{max_delay:.3f}"
            ])
                
        print(f"  -> Final results saved to {filename}")
    print()

def simulate_client_load(client_id, requests_per_client, config_text, use_big_primes):
    """Worker function for performance/throughput testing"""
    sys.path.insert(0, '../src')
    import client_api
    
    # Clear inherited state from the parent process
    client_api.servers = []  
    
    config_file = f"config_perf_{client_id}.txt"
    with open(config_file, "w") as f:
        f.write(config_text)
        
    client_api.init(config_file)
    payload = get_payload(use_big_primes)
    
    successes = 0
    individual_delays = []
    
    start_time_total = time.perf_counter()
    for i in range(requests_per_client):
        t0 = time.perf_counter()
        error, _ = client_api.doRequestReply(0, payload)
        t1 = time.perf_counter()
        
        if not error:
            successes += 1
            individual_delays.append((t1 - t0) * 1000)
            
    end_time_total = time.perf_counter()
    
    if os.path.exists(config_file):
        os.remove(config_file)
        
    return client_id, successes, end_time_total - start_time_total, individual_delays

def run_performance_test(num_clients, requests_per_client, config_text, use_big_primes=False, server_type="single"):
    payload_type = "Big Primes" if use_big_primes else "Small Primes"
    print(f"\n--- Measuring Performance ({num_clients} Clients, {requests_per_client} Req/Client, Payload: {payload_type}) ---")
    
    with multiprocessing.Pool(processes=num_clients) as pool:
        args = [(i, requests_per_client, config_text, use_big_primes) for i in range(num_clients)]
        results = pool.starmap(simulate_client_load, args)
        
    if results:
        # Results format: list of (client_id, successes, total_time, list_of_delays)
        
        # Calculate summary metrics (discarding fastest and slowest client times if > 2 clients)
        client_times = sorted([res[2] for res in results])
        if len(client_times) > 2:
            client_times = client_times[1:-1]
            print("  * Discarded fastest and slowest client outliers for total time.")

        total_successes = sum(res[1] for res in results)
        max_time = max(client_times) if client_times else max(res[2] for res in results)
        throughput = total_successes / max_time if max_time > 0 else 0
        
        # Calculate delay statistics across ALL individual requests processed
        all_delays = [delay for res in results for delay in res[3]]
        avg_req_delay = sum(all_delays) / len(all_delays) if all_delays else 0.0
        std_dev = statistics.stdev(all_delays) if len(all_delays) > 1 else 0.0
        min_req_delay = min(all_delays) if all_delays else 0.0
        max_req_delay = max(all_delays) if all_delays else 0.0
        
        print(f"Total Requests Processed: {total_successes}")
        print(f"Total Time Elapsed:       {max_time:.2f} seconds")
        print(f"Throughput:               {throughput:.2f} req/sec")
        print(f"Average Request Delay:    {avg_req_delay:.3f} ms (StdDev: {std_dev:.3f} ms)\n")
        
        # Save final summary to CSV
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"throughput_{server_type}_{timestamp}.csv"
        
        with open(filename, mode='w', newline='') as file:
            writer = csv.writer(file)
            # Write Header
            writer.writerow([
                "Server Type", "Clients", "Requests per Client", "Payload Type", 
                "Total Requests Processed", "Total Time Elapsed (s)", "Throughput (req/sec)", 
                "Average Req Delay (ms)", "Standard Dev (ms)", "Min Req Delay (ms)", "Max Req Delay (ms)"
            ])
            # Write Values
            writer.writerow([
                server_type, num_clients, requests_per_client, payload_type,
                total_successes, f"{max_time:.3f}", f"{throughput:.2f}",
                f"{avg_req_delay:.3f}", f"{std_dev:.3f}", f"{min_req_delay:.3f}", f"{max_req_delay:.3f}"
            ])
                    
        print(f"  -> Final results saved to {filename}")
    else:
        print("No successful requests processed.\n")

def test_load_distribution(requests=100):
    import client_api
    print(f"\n--- Confirming Load Distribution ({requests} requests) ---")
    
    routing_counts = Counter()
    for i in range(requests):
        error, _ = client_api.doRequestReply(0, NULL_PAYLOAD)
        if not error:
            # client_api increments the index AFTER choosing a server, so we check the previous one
            used_index = (client_api.current_server_index - 1) % len(client_api.servers)
            server = client_api.servers[used_index]
            server_label = f"{server.address[0]}:{server.address[1]}"
            routing_counts[server_label] += 1
            
    # Save to CSV
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"load_distribution_{timestamp}.csv"
    
    with open(filename, mode='w', newline='') as file:
        writer = csv.writer(file)
        writer.writerow(["Server", "Requests Handled", "Percentage (%)"])
        
        for server_label, count in routing_counts.items():
            percentage = (count / requests) * 100
            bar = "█" * int(percentage / 2)
            print(f"Server {server_label:<15} : {count:3} requests ({percentage:5.1f}%) {bar}")
            
            writer.writerow([server_label, count, f"{percentage:.1f}"])
            
    print(f"\n  -> Results saved to {filename}\n")

def main_menu():
    while True:
        print("======================================================")
        print(" Distributed Systems - Experiment Runner Menu")
        print("======================================================")
        print("1. Null Interaction Delay (Single Server)")
        print("2. Null Interaction Delay (Multi-Server)")
        print("3. Throughput / Performance Test (Single Server)")
        print("4. Throughput / Performance Test (Multi-Server)")
        print("5. Load Distribution Verification (Multi-Server)")
        print("0. Exit")
        print("======================================================")
        
        choice = input("Enter your choice: ").strip()
        
        if choice == '0':
            print("Exiting...")
            break
            
        elif choice == '1':
            demo_lib.init(SINGLE_SERVER_CONFIG)
            try:
                iters = int(input("Enter number of iterations (default 1000): ") or 1000)
                measure_null_delay(iterations=iters, server_type="single")
            except ValueError:
                print("Invalid input.")
            demo_lib.cleanUp()
            
        elif choice == '2':
            demo_lib.init(MULTI_SERVER_CONFIG)
            try:
                iters = int(input("Enter number of iterations (default 1000): ") or 1000)
                measure_null_delay(iterations=iters, server_type="multi")
            except ValueError:
                print("Invalid input.")
            demo_lib.cleanUp()
            
        elif choice in ('3', '4'):
            server_type = "single" if choice == '3' else "multi"
            config = SINGLE_SERVER_CONFIG if choice == '3' else MULTI_SERVER_CONFIG
            
            try:
                clients = int(input("Enter number of concurrent clients: "))
                reqs = int(input("Enter requests per client: "))
                big_primes = input("Use large prime numbers? (y/n): ").strip().lower() == 'y'
                
                run_performance_test(num_clients=clients, 
                                     requests_per_client=reqs, 
                                     config_text=config, 
                                     use_big_primes=big_primes,
                                     server_type=server_type)
            except ValueError:
                print("Invalid integer input. Aborting test.")
                
        elif choice == '5':
            demo_lib.init(MULTI_SERVER_CONFIG)
            try:
                reqs = int(input("Enter total number of requests (default 300): ") or 300)
                test_load_distribution(requests=reqs)
            except ValueError:
                print("Invalid input.")
            demo_lib.cleanUp()
            
        else:
            print("Invalid choice. Please select a valid option.")
            
        input("Press Enter to continue...")

if __name__ == "__main__":
    main_menu()
