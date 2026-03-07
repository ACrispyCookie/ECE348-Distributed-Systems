import sys
import os
import demo_lib

demo_lib.init()
import client_api

server = client_api.servers[0]

req = client_api.ClientRequest(server, client_api.MessageType.REQUEST, 0, b'\x00\x00\x00\x01\x00\x00\x00\x00\x00\x00\x00\x07')

print("\n[2] Sending first request...")
demo_lib.print_packet_info(req)
client_api.tryRequest(req, check_id=True, send_ack=True, track_cancel=False)

print("\n[3] Sending duplicate request after ACK...")
input("\n[PAUSE] Press Enter...")
demo_lib.print_packet_info(req)
error, response = client_api.tryRequest(req, check_id=True, send_ack=True, track_cancel=False)

if error:
    print(f"\n[4] Client Result: Request failed with '{error}' (Expected!)")
else:
    print("\n[4] Client Result: Received a response (Unexpected!)")

demo_lib.cleanUp()
