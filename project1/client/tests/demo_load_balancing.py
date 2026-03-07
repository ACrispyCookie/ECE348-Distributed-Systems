import sys
import os
import demo_lib
import random

# Requires 2 servers running - update these lines in config.txt accordingly
SERVERS_CONFIG = """S 10.0.0.8 55188 0 1
S 10.0.0.8 25565 0 1
"""

demo_lib.init(default_config_line=SERVERS_CONFIG.strip())
import client_api

servers = client_api.servers
if len(servers) < 2:
    print("Need at least 2 servers for load balancing demo. Exiting.")
    demo_lib.cleanUp()
    sys.exit(1)

print(f"\n[2] Discovered {len(servers)} servers:")
for i, s in enumerate(servers):
    print(f"    Server {i}: {s.address[0]}:{s.address[1]}")

print(f"\n[3] Starting index: Server {client_api.current_server_index}")
print(f"    (Randomized offset ensures clients don't all start on the same server)\n")

NUM_REQUESTS = 20
routing_log = []
numbers = [random.randint(2, 10000) for _ in range(NUM_REQUESTS)]

print(f"[4] Sending {NUM_REQUESTS} requests and tracking which server handles each...\n")

for i, num in enumerate(numbers):
    target_server = servers[client_api.current_server_index]
    target_label = f"{target_server.address[0]}:{target_server.address[1]}"

    payload = __import__('struct').pack("!IQ", 1, num)
    error, response = client_api.doRequestReply(0, payload)

    status = "OK" if not error else f"ERR({error})"
    routing_log.append((f"Req {i+1} ({num})", target_label, status))
    print(f"  Req {i+1:>2} | num={num:<6} | -> Server {target_label} | {status}")

print()
demo_lib.print_load_balancing_info(
    [(r[0], r[1]) for r in routing_log],
    strategy="Round Robin (randomized start)"
)

# Print distribution summary
print("\n[5] Distribution summary:")
counts = {}
for _, server_label, _ in routing_log:
    counts[server_label] = counts.get(server_label, 0) + 1
for server_label, count in counts.items():
    bar = "█" * count
    print(f"    {server_label} : {bar} ({count} requests)")

demo_lib.cleanUp()