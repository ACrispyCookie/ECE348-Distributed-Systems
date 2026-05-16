import json
import socket
import struct
import threading

from dataclasses import dataclass, asdict
from enum import Enum
from typing import Any

from tuple_space import TupleRequest

class MessageType(str, Enum):
    INITIALIZE = "INITIALIZE"
    INITIALIZE_RESPONSE = "INITIALIZE_RESPONSE"

@dataclass
class Message:
    type: MessageType
    node_id: str

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
    pass

@dataclass
class InitializeResponseMessage(Message):
    tcp_port: int
    requests: list[dict[str, Any]]

    @classmethod
    def from_dict(cls, data: dict):
        return cls(
            type=MessageType(data["type"]),
            node_id=data["node_id"],
            tcp_port=data["tcp_port"],
            requests=data["requests"]
        )

class DiscoveryThread(threading.Thread):
    def __init__(self, node_id, scheduler, network, multicast_group='224.1.1.1', port=10100):
        super().__init__(daemon=True)
        self.node_id = str(node_id)
        self.scheduler = scheduler
        self.network = network
        self.multicast_group = multicast_group
        self.port = port
        self.running = True
        self.sock = self._create_socket()

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
        sock.setsockopt(
            socket.IPPROTO_IP,
            socket.IP_ADD_MEMBERSHIP,
            mreq
        )

        ttl = struct.pack("b", 5)
        sock.setsockopt(
            socket.IPPROTO_IP,
            socket.IP_MULTICAST_TTL,
            ttl
        )

        return sock

    def run(self):
        self.send_initialize()
        while self.running:
            try:
                data, addr = self.sock.recvfrom(4096)
                payload = json.loads(data.decode("utf-8"))
                sender_id = payload.get("node_id")

                if sender_id == self.node_id:
                    continue

                message_type = MessageType(payload["type"])
                match message_type:
                    case MessageType.INITIALIZE:
                        self.handle_initialize(payload, addr)
                    case MessageType.INITIALIZE_RESPONSE:
                        self.handle_initialize_response(payload, addr)
            except Exception as e:
                if self.running:
                    print(f"[{self.node_id}] Network error: {e}")
            
    def handle_initialize(self, payload, addr):
        sender_id = payload["node_id"]
        tcp_port = payload["tcp_port"]
        print(f"[DISCOVERY] Received INITIALIZE from {addr[0]}:{addr[1]}")

        requests = [
            req.to_dict()
            for req in self.scheduler.tuple_space.get_local_requests(self.node_id)
        ]
        response = InitializeResponseMessage(
            type=MessageType.INITIALIZE_RESPONSE,
            node_id=self.node_id,
            tcp_port=self.network.tcp_port,
            requests=requests
        )
        self.network.add_node(sender_id, addr[0], tcp_port)
        self.sock.sendto(response.to_json(), addr)

    def handle_initialize_response(self, payload, addr):
        message = InitializeResponseMessage.from_dict(payload)
        print(f"[DISCOVERY] Received INITIALIZE_RESPONSE from {addr[0]}:{addr[1]}")

        requests = [
            TupleRequest.from_dict(req)
            for req in message.requests
        ]
        self.network.add_node(message.node_id, addr[0], message.tcp_port)
        self.scheduler.tuple_space.add_foreign_requests(requests)

    def send_initialize(self):
        print(f"[DISCOVERY] Discovering nodes on {self.multicast_group}:{self.port}")
        message = InitializeMessage(type=MessageType.INITIALIZE, node_id=self.node_id, tcp_port=self.network.tcp_port)
        self.sock.sendto(message.to_json(), (self.multicast_group, self.port))

    def stop(self):
        self.running = False
        try:
            temp_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            temp_sock.sendto(b"{}", ('127.0.0.1', self.port))
            temp_sock.close()
        except:
            pass