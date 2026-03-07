import struct
import sys
import os

CONFIG_FILENAME = None

def init():
    global CONFIG_FILENAME

    CONFIG_FILENAME = "config.txt"
    with open(CONFIG_FILENAME, "w") as f:
        pass

    sys.path.insert(0, '../src')

def cleanUp():
    global CONFIG_FILENAME
    
    if os.path.exists(CONFIG_FILENAME):
        os.remove(CONFIG_FILENAME)

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
    print(f"  Raw Bytes  : {' ' if req.payload == None else req.payload.hex(' ')}")
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
    print(f"  Raw Bytes  : {rsp if rsp.payload == None else rsp.payload.hex(' ')}")
    print("-" * 40)