import socket
import threading
import selectors
import copy
import os
import time
from network.network_msgs import *
from network.state import *
from network.discovery import DiscoveryThread

class NetworkThread(threading.Thread):
    def __init__(self, scheduler, host="0.0.0.0", tcp_port=0):
        super().__init__(daemon=True)
        self.node_id = str(uuid.uuid4())
        self.node_addr = host, tcp_port
        self.scheduler = scheduler
        self.running = True

        self.lock = threading.RLock()
        self.shutdown_prepare_condition = threading.Condition(self.lock)
        self.pending_requests: dict[str, tuple] = {}
        self.nodes: dict[str, tuple] = {}
        self.recv_buffers: dict[str, bytes] = {}
        self.draining = False
        self.draining_nodes: set[str] = set()
        self.shutdown_prepare_acks: set[str] = set()

        scheduler.tuple_space.send_in = self.send_in
        scheduler.tuple_space.send_out = self.send_out
        scheduler.tuple_space.send_cancel = self.send_cancel

        self.server_socket = self._create_server_socket()
        print(f"[NETWORK] Running main socket on {self.node_addr[0]}:{self.node_addr[1]}.")
        self.start()

        self.discovery = DiscoveryThread(self)

    def _create_server_socket(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(self.node_addr)
        self.node_addr = self._get_actual_address(), sock.getsockname()[1]
        sock.listen()
        sock.setblocking(False)

        self.sel = selectors.DefaultSelector()
        self.sel.register(sock, selectors.EVENT_READ, data=None)

        self._wake_recv, self._wake_send = socket.socketpair()
        self._wake_recv.setblocking(False)
        self.sel.register(self._wake_recv, selectors.EVENT_READ, data="__wake__")
        return sock

    def run(self):
        while self.running:
            events = self.sel.select()
            if not self.running:
                return
                
            with self.lock:
                for key, mask in events:
                    if key.data == "__wake__": # Wakeup
                        self._wake_recv.recv(1024)
                        continue
                    elif key.data is None:  # Server socket
                        conn, addr = self.server_socket.accept()
                        self.nodes[str(addr)] = conn, addr
                        self.sel.register(conn, selectors.EVENT_READ, data=str(addr))
                    else:  # Client socket
                        node_id = key.data
                        self._handle_read_ready(node_id)

    def establish_connection(self, node_id: str, remote_addr: tuple):
        with self.lock:
            if node_id in self.nodes or remote_addr == self.node_addr:
                return  # Already connected
            
            try:
                print(f"[NETWORK] Discovered node on {remote_addr[0]}:{remote_addr[1]}, sending NEW message")
                conn = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                conn.settimeout(5.0)
                conn.connect(remote_addr)
                conn.setblocking(False)
                
                self.nodes[node_id] = conn, remote_addr
                self.sel.register(conn, selectors.EVENT_READ, data=node_id)
                self._send_new(node_id)
            except Exception as e:
                print(f"[NETWORK] Failed to connect to {remote_addr}: {e}")

    def new_node(self, node_addr: tuple, conn: socket.socket, node_id:str, real_node_addr: tuple, requests: list[dict[str, Any]]):
        with self.lock:
            tuple_reqs = [
                TupleRequest.from_dict(req)
                for req in requests
            ]
            self.scheduler.tuple_space.add_foreign_requests(tuple_reqs)

            if str(node_addr) in self.nodes: # I was discovered
                self.sel.unregister(conn)
                del self.nodes[str(node_addr)]
                self.nodes[node_id] = conn, real_node_addr
                self.sel.register(conn, selectors.EVENT_READ, data=node_id)
                self._send_new(node_id)

    def remove_node(self, node_id: str, tuples: list):
        with self.lock:
            for tid, (owner, actual_tup) in list(self.pending_requests.items()):
                if owner == node_id:
                    self.scheduler.tuple_space.out(actual_tup)
                    del self.pending_requests[tid]

            self.scheduler.tuple_space.remove_all_requests(node_id)

            for tup in tuples or []:
                self.scheduler.tuple_space.out(tup)
            if node_id in self.nodes:
                del self.nodes[node_id]
            self.draining_nodes.discard(node_id)
            self.shutdown_prepare_acks.discard(node_id)

    def mark_node_draining(self, node_id: str):
        with self.lock:
            self.draining_nodes.add(node_id)

    def mark_shutdown_prepare_ack(self, node_id: str):
        with self.shutdown_prepare_condition:
            self.shutdown_prepare_acks.add(node_id)
            self.shutdown_prepare_condition.notify_all()

    def _available_nodes(self):
        return [
            node_id for node_id in self.nodes.keys()
            if node_id not in self.draining_nodes
        ]

    def node_id_for_address(self, ip: str, port: int):
        with self.lock:
            for node_id, (_conn, addr) in self.nodes.items():
                if addr[0] == ip and addr[1] == port:
                    return node_id
        return None


    def send_in(self, req: TupleRequest):
        with self.lock:
            copy_req = copy.deepcopy(req)
            copy_req.owner = self.node_id
            msg = TupleInMessage(MessageType.IN, copy_req)

            for node in self._available_nodes():
                self._send_message(node, msg)

    def send_out(self, tid: str, destructive: bool, owner: str, tup: tuple):
        with self.lock:
            msg = TupleOutMessage(MessageType.OUT, self.node_id, tid, tup)
            self._send_message(owner, msg)
            if destructive:
                self.pending_requests[tid] = owner, tup

    def send_cancel(self, tid: str, destructive: bool, completer: str, exclude_node: str | None = None):
        with self.lock:
            if destructive and completer:
                msg = TupleIdMessage(MessageType.OUT_CONSUME, tid)
                self._send_message(completer, msg)

            cancel = TupleIdMessage(MessageType.IN_CANCEL, tid)
            for node in self.nodes.keys():
                if node == exclude_node:
                    continue
                if destructive and node == completer:
                    continue
                self._send_message(node, cancel)

    def send_migrate(self, thread: ThreadState, ip: str, port: int):
        with self.lock:
            msg = ThreadMigrateMessage(MessageType.MIGRATE, self.node_id, thread)

            found = None
            for node_id, tup in self.nodes.items():
                conn, addr = tup
                if addr[0] == ip and addr[1] == port:
                    found = node_id
                    break

            if not found or found in getattr(self, "draining_nodes", set()):
                return False
            send_ok = self._send_message(found, msg)
            return send_ok

    def shutdown(self):
        self.discovery.shutdown()
        self.discovery.join()

        with self.lock:
            if not self.nodes:
                self.close()
                return False

            self.draining = True
            expected_acks = set()
            self.shutdown_prepare_acks.clear()
            prepare = ShutdownPrepareMessage(MessageType.SHUTDOWN_PREPARE, self.node_id)
            for node in list(self.nodes.keys()):
                if self._send_message(node, prepare):
                    expected_acks.add(node)

        if not self._wait_for_shutdown_prepare_acks(expected_acks):
            print("[NETWORK] Shutdown prepare timed out; aborting graceful shutdown.")
            with self.lock:
                self.draining = False
            return False

        with self.lock:
            available_nodes = self._available_nodes()
            if not available_nodes:
                print("[NETWORK] Cannot shutdown safely: no non-draining nodes available.")
                self.draining = False
                return False

            target_node = available_nodes[0]
            target_addr = self.nodes[target_node][1]
            for tid in list(self.scheduler.get_threads().keys()):
                if not self.scheduler.migrate_thread(tid, target_addr[0], target_addr[1]):
                    print(f"[NETWORK] Failed to migrate thread {tid}; aborting graceful shutdown.")
                    self.draining = False
                    return False

            my_tuples = self.scheduler.tuple_space.get_tuples(True)

            for node in list(self.nodes.keys()):
                tuples = my_tuples if node == target_node else []
                msg = ShutdownMessage(MessageType.SHUTDOWN, self.node_id, tuples)
                self._send_message(node, msg)

            self.close()
            return True

    def _wait_for_shutdown_prepare_acks(self, expected_acks: set[str], timeout: float = 5.0) -> bool:
        deadline = time.time() + timeout
        with self.shutdown_prepare_condition:
            while not expected_acks.issubset(self.shutdown_prepare_acks):
                remaining = deadline - time.time()
                if remaining <= 0:
                    return False
                self.shutdown_prepare_condition.wait(timeout=remaining)
            return True

    def close(self):
        with self.lock:
            self.running = False
            try:
                self._wake_send.send(b'\x00')
                self.server_socket.shutdown(socket.SHUT_RDWR)
                self.server_socket.close()
                self.sel.close()
            except Exception:
                pass

    def _send_new(self, node_id):
        requests = [
            req.to_dict()
            for req in self.scheduler.tuple_space.get_local_requests(self.node_id)
        ]
        response = NewMessage(
            type=MessageType.NEW,
            node_id=self.node_id,
            node_addr=self.node_addr,
            requests=requests
        )
        self._send_message(node_id, response)

    def _send_message(self, node_id: str, message: Message) -> bool:
        data = message.to_json()
        length = len(data).to_bytes(4, "big")
        if node_id not in self.nodes.keys():
            return False
        conn = self.nodes[node_id][0]
        
        try:
            conn.sendall(length)
            conn.sendall(data)
            return True
        except Exception as e:
            print(f"[NETWORK] Failed to send message to {node_id}: {e}")
            self.sel.unregister(conn)
            conn.close()
            del self.nodes[node_id]
            return False

    def _handle_read_ready(self, node_id):
        try:
            if node_id not in self.nodes:
                return
            
            conn, addr = self.nodes[node_id]
            data = conn.recv(4096)
            if not data:
                self.sel.unregister(conn)
                conn.close()
                del self.nodes[node_id]
                raise ValueError("Data was empty!")
            
            if node_id not in self.recv_buffers:
                self.recv_buffers[node_id] = b''
            self.recv_buffers[node_id] += data
            
            while True:
                payload = self._try_parse_message(node_id)
                if payload is None:
                    break
                Message.handle_from_json(payload, self, addr, conn)
        except Exception as e:
            print(f"[NETWORK] Read error: {e}")

    def _try_parse_message(self, addr) -> Optional[str]:
        buf = self.recv_buffers.get(addr, b'')
        if len(buf) < 4:
            return None
        
        length = int.from_bytes(buf[:4], "big")
        total_needed = 4 + length
        if len(buf) < total_needed:
            return None
        
        msg_bytes = buf[4:total_needed]
        self.recv_buffers[addr] = buf[total_needed:] # Keep remainder for next message
    
        try:
            return msg_bytes.decode("utf-8")
        except Exception as e:
            print(f"[NETWORK] Failed to decode message from {addr}: {e}")
            return None
    
    def _get_actual_address(self):
        """Get the actual IP address."""
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            # Connect to public DNS (doesn't actually send anything)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
        except:
            ip = "127.0.0.1"
        finally:
            s.close()
        return ip