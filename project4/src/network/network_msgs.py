import socket
import json
from typing import Any
from enum import Enum
from dataclasses import dataclass
from network.state import ThreadState, TupleRequest

class MessageType(str, Enum):
    NEW = "NEW"
    OUT = "OUT"
    OUT_CONSUME = "OUT_CONSUME"
    IN = "IN"
    IN_CANCEL = "IN_CANCEL"
    MIGRATE = "MIGRATE"
    SHUTDOWN = "SHUTDOWN"

@dataclass
class Message:
    type: MessageType

    def to_dict(self):
        return {
            "type": self.type.value
        }

    def to_json(self):
        return json.dumps(self.to_dict()).encode("utf-8")
    
    @classmethod
    def handle_from_json(self, data: str, network, addr, conn):
        raw = json.loads(data)
        msg_type = raw["type"]

        match msg_type:
            case MessageType.OUT.value:
                TupleOutMessage.from_dict(raw).handle(network, addr, conn)
            case MessageType.IN.value:
                TupleInMessage.from_dict(raw).handle(network, addr, conn)
            case MessageType.IN_CANCEL.value | MessageType.OUT_CONSUME.value:
                TupleIdMessage.from_dict(raw).handle(network, addr, conn)
            case MessageType.MIGRATE:
                ThreadMigrateMessage.from_dict(raw).handle(network, addr, conn)
            case MessageType.SHUTDOWN:
                ShutdownMessage.from_dict(raw).handle(network, addr, conn)
            case MessageType.NEW:
                NewMessage.from_dict(raw).handle(network, addr, conn)
            case _:
                raise ValueError(f"Unknown message type: {msg_type}")
            
@dataclass
class NewMessage(Message):
    node_id: str
    node_addr: tuple
    requests: list[dict[str, Any]]

    @classmethod
    def from_dict(cls, data: dict):
        return cls(
            type=MessageType(data["type"]),
            node_id=data["node_id"],
            node_addr=tuple(data["node_addr"]),
            requests=data["requests"]
        )
    
    def to_dict(self):
        return {
            "type": self.type.value,
            "node_id": self.node_id,
            "node_addr": self.node_addr,
            "requests": self.requests
        }
    
    def handle(self, network, addr, conn: socket.socket): 
        print(f"[NETWORK] NEW from {self.node_addr[0]}:{self.node_addr[1]}")
        network.new_node(addr, conn, self.node_id, self.node_addr, self.requests)
    
@dataclass
class TupleInMessage(Message):
    req: TupleRequest

    @classmethod
    def from_dict(cls, data):
        return cls(
            type=MessageType(data["type"]),
            req=TupleRequest.from_dict(data["req"])
        )
    
    def to_dict(self):
        return {
            "type": self.type.value,
            "req": self.req.to_dict()
        }
    
    def handle(self, network, addr, conn: socket.socket): 
        # print(f"[NETWORK] IN from {self.req.owner}: {self.req}")
        network.scheduler.tuple_space.read(self.req.thread_id, self.req.pattern, self.req.is_destructive, self.req.owner)
    
@dataclass
class TupleOutMessage(Message):
    node_id: tuple
    thread_id: str
    tup: tuple

    @classmethod
    def from_dict(cls, data):
        return cls(
            type=MessageType(data["type"]),
            node_id=data["node_id"],
            thread_id=data["thread_id"],
            tup=data["tup"]
        )
    
    def to_dict(self):
        return {
            "type": self.type.value,
            "node_id": self.node_id,
            "thread_id": self.thread_id,
            "tup": self.tup
        }
    
    def handle(self, network, addr, conn: socket.socket):
        # print(f"[NETWORK] OUT from {message.node_id}: {message.tup}")
        network.scheduler.tuple_space.out(self.tup, self.node_id, self.thread_id)
    
@dataclass
class TupleIdMessage(Message):
    thread_id: str

    @classmethod
    def from_dict(cls, data):
        return cls(
            type=MessageType(data["type"]),
            thread_id=data["thread_id"]
        )
    
    def to_dict(self):
        return {
            "type": self.type.value,
            "thread_id": self.thread_id
        }
    
    def handle(self, network, addr, conn: socket.socket):
        if self.type == "OUT_CONSUME":
            # print(f"[NETWORK] OUT_CONSUME from {conn}: {message.thread_id}")
            if self.thread_id in network.pending_requests:
                network.pending_requests.pop(self.thread_id)
        else:
            # print(f"[NETWORK] IN_CANCEL from {conn}: {message.thread_id}")
            if self.thread_id in network.pending_requests:
                _owner, actual_tup = network.pending_requests.pop(self.thread_id)
                network.scheduler.tuple_space.out(actual_tup)
            else:
                network.scheduler.tuple_space.remove_request(self.thread_id)
    
@dataclass
class ThreadMigrateMessage(Message):
    node_id: str
    thread: ThreadState

    @classmethod
    def from_dict(cls, data):
        return cls(
            type=MessageType(data["type"]),
            node_id=data["node_id"],
            thread=data["thread"]
        )
    
    def to_dict(self):
        return {
            "type": self.type.value,
            "node_id": self.node_id,
            "thread": self.thread.to_dict()
        }
    
    def handle(self, network, addr, conn: socket.socket):
        state = ThreadState.from_dict(self.thread)
        ip, port = conn.getpeername()
        added = network.scheduler.add_thread(state)
        if added:
            print(f"[NETWORK] Received migrated thread {state.thread_id} from {ip}:{port}...")
        else:
            network.send_migrate(state, self.node_id)
    
@dataclass
class ShutdownMessage(Message):
    node_id: str
    tuples: list = None

    @classmethod
    def from_dict(cls, data):
        return cls(
            type=MessageType(data["type"]),
            node_id=data["node_id"],
            tuples=data["tuples"]
        )
    
    def to_dict(self):
        return {
            "type": self.type.value,
            "node_id": self.node_id,
            "tuples": self.tuples
        }
    
    def handle(self, network, addr, conn: socket.socket):
        print(f"[NETWORK] Received SHUTDOWN from {addr[0]}:{addr[1]}")
        network.remove_node(self.node_id, self.tuples)