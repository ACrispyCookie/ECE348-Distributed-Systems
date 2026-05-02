import socket
import struct

HOST = '127.0.0.1'
PORT = 10100
BODY_SIZE = 256  # sizeof(union body) — largest member is OpenReq.fname[256]

def send_recv(sock, data):
    sock.sendto(data, (HOST, PORT))
    resp, _ = sock.recvfrom(65536)
    return resp

def parse_open_resp(buf):
    # Header: type(4) + seq(4) + is_response(1) = 9 bytes
    # Body (OpenResp): fid(4) + mtime(8) + size(8) = 20 bytes
    hdr_type, seq, is_resp = struct.unpack_from('!IIB', buf, 0)
    fid, mtime, size = struct.unpack_from('!IQQ', buf, 9)
    print(f"  type={hdr_type} seq={seq} is_response={is_resp}")
    print(f"  fid={fid} mtime={mtime} size={size}")
    return fid

def test_open(sock, fname):
    print(f"[OPEN] {fname}")
    body = fname.encode().ljust(256, b'\x00')   # fname padded to 256 bytes
    pkt  = struct.pack('!IIB', 0, 1, 0) + body  # type=OPEN(0), seq=1, is_response=0
    resp = send_recv(sock, pkt)
    return parse_open_resp(resp)

def test_write(sock, fid, offset, data, seq):
    print(f"[WRITE] fid={fid} offset={offset} data={data}")
    body = struct.pack('!III', fid, offset, len(data))
    body = body.ljust(256, b'\x00')
    pkt  = struct.pack('!IIB', 2, seq, 0) + body + data  # type=WRITE(2)
    resp = send_recv(sock, pkt)
    hdr_type, rseq, is_resp = struct.unpack_from('!IIB', resp, 0)
    fid_r, mtime, size = struct.unpack_from('!IQQ', resp, 9)
    print(f"  type={hdr_type} fid={fid_r} mtime={mtime} size={size}")
    return mtime

def test_read(sock, fid, offset, length, seq, mtime):
    print(f"[READ] fid={fid} offset={offset} length={length}")
    body = struct.pack('!IIIQ', fid, offset, length, 0)  # cached_mtime=0
    body = body.ljust(256, b'\x00')
    pkt  = struct.pack('!IIB', 1, seq, 0) + body         # type=READ(1)
    resp = send_recv(sock, pkt)
    hdr_type, rseq, is_resp = struct.unpack_from('!IIB', resp, 0)
    rfid, roffset, rlength, rmtime = struct.unpack_from('!IIIQ', resp, 9)
    payload = resp[9 + 256:]
    print(f"  type={hdr_type} fid={rfid} offset={roffset} length={rlength} mtime={rmtime}")
    print(f"  data={payload[:rlength]}")

if __name__ == '__main__':
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(3)

    fid = test_open(sock, '/test.txt')
    mtime = test_write(sock, fid, 0, b'hello world', seq=2)
    test_read(sock, fid, 0, 11, 3, mtime)

    sock.close()
