import struct
import sys
import os

CONFIG_FILENAME = None
DEFAULT_LINE = "S 10.64.16.94 38574 0 0"
DEMO_DEBUG = False

import sys

def init(default_config_line=DEFAULT_LINE):
    global CONFIG_FILENAME

    CONFIG_FILENAME = "config.txt"
    with open(CONFIG_FILENAME, "w") as f:
        if default_config_line:
            f.write(default_config_line + "\n")
            
    if '../src' not in sys.path:
        sys.path.insert(0, '../src')

    import client_api
    client_api.DEBUG = DEMO_DEBUG

    print("="*50 + "\n")
    print("[1] Initializing client API to discover or use existing servers...")
    if client_api.init(CONFIG_FILENAME) is None:
        print("Failed to initialize API. Is the server running?")
        sys.exit(1)

def cleanUp():
    global CONFIG_FILENAME
    
    if os.path.exists(CONFIG_FILENAME):
        os.remove(CONFIG_FILENAME)
    print("="*50 + "\n")

def print_packet_info(req):
    """Reconstructs the packet exactly as client_api does to display its contents."""
    server = req.server
    payload_len = len(req.payload) if req.payload is not None else 0

    print("-" * 40)
    print("  [Packet Inspection]")
    print(f"  Type       : {req.message_type}")
    print(f"  Client ID  : {server.client_id}")
    print(f"  Service ID : {req.service_id}")
    print(f"  Seq Number : {server.sequence_number}")
    print(f"  Length     : {payload_len}")
    print(f"  Raw Bytes  : {' ' if req.payload == None else req.payload.hex()}")
    print("-" * 40)

def print_response_info(rsp, server):
    """Reconstructs the packet exactly as client_api does to display its contents."""
    payload_len = len(rsp.payload) if rsp.payload is not None else 0
    
    print("-" * 40)
    print("  [Packet Inspection]")
    print(f"  Type       : {rsp.message_type}")
    print(f"  Client ID  : {rsp.client_id}")
    print(f"  Service ID : {rsp.service_id}")
    print(f"  Seq Number : {server.sequence_number}")
    print(f"  Length     : {payload_len}")
    print(f"  Raw Bytes  : {rsp if rsp.payload == None else rsp.payload.hex()}")
    print("-" * 40)

def print_discovery_info(found_servers):
    print("-" * 50)
    print("  [Service Discovery]")
    print("  Client  ---(Broadcast/Multicast)---> [Network]")
    
    if not found_servers:
        print("          <--- (No response)")
    else:
        for i, server in enumerate(found_servers):
            # Assuming 'server' has an address attribute like ('10.0.0.8', 25565)
            print(f"          <--- [Server {i+1}] {server.address[0]}:{server.address[1]}")
            
    print(f"  Total Servers Found: {len(found_servers)}")
    print("-" * 50)

def print_load_balancing_info(request_routing, strategy="Round Robin"):
    """
    Prints how requests were distributed.
    'request_routing' should be a list of tuples: [("Req 1", "10.0.0.8"), ("Req 2", "10.0.0.9")]
    """
    print("-" * 50)
    print(f"  [Load Balancing Distribution] - Strategy: {strategy}")
    
    for req_name, target_ip in request_routing:
        print(f"  {req_name:<10}  ==========>  [ Server {target_ip} ]")
        
    print("-" * 50)