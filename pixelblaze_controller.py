#!/usr/bin/env python3
"""
Pixelblaze Controller - Interactive stdin/stdout interface
Used by Java LedMappingSession to control LEDs
"""
import socket, struct, json, sys, time

HOST = "192.168.86.65"
PORT = 81

sock = None

def connect():
    global sock
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.connect((HOST, PORT))
    key = "dGhlIHNhbXBsZSBub25jZQ=="
    sock.send(f"GET / HTTP/1.1\r\nHost: {HOST}:{PORT}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n".encode())
    response = b""
    while b"\r\n\r\n" not in response:
        response += sock.recv(1024)
    if b"101" not in response:
        raise Exception("WebSocket failed")

def send_vars(**kwargs):
    data = json.dumps({"setVars": kwargs}).encode('utf-8')
    header = bytearray([0x81, 0x80 | len(data), 0x12, 0x34, 0x56, 0x78])
    masked = bytearray(data[i] ^ [0x12, 0x34, 0x56, 0x78][i % 4] for i in range(len(data)))
    sock.send(header + masked)
    time.sleep(0.3)

connect()
print("READY", flush=True)

for line in sys.stdin:
    cmd = line.strip().split()
    if not cmd:
        continue
    
    if cmd[0] == "quit":
        break
    elif cmd[0] == "off":
        send_vars(mode=0, r=0, g=0, b=0)
    elif cmd[0] == "blue":
        send_vars(mode=1, ledIndex=0, r=0, g=0, b=1.0)
    elif cmd[0] == "red":
        send_vars(mode=1, ledIndex=255, r=1.0, g=0, b=0)
    elif cmd[0] == "green" and len(cmd) > 1:
        send_vars(mode=1, ledIndex=int(cmd[1]), r=0, g=1.0, b=0)
    elif cmd[0] == "pixel" and len(cmd) > 1:
        # Light a single pixel white at specified brightness (0-100)
        idx = int(cmd[1])
        brightness = float(cmd[2]) / 100.0 if len(cmd) > 2 else 1.0
        send_vars(mode=1, ledIndex=idx, r=brightness, g=brightness, b=brightness)
    elif cmd[0] == "white":
        send_vars(mode=2, r=1.0, g=1.0, b=1.0)
    
    print("OK", flush=True)

sock.close()

