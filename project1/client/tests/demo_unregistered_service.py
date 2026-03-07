import sys
import os
import demo_lib

demo_lib.init()
import client_api

server = client_api.servers[0]

INVALID_SERVICE_ID = 999
payload = b'\x00\x00\x00\x01\x00\x00\x00\x00\x00\x00\x00\x07'

req = client_api.ClientRequest(server, client_api.MessageType.REQUEST, INVALID_SERVICE_ID, payload)

print("\n[2] Sending eequest to unsupported service")
demo_lib.print_packet_info(req)

error, response = client_api.tryRequest(req, check_id=True, send_ack=True, track_cancel=False)

if error:
    print(f"\n[3] Client result: Request correctly failed with error -> '{error}'")
else:
    print("\n[3] Client result: Received a response (Unexpected!)")

demo_lib.cleanUp()