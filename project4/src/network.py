import json
import socket
import threading
import copy
from dataclasses import dataclass, asdict
from enum import Enum
from state import *

@dataclass
class NodeInfo:
    ip: str
    tcp_port: int

class MessageType(str, Enum):
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
    
@dataclass
class TupleOutMessage(Message):
    node_id: str
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
    
@dataclass
class ThreadMigrateMessage(Message):
    thread: ThreadState

    @classmethod
    def from_dict(cls, data):
        return cls(
            type=MessageType(data["type"]),
            thread=data["thread"]
        )
    
    def to_dict(self):
        return {
            "type": self.type.value,
            "thread": self.thread.to_dict()
        }
    
@dataclass
class ShutdownMessage(Message):
    node_id: str

    @classmethod
    def from_dict(cls, data):
        return cls(
            type=MessageType(data["type"]),
            node_id = data["node_id"]
        )
    
    def to_dict(self):
        return {
            "type": self.type.value,
            "node_id": self.node_id
        }


class NetworkReceiver(threading.Thread):
    def __init__(self, node_id, scheduler, host="0.0.0.0", tcp_port=0):
        super().__init__(daemon=True)
        self.node_id = str(node_id)
        self.scheduler = scheduler
        scheduler.tuple_space.send_in = self.send_in
        scheduler.tuple_space.send_out = self.send_out
        scheduler.tuple_space.send_cancel = self.send_cancel
        self.nodes: dict[str, NodeInfo] = {}
        self.pending_requests: dict[str, tuple] = {}

        self.host = host
        self.tcp_port = tcp_port
        self.running = True
        self.server_socket = self._create_server_socket()
        self.start()

    def _create_server_socket(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind((self.host, self.tcp_port))
        self.tcp_port = sock.getsockname()[1]
        sock.listen()
        return sock

    def run(self):
        print(f"[NETWORK] TCP receiver listening on {self.host}:{self.tcp_port}")
        while self.running:
            try:
                conn, addr = self.server_socket.accept()
                self.handle_connection(conn, addr)
            except Exception as e:
                if self.running:
                    print(f"[NETWORK] TCP accept error: {e}")

    def add_node(self, node_id: str, address: str, port: int):
        self.nodes[node_id] = NodeInfo(address, port)

    def remove_node(self, node_id: str):
        self.nodes.pop(node_id)

    def find_node(self, node_ip: str, node_port: int) -> Optional[str]:
        for nid, info in self.nodes.items():
            if info.ip == node_ip and info.tcp_port == node_port:
                return nid
        return None

    def handle_connection(self, conn: socket.socket, addr):
        try:
            while self.running:
                payload = self._recv_message(conn)
                if payload is None:
                    break

                message = self._message_from_json(payload)
                match message.type:
                    case MessageType.OUT:
                        self.handle_out(message, conn)
                    case MessageType.IN:
                        self.handle_in(message, conn)
                    case MessageType.IN_CANCEL:
                        self.handle_in_cancel(message, conn)
                    case MessageType.OUT_CONSUME:
                        self.handle_out_consume(message, conn)
                    case MessageType.MIGRATE:
                        self.handle_migrate(message, conn)
                    case MessageType.SHUTDOWN:
                        self.handle_shutdown(message)
        except Exception as e:
            print(f"[NETWORK] Connection error: {e}")
        finally:
            conn.close()

    def handle_out(self, message: TupleOutMessage, conn):
        # print(f"[NETWORK] OUT from {message.node_id}: {message.tup}")
        self.scheduler.tuple_space.out(message.tup, message.node_id, message.thread_id)

    def handle_in(self, message: TupleInMessage, conn):
        # print(f"[NETWORK] IN from {message.req.owner}: {message.req}")
        self.scheduler.tuple_space.read(message.req.thread_id, message.req.pattern, message.req.is_destructive, message.req.owner)

    def handle_in_cancel(self, message: TupleIdMessage, conn):
        # print(f"[NETWORK] IN_CANCEL from {conn}: {message.thread_id}")
        if message.thread_id in self.pending_requests:
            tup = self.pending_requests.pop(message.thread_id)
            self.scheduler.tuple_space.out(tup)
        else:
            self.scheduler.tuple_space.local.remove_request(message.thread_id)

    def handle_out_consume(self, message: TupleIdMessage, conn):
        # print(f"[NETWORK] OUT_CONSUME from {conn}: {message.thread_id}")
        if message.thread_id in self.pending_requests:
            self.pending_requests.pop(message.thread_id)

    def send_in(self, req: TupleRequest):
        copy_req = copy.deepcopy(req)
        copy_req.owner = self.node_id
        msg = TupleInMessage(MessageType.IN, copy_req)

        for node in self.nodes.keys():
            self._send_message(node, msg)

    def send_out(self, tid: str, destructive: bool, owner: str, tup: tuple):
        msg = TupleOutMessage(MessageType.OUT, self.node_id, tid, tup)
        self._send_message(owner, msg)
        if destructive:
            self.pending_requests[tid] = tup

    def send_cancel(self, tid: str, destructive: bool, completer: str):
        if destructive:
            msg = TupleIdMessage(MessageType.OUT_CONSUME, tid)
            self._send_message(completer, msg)

        cancel = TupleIdMessage(MessageType.IN_CANCEL, tid)
        for node in self.nodes.keys():
            if destructive and node == completer:
                continue
            self._send_message(node, cancel)

    def handle_migrate(self, message: ThreadMigrateMessage, conn):
        state = ThreadState.from_dict(message.thread)
        ip, port = conn.getpeername()
        print(f"[NETWORK] Received migrated thread {state.thread_id} from {ip}:{port}...")
        self.scheduler.add_thread(state)

    def send_migrate(self, thread: ThreadState, node_id: str):
        msg = ThreadMigrateMessage(MessageType.MIGRATE, thread)
        thread.variables['$result'] = 10
        self._send_message(node_id, msg)

    def handle_shutdown(self, message: ShutdownMessage):
        node_info = self.nodes[message.node_id]
        print(f"[NETWORK] Received SHUTDOWN from {node_info.ip}:{node_info.tcp_port}")
        self.remove_node(message.node_id)

    def send_shutdown(self):
        msg = ShutdownMessage(MessageType.SHUTDOWN, self.node_id)
        for node in self.nodes.keys():
            self._send_message(node, msg)

    def _send_message(self, node_id: str, message: Message):
        data = message.to_json()
        length = len(data).to_bytes(4, "big")
        if node_id not in self.nodes.keys():
            return
        node = self.nodes[node_id]
        
        conn = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            conn.connect((node.ip, node.tcp_port))
            conn.sendall(length)
            conn.sendall(data)
        except Exception as e:
            print(f"[NETWORK] Failed to send message to {node_id}: {e}")
            self.remove_node(node_id)
        finally:
            conn.close()

    def _recv_exact(self, conn, size):
        data = b""

        while len(data) < size:
            chunk = conn.recv(size - len(data))
            if not chunk:
                return None
            data += chunk
        return data

    def _recv_message(self, conn):
        length_data = self._recv_exact(conn, 4)
        if not length_data:
            return None

        length = int.from_bytes(length_data, "big")
        payload = self._recv_exact(conn, length)
        if not payload:
            return None
        return payload.decode("utf-8")
    
    def _message_from_json(self, data: str):
        raw = json.loads(data)
        msg_type = raw["type"]

        match msg_type:
            case MessageType.OUT.value:
                return TupleOutMessage.from_dict(raw)
            case MessageType.IN.value:
                return TupleInMessage.from_dict(raw)
            case MessageType.IN_CANCEL.value | MessageType.OUT_CONSUME.value:
                return TupleIdMessage.from_dict(raw)
            case MessageType.MIGRATE:
                return ThreadMigrateMessage.from_dict(raw)
            case MessageType.SHUTDOWN:
                return ShutdownMessage.from_dict(raw)
            case _:
                raise ValueError(f"Unknown message type: {msg_type}")

    def stop(self):
        self.send_shutdown()
        self.running = False
        try:
            self.server_socket.shutdown(socket.SHUT_RDWR)
            self.server_socket.close()
        except Exception:
            pass