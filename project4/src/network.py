import socket
import struct
import threading
import json

class DiscoveryThread(threading.Thread):
    def __init__(self, node_id, multicast_group='224.1.1.1', port=10100):
        super().__init__(daemon=True)
        self.node_id = node_id
        self.multicast_group = multicast_group
        self.port = port
        self.running = True

        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind(('', self.port))

        mreq = struct.pack('4sL', socket.inet_aton(self.multicast_group), socket.INADDR_ANY)
        self.sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)

        ttl = struct.pack('b', 5)
        self.sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, ttl)

    def run(self):
        discovery_msg = json.dumps({
            "action": "HELLO",
            "node_id": self.node_id
        }).encode('utf-8')
        
        print(f"[{self.node_id}] Discovering nodes on {self.multicast_group}:{self.port}...")
        self.sock.sendto(discovery_msg, (self.multicast_group, self.port))

        while self.running:
            try:
                data, addr = self.sock.recvfrom(1024)
                message = json.loads(data.decode('utf-8'))
                
                sender_id = message.get("node_id")
                if sender_id == self.node_id:
                    continue
                    
                print(f"[{self.node_id}] Received '{message['action']}' from {sender_id} at {addr[0]}")
                if message.get("action") == "HELLO":
                    reply_msg = json.dumps({
                        "action": "REPLY",
                        "node_id": self.node_id
                    }).encode('utf-8')
                    self.sock.sendto(reply_msg, addr)
                    
            except Exception as e:
                if self.running:
                    print(f"Network error: {e}")

    def stop(self):
        self.running = False
        try:
            temp_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            temp_sock.sendto(b"{}", ('127.0.0.1', self.port))
            temp_sock.close()
        except:
            pass