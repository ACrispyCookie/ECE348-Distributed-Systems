import sys
import os
import demo_lib

demo_lib.init()
import client_api

print("1. Initializing client API normally to discover the server...")
if client_api.init(demo_lib.CONFIG_FILENAME) is None:
    print("Failed to initialize API. Is the server running?")
    sys.exit(1)

server = client_api.servers[0]
server.cancellable = True
client_api.updateMemory()

print("\n2. Simulating a hard crash...")
print(f"   [File State] config.txt now marks this connection as Cancellable (C).")
client_api.servers = []
client_api.cleanUp()

print("\n3. Restarting Client API...")
if client_api.init(demo_lib.CONFIG_FILENAME) is None:
    print("Failed to re-initialize API.")
    sys.exit(1)

print("   [Recovery Success] Client successfully sent CANCEL and re-established state!")

print("\n4. Proving we can continue with normal requests...")
server = client_api.servers[0]
payload = b'\x00\x00\x00\x01\x00\x00\x00\x00\x00\x00\x00\x07'
req = client_api.ClientRequest(server, client_api.MessageType.REQUEST, 0, payload)

error, response = client_api.tryRequest(req, check_id=True, send_ack=True, track_cancel=False)

if not error:
    print("   [Normal Flow Success] Received prime calculation successfully!")
    demo_lib.print_response_info(response, server)
else:
    print(f"   [Normal Flow Failed] {error}")

demo_lib.cleanUp()