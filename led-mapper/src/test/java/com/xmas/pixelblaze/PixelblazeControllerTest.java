package com.xmas.pixelblaze;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Integration tests for PixelblazeController.tryConnect method.
 * These tests attempt actual network connections to 192.168.86.65
 */
public class PixelblazeControllerTest {

    private static final String PIXELBLAZE_HOST = "192.168.86.65";
    private static final int NUM_PIXELS = 100;

    private PixelblazeController controller;

    @Before
    public void setUp() {
        controller = new PixelblazeController(PIXELBLAZE_HOST, NUM_PIXELS);
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.close();
        }
    }

    @Test
    public void testTryConnectPort80() {
        String wsUrl = "ws://" + PIXELBLAZE_HOST + ":80/";
        System.out.println("Testing connection to: " + wsUrl);

        boolean connected = controller.tryConnect(wsUrl);

        System.out.println("Port 80 connection result: " + connected);
        // Just log the result - connection may or may not succeed depending on device config
        if (connected) {
            assertTrue("Should be connected after successful tryConnect", controller.isConnected());
        } else {
            throw new RuntimeException("Failed to connect to Pixelblaze on port 80!");
        }
    }

    @Test
    public void testTryConnectPort81() {
        String wsUrl = "ws://" + PIXELBLAZE_HOST + ":81/";
        System.out.println("Testing connection to: " + wsUrl);

        boolean connected = controller.tryConnect(wsUrl);

        System.out.println("Port 81 connection result: " + connected);
        // Just log the result - connection may or may not succeed depending on device config
        if (connected) {
            assertTrue("Should be connected after successful tryConnect", controller.isConnected());
        } else {
            throw new RuntimeException("Failed to connect to Pixelblaze on port 81!");
        }
    }

    @Test
    public void testTryConnectPort80WithWsPath() {
        String wsUrl = "ws://" + PIXELBLAZE_HOST + ":80/ws";
        System.out.println("Testing connection to: " + wsUrl);

        boolean connected = controller.tryConnect(wsUrl);

        System.out.println("Port 80/ws connection result: " + connected);
        // Just log the result - connection may or may not succeed depending on device config
        if (connected) {
            assertTrue("Should be connected after successful tryConnect", controller.isConnected());
        } else {
            throw new RuntimeException("Failed to connect to Pixelblaze on port 80/ws!");
        }
    }
}

