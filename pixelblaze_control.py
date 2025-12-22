#!/usr/bin/env python3
"""
Pixelblaze LED Controller
Usage:
    python3 pixelblaze_control.py off              # Turn all LEDs off
    python3 pixelblaze_control.py white            # All LEDs white
    python3 pixelblaze_control.py all <r> <g> <b>  # All LEDs custom color (0-255)
    python3 pixelblaze_control.py led <index> <r> <g> <b>  # Single LED
    python3 pixelblaze_control.py blue             # Blue at index 0
    python3 pixelblaze_control.py red              # Red at index 255
    python3 pixelblaze_control.py green <index>    # Green at specified index
"""

import socket
import struct
import json
import time
import sys

HOST = "192.168.86.65"
PORT = 81

def connect():
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.connect((HOST, PORT))
    
    key = "dGhlIHNhbXBsZSBub25jZQ=="
    handshake = f"GET / HTTP/1.1\r\nHost: {HOST}:{PORT}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n"
    sock.send(handshake.encode())
    
    response = b""
    while b"\r\n\r\n" not in response:
        response += sock.recv(1024)
    
    if b"101" not in response:
        raise Exception("WebSocket handshake failed")
    
    return sock

def send_vars(sock, **kwargs):
    data = json.dumps({"setVars": kwargs}).encode('utf-8')
    header = bytearray([0x81, 0x80 | len(data), 0x12, 0x34, 0x56, 0x78])
    masked = bytearray(data[i] ^ [0x12, 0x34, 0x56, 0x78][i % 4] for i in range(len(data)))
    sock.send(header + masked)
    time.sleep(0.5)  # Important: wait before closing

def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    
    cmd = sys.argv[1].lower()
    sock = connect()
    
    try:
        if cmd == "off":
            send_vars(sock, mode=0, r=0, g=0, b=0)
            print("LEDs OFF")
        
        elif cmd == "white":
            send_vars(sock, mode=2, r=1.0, g=1.0, b=1.0)
            print("All LEDs WHITE")
        
        elif cmd == "all" and len(sys.argv) == 5:
            r, g, b = int(sys.argv[2])/255, int(sys.argv[3])/255, int(sys.argv[4])/255
            send_vars(sock, mode=2, r=r, g=g, b=b)
            print(f"All LEDs RGB({sys.argv[2]},{sys.argv[3]},{sys.argv[4]})")
        
        elif cmd == "led" and len(sys.argv) == 6:
            idx = int(sys.argv[2])
            r, g, b = int(sys.argv[3])/255, int(sys.argv[4])/255, int(sys.argv[5])/255
            send_vars(sock, mode=1, ledIndex=idx, r=r, g=g, b=b)
            print(f"LED {idx} = RGB({sys.argv[3]},{sys.argv[4]},{sys.argv[5]})")
        
        elif cmd == "blue":
            send_vars(sock, mode=1, ledIndex=0, r=0, g=0, b=1.0)
            print("BLUE at index 0")
        
        elif cmd == "red":
            send_vars(sock, mode=1, ledIndex=255, r=1.0, g=0, b=0)
            print("RED at index 255")
        
        elif cmd == "green" and len(sys.argv) == 3:
            idx = int(sys.argv[2])
            send_vars(sock, mode=1, ledIndex=idx, r=0, g=1.0, b=0)
            print(f"GREEN at index {idx}")
        
        else:
            print(__doc__)
    
    finally:
        sock.close()

if __name__ == "__main__":
    main()

