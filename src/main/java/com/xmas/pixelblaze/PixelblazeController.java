package com.xmas.pixelblaze;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller for Pixelblaze LED controller via WebSocket.
 * 
 * Pixelblaze WebSocket API:
 * - Connect to ws://<ip>/ws
 * - Send JSON for pattern control and variables
 * - Send binary data for direct pixel control (frame data)
 * 
 * Binary frame format:
 * - First byte: frame type (0x01 = preview frame, others for different uses)
 * - Remaining bytes: RGB data (3 bytes per pixel)
 */
public class PixelblazeController implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(PixelblazeController.class);

    private final String host;
    private final int numPixels;
    private WebSocket webSocket;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    
    // Current pixel state (RGB values, 3 bytes per pixel)
    private final byte[] pixelData;

    public PixelblazeController(String host, int numPixels) {
        this.host = host;
        this.numPixels = numPixels;
        this.pixelData = new byte[numPixels * 3];
    }

    /**
     * Connect to the Pixelblaze websocket.
     * Tries port 81 first (standard Pixelblaze WS port), then port 80/ws.
     */
    public boolean connect() {
        logger.info("Attempting to connect to Pixelblaze at {}", host);
        
        // Try port 81 first (standard Pixelblaze websocket port)
        logger.info("Trying ws://{}:81/", host);
        if (tryConnect("ws://" + host + ":81/")) {
            return true;
        }
        
        // Try port 80 with /ws path
        logger.info("Trying ws://{}/ws", host);
        if (tryConnect("ws://" + host + "/ws")) {
            return true;
        }
        
        // Try port 80 without path
        logger.info("Trying ws://{}:80/", host);
        if (tryConnect("ws://" + host + ":80/")) {
            return true;
        }
        
        logger.error("Failed to connect to Pixelblaze on any port");
        return false;
    }
    
    public  boolean tryConnect(String wsUrl) {
        try {
            // Force IPv4 to avoid "No route to host" issues on some systems
            System.setProperty("java.net.preferIPv4Stack", "true");
            
            CountDownLatch connectLatch = new CountDownLatch(1);
            AtomicBoolean success = new AtomicBoolean(false);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
            
            CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(3))
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            logger.info("WebSocket connected to Pixelblaze at {}", wsUrl);
                            connected.set(true);
                            success.set(true);
                            connectLatch.countDown();
                            WebSocket.Listener.super.onOpen(webSocket);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            logger.debug("Received text: {}", data);
                            return WebSocket.Listener.super.onText(webSocket, data, last);
                        }

                        @Override
                        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                            logger.debug("Received binary data: {} bytes", data.remaining());
                            return WebSocket.Listener.super.onBinary(webSocket, data, last);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            logger.info("WebSocket closed: {} - {}", statusCode, reason);
                            connected.set(false);
                            connectLatch.countDown();
                            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            logger.debug("WebSocket error on {}: {}", wsUrl, error.getMessage());
                            connected.set(false);
                            connectLatch.countDown();
                            WebSocket.Listener.super.onError(webSocket, error);
                        }
                    });

            this.webSocket = wsFuture.get(5, TimeUnit.SECONDS);
            connectLatch.await(3, TimeUnit.SECONDS);
            
            if (success.get()) {
                logger.info("Successfully connected to Pixelblaze");
                // Don't clear on connect - let caller decide
                return true;
            }
            
            return false;

        } catch (Exception e) {
            logger.warn("Connection attempt to {} failed: {}", wsUrl, e.getMessage());
            return false;
        }
    }

    /**
     * Check if connected.
     */
    public boolean isConnected() {
        return connected.get() && webSocket != null;
    }

    /**
     * Set a single pixel to a color.
     * 
     * @param index pixel index (0-based)
     * @param r red (0-255)
     * @param g green (0-255)
     * @param b blue (0-255)
     */
    public void setPixel(int index, int r, int g, int b) {
        if (index < 0 || index >= numPixels) {
            logger.warn("Pixel index {} out of range (0-{})", index, numPixels - 1);
            return;
        }
        
        int offset = index * 3;
        pixelData[offset] = (byte) (r & 0xFF);
        pixelData[offset + 1] = (byte) (g & 0xFF);
        pixelData[offset + 2] = (byte) (b & 0xFF);
    }

    /**
     * Clear a single pixel (set to black).
     */
    public void clearPixel(int index) {
        setPixel(index, 0, 0, 0);
    }

    /**
     * Clear all pixels.
     */
    public void clearAll() {
        java.util.Arrays.fill(pixelData, (byte) 0);
        // Use setVars to turn off all LEDs (mode=0)
        setVars(0, 0, 0, 0, 0);
    }

    /**
     * Send the current pixel data to the Pixelblaze.
     * Uses the binary preview frame format.
     */
    public void sendFrame() {
        if (!isConnected()) {
            logger.warn("Not connected to Pixelblaze");
            return;
        }

        try {
            // Binary frame format for Pixelblaze:
            // Byte 0: Frame type (0x01 for preview/direct pixel control)
            // Bytes 1+: RGB data
            byte[] frame = new byte[1 + pixelData.length];
            frame[0] = 0x01; // Preview frame type
            System.arraycopy(pixelData, 0, frame, 1, pixelData.length);

            ByteBuffer buffer = ByteBuffer.wrap(frame);
            webSocket.sendBinary(buffer, true);
            
            logger.debug("Sent frame with {} pixels", numPixels);
            
        } catch (Exception e) {
            logger.error("Failed to send frame: {}", e.getMessage());
        }
    }

    /**
     * Set a pixel and immediately send the frame.
     */
    public void setPixelAndSend(int index, int r, int g, int b) {
        setPixel(index, r, g, b);
        sendFrame();
    }

    /**
     * Light only the specified pixel (all others off).
     * Uses setVars to control the pattern loaded on the Pixelblaze.
     */
    public void lightOnlyPixel(int index, int r, int g, int b) {
        // mode=1 for single LED, convert RGB 0-255 to 0-1 range
        setVars(1, index, r / 255.0, g / 255.0, b / 255.0);
    }

    /**
     * Light all pixels with a single color.
     */
    public void lightAll(int r, int g, int b) {
        // mode=2 for all LEDs same color
        setVars(2, 0, r / 255.0, g / 255.0, b / 255.0);
    }

    /**
     * Set variables on the Pixelblaze pattern.
     * Pattern expects: mode, ledIndex, r, g, b
     */
    private void setVars(int mode, int ledIndex, double r, double g, double b) {
        if (!isConnected()) {
            logger.warn("Not connected to Pixelblaze");
            return;
        }

        try {
            String json = String.format(
                "{\"setVars\":{\"mode\":%d,\"ledIndex\":%d,\"r\":%.4f,\"g\":%.4f,\"b\":%.4f}}",
                mode, ledIndex, r, g, b
            );
            logger.info("Sending: {}", json);
            // Wait for the send to complete
            webSocket.sendText(json, true).join();
            // Small delay to ensure Pixelblaze processes the command
            Thread.sleep(50);
            logger.info("setVars sent: mode={}, ledIndex={}, r={}, g={}, b={}", mode, ledIndex, r, g, b);
        } catch (Exception e) {
            logger.error("Failed to setVars: {}", e.getMessage());
        }
    }

    /**
     * Blink a specific pixel.
     * 
     * @param index pixel index
     * @param r red
     * @param g green
     * @param b blue
     * @param onTimeMs on time in milliseconds
     * @param offTimeMs off time in milliseconds
     * @param count number of blinks (0 = infinite until stopped)
     */
    public void blinkPixel(int index, int r, int g, int b, int onTimeMs, int offTimeMs, int count) {
        Thread blinkThread = new Thread(() -> {
            int blinks = 0;
            while ((count == 0 || blinks < count) && isConnected()) {
                // On
                lightOnlyPixel(index, r, g, b);
                sleep(onTimeMs);
                
                // Off
                clearAll();
                sleep(offTimeMs);
                
                blinks++;
            }
        }, "Blink-" + index);
        blinkThread.setDaemon(true);
        blinkThread.start();
    }

    /**
     * Light pixel 0 as blue (start marker).
     */
    public void showBlueStart() {
        lightOnlyPixel(0, 0, 0, 255);
    }

    /**
     * Light the last pixel as red (end marker).
     */
    public void showRedEnd() {
        lightOnlyPixel(numPixels - 1, 255, 0, 0);
    }

    /**
     * Light a specific pixel as green (sequence marker).
     */
    public void showGreen(int index) {
        lightOnlyPixel(index, 0, 255, 0);
    }

    /**
     * Run the LED detection sequence:
     * 1. Blink blue at index 0
     * 2. Blink green at each index from 1 to n-2
     * 3. Blink red at index n-1
     * 
     * @param blinkTimeMs time for each blink (on + off)
     * @param callback called with each LED index as it's lit
     */
    public void runDetectionSequence(int blinkTimeMs, LedSequenceCallback callback) {
        Thread sequenceThread = new Thread(() -> {
            logger.info("Starting LED detection sequence with {} pixels", numPixels);
            
            int halfBlink = blinkTimeMs / 2;
            
            // Blue LED (index 0) - blink a few times
            for (int i = 0; i < 3 && isConnected(); i++) {
                lightOnlyPixel(0, 0, 0, 255);
                sleep(halfBlink);
                clearAll();
                sleep(halfBlink);
            }
            if (callback != null) callback.onLedLit(0, "blue");
            
            // Green LEDs (indices 1 to n-2)
            for (int idx = 1; idx < numPixels - 1 && isConnected(); idx++) {
                lightOnlyPixel(idx, 0, 255, 0);
                sleep(halfBlink);
                clearAll();
                sleep(halfBlink);
                if (callback != null) callback.onLedLit(idx, "green");
            }
            
            // Red LED (last index) - blink a few times
            for (int i = 0; i < 3 && isConnected(); i++) {
                lightOnlyPixel(numPixels - 1, 255, 0, 0);
                sleep(halfBlink);
                clearAll();
                sleep(halfBlink);
            }
            if (callback != null) callback.onLedLit(numPixels - 1, "red");
            
            logger.info("LED detection sequence complete");
            
        }, "LED-Sequence");
        sequenceThread.setDaemon(true);
        sequenceThread.start();
    }

    /**
     * Send a JSON command to set a variable in the current pattern.
     */
    public void setVariable(String name, double value) {
        if (!isConnected()) {
            logger.warn("Not connected to Pixelblaze");
            return;
        }

        try {
            String json = String.format("{\"setVars\":{\"%s\":%f}}", name, value);
            webSocket.sendText(json, true);
            logger.debug("Set variable {} = {}", name, value);
        } catch (Exception e) {
            logger.error("Failed to set variable: {}", e.getMessage());
        }
    }

    /**
     * Send a JSON command to the Pixelblaze.
     */
    public void sendCommand(String json) {
        if (!isConnected()) {
            logger.warn("Not connected to Pixelblaze");
            return;
        }

        try {
            webSocket.sendText(json, true);
            logger.debug("Sent command: {}", json);
        } catch (Exception e) {
            logger.error("Failed to send command: {}", e.getMessage());
        }
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        if (webSocket != null) {
            try {
                // Don't clear LEDs on close - let them stay in their current state
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "closing");
                logger.info("Pixelblaze connection closed");
            } catch (Exception e) {
                logger.debug("Error closing websocket: {}", e.getMessage());
            }
        }
        connected.set(false);
    }

    /**
     * Callback interface for LED sequence events.
     */
    public interface LedSequenceCallback {
        void onLedLit(int index, String color);
    }
}

