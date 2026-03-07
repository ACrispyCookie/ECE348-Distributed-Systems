import sys
import os
import demo_lib

demo_lib.init()
import client_api

print("Initializing client API and discovering server...")
if client_api.init(demo_lib.CONFIG_FILENAME) is None:
    print("Failed to initialize API. Is the server running?")
    sys.exit(1)

server = client_api.servers[0]
payload = b'\x00\x00\x00\x01\x00\x00\x00\x00\x00\x00\x00\x07'

req = client_api.ClientRequest(server, client_api.MessageType.REQUEST, 0, payload)

print(f"\n--- Sending Normal Request (Seq = {server.sequence_number}) ---")
demo_lib.print_packet_info(req)
client_api.tryRequest(req, check_id=True, send_ack=True, track_cancel=False)

original_seq = server.sequence_number
server.sequence_number = 0
req_older = client_api.ClientRequest(server, client_api.MessageType.REQUEST, 0, payload)

print(f"\n--- Sending Older Duplicate Request (Seq = {server.sequence_number}) ---")
demo_lib.print_packet_info(req_older)

old_retries = client_api.MAX_RETRIES
client_api.MAX_RETRIES = 1 

error, response = client_api.tryRequest(req_older, check_id=True, send_ack=False, track_cancel=False)

client_api.MAX_RETRIES = old_retries

if error:
    print(f"\nClient Result: Request Failed with '{error}' (This is expected!)")
else:
    print("\nClient Result: Received a response (Unexpected!)")

demo_lib.cleanUp()