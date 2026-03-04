import socket
import struct
from enum import Enum

DEBUG = True
MEMORY_FILE = "memory.txt"

TIMEOUT_SECONDS = 2.0
MAX_RETRIES = 5

MAX_PAYLOAD_LENGTH = 4096
MAX_BUFFER_SIZE = MAX_PAYLOAD_LENGTH + 5 * 4

class MessageType(Enum):
    REQUEST = 0
    REPLY = 1
    ACKNOWLEDGE = 2

sock = None
serverAddress = None
clientId = 1
sequenceNumber = 1

def init(config_filename="config.txt"):
    global sock, serverAddress, clientId
    
    # Read the config file for the server address and port
    try:
        with open(config_filename, 'r') as f:
            ip = f.readline().strip()
            port = int(f.readline().strip())
            serverAddress = (ip, port)
    except FileNotFoundError:
        if DEBUG:
            print(f"[API] Error: Could not find {config_filename}")
        exit(1)
        
    # Read persistent memory for incarnation number and client port
    try:
        with open(MEMORY_FILE, 'r') as f:
            clientId = int(f.readline().strip()) + 1
        with open(MEMORY_FILE, 'w') as f:
            f.write(str(clientId))
    except FileNotFoundError:
        f = open(MEMORY_FILE, 'a')
        f.write(str(clientId))
        f.close()
    
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    
    if DEBUG:
        print(f"[API] Initialized. Client ID: {clientId}, Server: {serverAddress}")

def doRequestReply(serviceId, payload):
    global sequenceNumber, clientId
    
    # Serialize packet
    header = struct.pack("!IIIII", 
                         MessageType.REQUEST.value, 
                         clientId, 
                         serviceId, 
                         sequenceNumber, 
                         len(payload))
    packet = header + payload
    
    # Try MAX_RETRIES times
    for attempt in range(MAX_RETRIES):
        try:
            sock.sendto(packet, serverAddress)
            sock.settimeout(TIMEOUT_SECONDS)
            
            response_data, server = sock.recvfrom(MAX_BUFFER_SIZE)
            
            resp_header = response_data[:20]
            r_type, r_client_id, r_service_id, r_seq_num, r_len = struct.unpack("!IIIII", resp_header)
            
            # Check if this is exactly the reply we are waiting for
            if r_type == MessageType.REPLY.value and r_client_id == clientId and r_seq_num == sequenceNumber:
                
                ackHeader = struct.pack("!IIIII", MessageType.ACKNOWLEDGE.value, clientId, serviceId, sequenceNumber, 0)
                sock.sendto(ackHeader, serverAddress)
                
                sequenceNumber += 1 

            return response_data[20:]
                
        except socket.timeout:
            if DEBUG:
                print(f"[API] Timeout on attempt {attempt + 1}. Retrying...")
            continue
            
    raise TimeoutError("Server is unresponsive after maximum retries.")

def cleanUp():
    """Closes the socket."""
    if sock:
        sock.close()