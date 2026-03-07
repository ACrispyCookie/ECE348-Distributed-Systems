import sys
import os
import struct

CONFIG_FILENAME = "config.txt"
with open(CONFIG_FILENAME, "w") as f:
    pass

sys.path.insert(0, '../src')
import client_api

def print_packet_info(req):
    """Reconstructs the packet exactly as client_api does to display its contents."""
    server = req.server
    payload_len = len(req.payload) if req.payload is not None else 0
    
    header = struct.pack("!IIIII", 
                         req.message_type.value, 
                         server.client_id, 
                         req.service_id, 
                         server.sequence_number,
                         payload_len)
                         
    packet = header + req.payload if req.payload is not None else header
    
    print("  [Packet Inspection]")
    print(f"  Type       : {req.message_type.name} ({req.message_type.value})")
    print(f"  Client ID  : {server.client_id}")
    print(f"  Service ID : {req.service_id}")
    print(f"  Seq Number : {server.sequence_number}")
    print(f"  Length     : {payload_len}")
    print(f"  Raw Bytes  : {packet.hex(' ')}")
    print("-" * 40)


print("Initializing client API and discovering server...")
if client_api.init(CONFIG_FILENAME) is None:
    print("Failed to initialize API. Is the server running?")
    sys.exit(1)

server = client_api.servers[0]
payload = b'\x00\x00\x00\x01\x00\x00\x00\x00\x00\x00\x00\x07'

req = client_api.ClientRequest(server, client_api.MessageType.REQUEST, 0, payload)

print(f"\n--- Sending Normal Request (Seq = {server.sequence_number}) ---")
print_packet_info(req)
client_api.tryRequest(req, check_id=True, send_ack=True, track_cancel=False)

original_seq = server.sequence_number
server.sequence_number = 0
req_older = client_api.ClientRequest(server, client_api.MessageType.REQUEST, 0, payload)

print(f"\n--- Sending Older Duplicate Request (Seq = {server.sequence_number}) ---")
print_packet_info(req_older)

old_retries = client_api.MAX_RETRIES
client_api.MAX_RETRIES = 1 

error, response = client_api.tryRequest(req_older, check_id=True, send_ack=False, track_cancel=False)

client_api.MAX_RETRIES = old_retries

if error:
    print(f"\nClient Result: Request Failed with '{error}' (This is expected!)")
else:
    print("\nClient Result: Received a response (Unexpected!)")

if os.path.exists(CONFIG_FILENAME):
    os.remove(CONFIG_FILENAME)