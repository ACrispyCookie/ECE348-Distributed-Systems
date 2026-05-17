import json
import socket
import struct
import threading
import selectors
import time

from dataclasses import dataclass, asdict
from enum import Enum

class MessageType(str, Enum):
    INITIALIZE = "INITIALIZE"

@dataclass
class Message:
    type: MessageType

    def to_dict(self) -> dict:
        return {
            **asdict(self),
            "type": self.type.value
        }

    def to_json(self) -> bytes:
        return json.dumps(self.to_dict()).encode("utf-8")
    
@dataclass
class InitializeMessage(Message):
    tcp_port: int
    node_id: str
    pass

class DiscoveryThread(threading.Thread):
    def __init__(self, network, multicast_group='224.1.1.1', port=10100, interval=1.0):
        super().__init__(daemon=True)
        self.network = network
        self.running = True

        self.multicast_group = multicast_group
        self.port = port
        self.interval = interval
        self.sock = self._create_socket()

        print(f"[DISCOVERY] Discovering nodes on {self.multicast_group}:{self.port} every {self.interval} seconds.")
        self.start()

    def _create_socket(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(("", self.port))

        mreq = struct.pack(
            "4sL",
            socket.inet_aton(self.multicast_group),
            socket.INADDR_ANY
        )
        sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)

        ttl = struct.pack("b", 5)
        sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, ttl)
        
        sock.setblocking(False)
        return sock

    def run(self):
        sel = selectors.DefaultSelector()
        sel.register(self.sock, selectors.EVENT_READ, data='discovery')
        next_broadcast = time.time()
        
        while self.running:
            try:
                timeout = max(0.1, next_broadcast - time.time())
                events = sel.select(timeout=timeout)
                
                for key, mask in events:
                    data, addr = self.sock.recvfrom(4096)
                    self._handle_initialize(data, addr)
                
                if time.time() >= next_broadcast:
                    self._send_initialize()
                    next_broadcast = time.time() + self.interval
            except OSError:
                break
            
    def _handle_initialize(self, data, addr):
        try:
            payload = json.loads(data.decode("utf-8"))
            tcp_port = payload["tcp_port"]
            node_id = payload["node_id"]
            
            if self.network.node_id > node_id:
                remote_addr = (addr[0], tcp_port)
                self.network.establish_connection(node_id, remote_addr)
        except Exception as e:
            print(f"[DISCOVERY] Failed to handle INITIALIZE: {e}")

    def _send_initialize(self):
        message = InitializeMessage(type=MessageType.INITIALIZE, tcp_port=self.network.node_addr[1], node_id=self.network.node_id)
        self.sock.sendto(message.to_json(), (self.multicast_group, self.port))

    def shutdown(self):
        self.running = False
        try:
            self.sock.shutdown(socket.SHUT_RDWR)
            self.sock.close()
        except Exception:
            pass