import sys
import os
import multiprocessing
from collections import Counter

NUM_CLIENTS = 50
REQUESTS_PER_CLIENT = 2

KNOWN_SERVERS = [
    ("10.64.16.53", 10101),
    ("10.64.16.53", 10102),
    ("10.64.16.53", 10103),
    ("10.64.16.53", 10104)
]

def run_client(client_id):
    sys.path.insert(0, '../src')
    import client_api
    
    client_api.servers = []
    
    config_file = f"config_client_{client_id}.txt"
    
    with open(config_file, "w") as f:
        for ip, port in KNOWN_SERVERS:
            f.write(f"S {ip} {port} 0 0\n")

    client_api.init(config_file)
    
    distribution = Counter()
    payload = b'\x00\x00\x00\x01\x00\x00\x00\x00\x00\x00\x00\x07'
    
    for _ in range(REQUESTS_PER_CLIENT):
        error, response_payload = client_api.doRequestReply(0, payload)
        
        if not error:
            used_index = (client_api.current_server_index - 1) % len(client_api.servers)
            server = client_api.servers[used_index]
            
            distribution[f"{server.address[0]}:{server.address[1]}"] += 1
        else:
            distribution["FAILED"] += 1
            
    if os.path.exists(config_file):
        os.remove(config_file)
        
    return client_id, dict(distribution)

if __name__ == '__main__':
    print(f"Starting Multi-Client Load Balancing Test...")
    print(f"Spawning {NUM_CLIENTS} distinct clients, each sending {REQUESTS_PER_CLIENT} requests.")
    print("-" * 60)
    
    with multiprocessing.Pool(processes=NUM_CLIENTS) as pool:
        results = pool.map(run_client, range(1, NUM_CLIENTS + 1))
        
    global_distribution = Counter()

    print("\n[ Distribution per Client ]")
    for client_id, dist in results:
        print(f" Client {client_id:2}: ", end="")
        for server_addr, count in dist.items():
            print(f"[{server_addr}] served {count:2} | ", end="")
            global_distribution[server_addr] += count
        print()
        
    print("\n" + "=" * 60)
    print("[ FINAL SERVER DISTRIBUTION ]")
    print("=" * 60)
    
    total_requests = NUM_CLIENTS * REQUESTS_PER_CLIENT
    
    for server_addr, count in global_distribution.items():
        percentage = (count / total_requests) * 100
        bar = "█" * int(percentage / 2)
        print(f" Server {server_addr:<15} : {count:3} requests ({percentage:5.1f}%) {bar}")
        
    print("=" * 60)