package com.xmas.pixelblaze;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Test runner for Pixelblaze LED control.
 * 
 * Usage: 
 *   ./gradlew bootRun --args="--pixelblaze-test <led_count>"
 *   ./gradlew bootRun --args="--pixelblaze-test 256"
 *   ./gradlew bootRun --args="--pixelblaze-blink <index> <color> <led_count>"
 *   ./gradlew bootRun --args="--pixelblaze-blink 0 blue 256"
 *   ./gradlew bootRun --args="--pixelblaze-blink 255 red 256"
 *   ./gradlew bootRun --args="--pixelblaze-sequence <led_count>"
 */
@Component
@Order(0)
public class PixelblazeTestRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(PixelblazeTestRunner.class);

    private static final String PIXELBLAZE_HOST = "192.168.86.65";

    @Override
    public void run(String... args) throws Exception {
        if (args.length < 1) return;

        switch (args[0]) {
            case "--pixelblaze-test" -> runConnectionTest(args);
            case "--pixelblaze-blink" -> runBlinkTest(args);
            case "--pixelblaze-sequence" -> runSequenceTest(args);
            case "--pixelblaze-all-white" -> runAllWhiteTest(args);
            case "--pixelblaze-off" -> runOffTest(args);
            default -> { return; }
        }
        
        System.exit(0);
    }

    private void runConnectionTest(String[] args) {
        int ledCount = args.length > 1 ? Integer.parseInt(args[1]) : 256;
        
        logger.info("=== PIXELBLAZE CONNECTION TEST (Python Bridge) ===");
        logger.info("Host: {}", PIXELBLAZE_HOST);
        logger.info("LED count: {}", ledCount);

        try (PixelblazePythonBridge controller = new PixelblazePythonBridge(PIXELBLAZE_HOST, ledCount)) {
            if (!controller.connect()) {
                logger.error("Failed to connect to Pixelblaze!");
                return;
            }

            logger.info("Connected! Running LED test...");
            
            // Test blue (first pixel)
            logger.info("Lighting BLUE (pixel 0)...");
            controller.showBlueStart();
            Thread.sleep(1000);
            
            // Test red (last pixel)
            logger.info("Lighting RED (pixel {})...", ledCount - 1);
            controller.showRedEnd();
            Thread.sleep(1000);
            
            // Test green (middle pixel)
            int middle = ledCount / 2;
            logger.info("Lighting GREEN (pixel {})...", middle);
            controller.showGreen(middle);
            Thread.sleep(1000);
            
            // Clear
            logger.info("Clearing all pixels...");
            controller.clearAll();
            Thread.sleep(500);
            
            logger.info("Test complete!");
        } catch (Exception e) {
            logger.error("Test failed: {}", e.getMessage(), e);
        }
    }

    private void runBlinkTest(String[] args) {
        if (args.length < 4) {
            logger.error("Usage: --pixelblaze-blink <index> <color> <led_count>");
            logger.error("  color: blue, red, green, white");
            return;
        }

        int index = Integer.parseInt(args[1]);
        String color = args[2].toLowerCase();
        int ledCount = Integer.parseInt(args[3]);

        int r = 0, g = 0, b = 0;
        switch (color) {
            case "blue" -> b = 255;
            case "red" -> r = 255;
            case "green" -> g = 255;
            case "white" -> { r = 255; g = 255; b = 255; }
            default -> {
                logger.error("Unknown color: {}. Use blue, red, green, or white.", color);
                return;
            }
        }

        logger.info("=== PIXELBLAZE BLINK TEST (Python Bridge) ===");
        logger.info("Blinking {} LED at index {} (of {} total)", color.toUpperCase(), index, ledCount);

        try (PixelblazePythonBridge controller = new PixelblazePythonBridge(PIXELBLAZE_HOST, ledCount)) {
            if (!controller.connect()) {
                logger.error("Failed to connect to Pixelblaze!");
                return;
            }

            logger.info("Connected! Blinking for 10 seconds...");
            logger.info("Press Ctrl+C to stop");

            // Blink for 10 seconds
            int finalR = r, finalG = g, finalB = b;
            for (int i = 0; i < 20; i++) {
                controller.lightOnlyPixel(index, finalR, finalG, finalB);
                Thread.sleep(250);
                controller.clearAll();
                Thread.sleep(250);
            }

            logger.info("Blink test complete!");
        } catch (Exception e) {
            logger.error("Test failed: {}", e.getMessage(), e);
        }
    }

    private void runAllWhiteTest(String[] args) {
        int ledCount = args.length > 1 ? Integer.parseInt(args[1]) : 256;
        
        logger.info("=== PIXELBLAZE ALL WHITE TEST ===");
        logger.info("Lighting all {} LEDs WHITE", ledCount);

        try (PixelblazePythonBridge controller = new PixelblazePythonBridge(PIXELBLAZE_HOST, ledCount)) {
            if (!controller.connect()) {
                logger.error("Failed to connect to Pixelblaze!");
                return;
            }

            logger.info("Connected! Turning all LEDs WHITE...");
            controller.allWhite();
            logger.info("All LEDs are now WHITE. They will stay on.");
            logger.info("Run --pixelblaze-off to turn them off.");
            
        } catch (Exception e) {
            logger.error("Test failed: {}", e.getMessage(), e);
        }
    }

    private void runOffTest(String[] args) {
        int ledCount = args.length > 1 ? Integer.parseInt(args[1]) : 256;
        
        logger.info("=== PIXELBLAZE OFF ===");
        logger.info("Turning off all {} LEDs", ledCount);

        try (PixelblazePythonBridge controller = new PixelblazePythonBridge(PIXELBLAZE_HOST, ledCount)) {
            if (!controller.connect()) {
                logger.error("Failed to connect to Pixelblaze!");
                return;
            }

            controller.clearAll();
            logger.info("All LEDs are now OFF.");
            
        } catch (Exception e) {
            logger.error("Test failed: {}", e.getMessage(), e);
        }
    }

    private void runSequenceTest(String[] args) {
        int ledCount = args.length > 1 ? Integer.parseInt(args[1]) : 256;
        
        logger.info("=== PIXELBLAZE SEQUENCE TEST ===");
        logger.info("Running detection sequence with {} LEDs", ledCount);
        logger.info("Blue (0) -> Green (1 to {}) -> Red ({})", ledCount - 2, ledCount - 1);

        try (PixelblazeController controller = new PixelblazeController(PIXELBLAZE_HOST, ledCount)) {
            if (!controller.connect()) {
                logger.error("Failed to connect to Pixelblaze!");
                return;
            }

            logger.info("Connected! Starting sequence...");
            
            // Run the sequence with callback
            final Object lock = new Object();
            controller.runDetectionSequence(200, (index, colorName) -> {
                logger.info("Lit LED {} ({})", index, colorName);
                if (index == ledCount - 1) {
                    synchronized (lock) {
                        lock.notify();
                    }
                }
            });
            
            // Wait for sequence to complete
            synchronized (lock) {
                lock.wait(ledCount * 250L + 5000); // timeout
            }
            
            Thread.sleep(1000);
            logger.info("Sequence complete!");
        } catch (Exception e) {
            logger.error("Test failed: {}", e.getMessage(), e);
        }
    }
}

