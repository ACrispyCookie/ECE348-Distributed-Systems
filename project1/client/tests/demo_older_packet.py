import sys
import os
import demo_lib

demo_lib.init()
import client_api

server = client_api.servers[0]
payload = b'\x00\x00\x00\x01\x00\x00\x00\x00\x00\x00\x00\x07'

req = client_api.ClientRequest(server, client_api.MessageType.REQUEST, 0, payload)

print(f"\n[2] Sending normal request (Seq = {server.sequence_number}) ---")
demo_lib.print_packet_info(req)
client_api.tryRequest(req, check_id=True, send_ack=True, track_cancel=False)

original_seq = server.sequence_number
server.sequence_number = 0
req_older = client_api.ClientRequest(server, client_api.MessageType.REQUEST, 0, payload)

print(f"\n[3] Sending older duplicate request (Seq = {server.sequence_number}) ---")
input("\n[PAUSE] Press Enter...")
demo_lib.print_packet_info(req_older)

old_retries = client_api.MAX_RETRIES
client_api.MAX_RETRIES = 1 

error, response = client_api.tryRequest(req_older, check_id=True, send_ack=False, track_cancel=False)

client_api.MAX_RETRIES = old_retries

if error:
    print(f"\n[4] Client Result: Request failed with '{error}' (Expected!)")
else:
    print("\n[4] Client Result: Received a response (Unexpected!)")

demo_lib.cleanUp()