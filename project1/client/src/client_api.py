import socket
import struct
from enum import Enum
from dataclasses import dataclass
from typing import Optional
import random

DEBUG = True
TIMEOUT_SECONDS = 2.0
MAX_RETRIES = 5

DISCOVERY_PORT = 12345
DISCOVERY_TIMEOUT = 2.0

MAX_PAYLOAD_LENGTH = 4096
MAX_BUFFER_SIZE = MAX_PAYLOAD_LENGTH + 5 * 4

class MessageType(Enum):
    REQUEST = 0
    REPLY = 1
    EXPIRED = 2
    NOT_SERVING = 3
    ACKNOWLEDGE = 4
    CANCEL = 5

@dataclass
class ServerEntry:
    cancellable: bool
    address: tuple
    client_id: int
    sequence_number: int

@dataclass
class ClientRequest:
    server: ServerEntry
    message_type: MessageType
    service_id: int
    payload: bytes

@dataclass
class ServerResponse:
    message_type: int
    client_id: int
    service_id: int
    length: int
    payload: bytes

sock = None
servers: list[ServerEntry] = []
memory_file = None
current_server_index = 0

def init(config_filename="config.txt"):
    global servers, sock, memory_file, current_server_index

    memory_file = config_filename
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    
    # Read the config file for the server address and port
    try:
        with open(memory_file, 'r') as file:
            for line in file:
                if line.startswith('#'):
                    continue

                parts = line.strip().split()
                if not parts:
                    continue

                if len(parts) == 5:
                    entry = ServerEntry(
                        cancellable=(parts[0] == 'C'),
                        address=(parts[1], int(parts[2])),
                        client_id=int(parts[3]),
                        sequence_number=int(parts[4])
                    )

                servers.append(entry)
    except FileNotFoundError:
        print(f"[API] Error: Could not find {memory_file}")
        return None
    
    doDiscovery()
        
    if len(servers) == 0:
        print(f"[Discovery] Error: Could not find any servers")
        return None
    
    for server in servers:
        if(initializeConnection(server) == None):
            print(f"[API] Error: Could not initialize server connections")
            return None
        
    if len(servers) > 0:
        current_server_index = random.randint(0, len(servers) - 1)
    
    if DEBUG:
        print(servers)
        print(f"[API] Initialized.")
    return True

def initializeConnection(server: ServerEntry):
    if (server.cancellable): # Clean any old request
        request = ClientRequest(server=server, message_type=MessageType.CANCEL, service_id=0, payload=None)
        error, response = tryRequest(request, True, False, False)
        if (error != None):
            return None

        server.cancellable = False
        server.sequence_number += 1
        updateMemory()
    elif (server.client_id == 0): # Initial handshake with server
        request = ClientRequest(server=server, message_type=MessageType.REQUEST, service_id=0, payload=None)
        error, response = tryRequest(request, False, False, False)
        if (error != None or response.client_id == 0):
            return None
        
        server.client_id = response.client_id
        server.sequence_number += 1
        updateMemory()

    return True

def doDiscovery():
    global servers
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.settimeout(DISCOVERY_TIMEOUT)
    
    discovery_msg = b"DISCOVER\0"
    expected_reply_prefix = b"DISCOVER_RPL\0"
    
    try:
        sock.sendto(discovery_msg, ('<broadcast>', DISCOVERY_PORT))
        
        while True:
            data, addr = sock.recvfrom(1024)
            
            if data.startswith(expected_reply_prefix):
                port_offset = len(expected_reply_prefix)
                port_bytes = data[port_offset : port_offset + 4]
                
                if len(port_bytes) == 4:
                    server_port = struct.unpack("!i", port_bytes)[0]
                    server_ip = addr[0]

                    for server in servers:
                        if server.address[0] == server_ip:
                            server.address = (server_ip, server_port)
                            break
                    else:
                        servers.append(ServerEntry(
                            cancellable=False,
                            address=(server_ip, server_port),
                            client_id=0,
                            sequence_number=0
                        ))
    except socket.timeout:
        return
    finally:
        sock.close()

def doRequestReply(serviceId, payload) -> Optional[str] | Optional[bytes]:
    for _ in range(len(servers)):
        server = getNextServer()
        request = ClientRequest(server=server, message_type=MessageType.REQUEST, service_id=serviceId, payload=payload)

        error, response = tryRequest(request, True, True, True)
        if (error == None):
            server.sequence_number += 1
            updateMemory()
            return None, response.payload
        
    return error, None

def tryRequest(request: ClientRequest, check_id, send_ack, track_cancel) -> Optional[str] | Optional[ServerResponse] :
    # Serialize request
    server = request.server

    payload_len = len(request.payload) if request.payload is not None else 0
    header = struct.pack("!IIIII", 
                         request.message_type.value, 
                         server.client_id, 
                         request.service_id, 
                         server.sequence_number,
                         payload_len)
    packet = header + request.payload if request.payload is not None else header

    # Try MAX_RETRIES times
    for attempt in range(MAX_RETRIES):
        try:
            if track_cancel:
                server.cancellable = True
                updateMemory()
            sock.sendto(packet, server.address)
            sock.settimeout(TIMEOUT_SECONDS)

            while True:    
                response_data, server_addr = sock.recvfrom(MAX_BUFFER_SIZE)
                
                resp_header = response_data[:20]
                r_type, r_client_id, r_service_id, r_seq_num, r_len = struct.unpack("!IIIII", resp_header)
                
                # Check if this is exactly the reply we are waiting for
                if r_seq_num == server.sequence_number and (not check_id or r_client_id == server.client_id):
                    if r_type == MessageType.EXPIRED.value or r_type == MessageType.NOT_SERVING.value:
                        if track_cancel:
                            server.cancellable = False
                            updateMemory()
                        return "Not serving." if r_type == MessageType.NOT_SERVING.value else "Timeout.", None
                    if r_type != MessageType.REPLY.value:
                        continue

                    if track_cancel:
                        server.cancellable = False
                        updateMemory()
                    
                    if send_ack:
                        ack_header = struct.pack("!IIIII", MessageType.ACKNOWLEDGE.value, server.client_id, request.service_id, server.sequence_number, 0)
                        sock.sendto(ack_header, server.address)

                    return None, ServerResponse(
                        message_type=r_type,
                        client_id=r_client_id,
                        service_id=r_service_id,
                        length=r_len,
                        payload=response_data[20:]
                    )
                
        except socket.timeout:
            if DEBUG:
                print(f"[API] Timeout on attempt {attempt + 1}. Retrying...")
            continue
            
    return "Timeout.", None

def updateMemory():
    with open(memory_file, 'w') as f:
        for entry in servers:
            ip, port = entry.address
            char = 'C' if entry.cancellable else 'S' 
            f.write(f"{char} {ip} {port} {entry.client_id} {entry.sequence_number}\n")

def getNextServer() -> ServerEntry:
    global current_server_index
    server = servers[current_server_index]
    current_server_index = (current_server_index + 1) % len(servers)
    return server

def cleanUp():
    """Closes the socket."""
    if sock:
        sock.close()