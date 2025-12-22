package com.xmas.pixelblaze;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * Bridge to Pixelblaze using Python for WebSocket communication.
 * Workaround for Java WebSocket issues on macOS.
 */
public class PixelblazePythonBridge implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(PixelblazePythonBridge.class);

    private final String host;
    private final int numPixels;
    private Process pythonProcess;
    private PrintWriter toProcess;
    private BufferedReader fromProcess;
    private boolean connected = false;

    public PixelblazePythonBridge(String host, int numPixels) {
        this.host = host;
        this.numPixels = numPixels;
    }

    /**
     * Start the Python bridge process.
     */
    public boolean connect() {
        try {
            // Create Python script
            String script = createPythonScript();
            File scriptFile = File.createTempFile("pixelblaze_bridge", ".py");
            scriptFile.deleteOnExit();
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(scriptFile))) {
                writer.print(script);
            }

            logger.info("Starting Python bridge to Pixelblaze at {}:81", host);
            
            ProcessBuilder pb = new ProcessBuilder("python3", scriptFile.getAbsolutePath(), host, String.valueOf(numPixels));
            pb.redirectErrorStream(true);
            pythonProcess = pb.start();
            
            toProcess = new PrintWriter(pythonProcess.getOutputStream(), true);
            fromProcess = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()));
            
            // Wait for ready message
            String response = fromProcess.readLine();
            if (response != null && response.startsWith("READY")) {
                connected = true;
                logger.info("Python bridge connected to Pixelblaze");
                return true;
            } else {
                logger.error("Unexpected response from bridge: {}", response);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Failed to start Python bridge: {}", e.getMessage());
            return false;
        }
    }

    public boolean isConnected() {
        return connected && pythonProcess != null && pythonProcess.isAlive();
    }

    /**
     * Light a single pixel (all others off).
     */
    public void lightOnlyPixel(int index, int r, int g, int b) {
        sendCommand(String.format("LIGHT %d %d %d %d", index, r, g, b));
    }

    /**
     * Clear all pixels.
     */
    public void clearAll() {
        sendCommand("CLEAR");
    }

    /**
     * Light all pixels with a single color.
     */
    public void lightAll(int r, int g, int b) {
        sendCommand(String.format("ALL %d %d %d", r, g, b));
    }

    /**
     * Light all pixels white.
     */
    public void allWhite() {
        lightAll(255, 255, 255);
    }

    /**
     * Show blue LED at index 0.
     */
    public void showBlueStart() {
        lightOnlyPixel(0, 0, 0, 255);
    }

    /**
     * Show red LED at last index.
     */
    public void showRedEnd() {
        lightOnlyPixel(numPixels - 1, 255, 0, 0);
    }

    /**
     * Show green LED at specified index.
     */
    public void showGreen(int index) {
        lightOnlyPixel(index, 0, 255, 0);
    }

    private void sendCommand(String command) {
        if (!isConnected()) {
            logger.warn("Not connected to Pixelblaze");
            return;
        }
        
        try {
            toProcess.println(command);
            String response = fromProcess.readLine();
            if (response != null && !response.equals("OK")) {
                logger.warn("Unexpected response: {}", response);
            }
        } catch (Exception e) {
            logger.error("Failed to send command: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        if (pythonProcess != null) {
            try {
                toProcess.println("QUIT");
                pythonProcess.waitFor(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                // ignore
            }
            pythonProcess.destroyForcibly();
        }
        connected = false;
    }

    private String createPythonScript() {
        return """
import socket
import struct
import sys
import json

class PixelblazeBridge:
    def __init__(self, host, num_pixels):
        self.host = host
        self.port = 81
        self.num_pixels = num_pixels
        self.sock = None
        
    def connect(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(10)
        self.sock.connect((self.host, self.port))
        
        # WebSocket handshake
        key = "dGhlIHNhbXBsZSBub25jZQ=="
        handshake = (
            f"GET / HTTP/1.1\\r\\n"
            f"Host: {self.host}:{self.port}\\r\\n"
            f"Upgrade: websocket\\r\\n"
            f"Connection: Upgrade\\r\\n"
            f"Sec-WebSocket-Key: {key}\\r\\n"
            f"Sec-WebSocket-Version: 13\\r\\n"
            f"\\r\\n"
        )
        self.sock.send(handshake.encode())
        
        # Read response
        response = b""
        while b"\\r\\n\\r\\n" not in response:
            response += self.sock.recv(1024)
        
        if b"101" not in response:
            raise Exception(f"WebSocket handshake failed: {response[:100]}")
    
    def send_text(self, text):
        # WebSocket text frame
        data = text.encode('utf-8')
        header = bytearray([0x81])  # FIN + text opcode
        length = len(data)
        
        if length <= 125:
            header.append(0x80 | length)  # Mask bit + length
        elif length <= 65535:
            header.append(0x80 | 126)
            header.extend(struct.pack(">H", length))
        else:
            header.append(0x80 | 127)
            header.extend(struct.pack(">Q", length))
        
        # Masking key (required for client->server)
        mask = bytearray([0x12, 0x34, 0x56, 0x78])
        header.extend(mask)
        
        # Mask the payload
        masked = bytearray(len(data))
        for i in range(len(data)):
            masked[i] = data[i] ^ mask[i % 4]
        
        self.sock.send(header + masked)
    
    def set_vars(self, **kwargs):
        # Send setVars command to Pixelblaze
        # Format: {"setVars": {"var1": value1, "var2": value2}}
        cmd = json.dumps({"setVars": kwargs})
        self.send_text(cmd)
        
    def light_pixel(self, index, r, g, b):
        # mode=1 for single LED, convert RGB 0-255 to 0-1
        self.set_vars(mode=1, ledIndex=index, r=r/255.0, g=g/255.0, b=b/255.0)
        
    def light_all(self, r, g, b):
        # mode=2 for all LEDs same color
        self.set_vars(mode=2, r=r/255.0, g=g/255.0, b=b/255.0)
        
    def clear(self):
        # mode=0 for all off
        self.set_vars(mode=0)
        
    def close(self):
        if self.sock:
            try:
                # Turn off before closing
                self.clear()
                # Send close frame
                close_frame = bytearray([0x88, 0x82, 0x12, 0x34, 0x56, 0x78, 0x15, 0x6B])
                self.sock.send(close_frame)
            except:
                pass
            self.sock.close()

def main():
    if len(sys.argv) < 3:
        print("ERROR Usage: python script.py <host> <num_pixels>", flush=True)
        return
        
    host = sys.argv[1]
    num_pixels = int(sys.argv[2])
    
    try:
        bridge = PixelblazeBridge(host, num_pixels)
        bridge.connect()
        print("READY", flush=True)
        
        while True:
            line = sys.stdin.readline().strip()
            if not line:
                continue
                
            parts = line.split()
            cmd = parts[0]
            
            if cmd == "QUIT":
                break
            elif cmd == "CLEAR":
                bridge.clear()
                print("OK", flush=True)
            elif cmd == "ALL" and len(parts) == 4:
                r = int(parts[1])
                g = int(parts[2])
                b = int(parts[3])
                bridge.light_all(r, g, b)
                print("OK", flush=True)
            elif cmd == "LIGHT" and len(parts) == 5:
                index = int(parts[1])
                r = int(parts[2])
                g = int(parts[3])
                b = int(parts[4])
                bridge.light_pixel(index, r, g, b)
                print("OK", flush=True)
            else:
                print(f"ERROR Unknown command: {line}", flush=True)
                
        bridge.close()
        
    except Exception as e:
        print(f"ERROR {e}", flush=True)
        sys.exit(1)

if __name__ == "__main__":
    main()
""";
    }
}

