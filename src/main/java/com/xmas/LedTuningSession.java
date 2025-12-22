package com.xmas;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.Graphics;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.*;
import java.util.*;

/**
 * Interactive LED Tuning Session
 * 
 * Usage: ./gradlew bootRun --args="--tune-led <led_index>"
 * 
 * Lights a single LED and lets you click on it to see the pixel values.
 * Use arrow keys to change LED index.
 */
@Component
@Order(0)
public class LedTuningSession implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LedTuningSession.class);

    private volatile boolean running = true;
    private volatile int currentLedIndex = 0;
    private volatile int totalLeds = 256;
    private volatile Point clickedPoint = null;
    private volatile boolean ledOn = true;
    
    // Pixelblaze connection
    private Process pixelblazeProcess;
    private PrintWriter pixelblazeWriter;
    private BufferedReader pixelblazeReader;

    // For analysis
    private Mat lastFrame = null;
    private List<Point> clickedPoints = new ArrayList<>();

    @Override
    public void run(String... args) throws Exception {
        if (args.length < 1 || !"--tune-led".equals(args[0])) {
            return;
        }

        if (args.length > 1) {
            currentLedIndex = Integer.parseInt(args[1]);
        }

        logger.info("=== LED TUNING SESSION ===");
        logger.info("Starting at LED index: {}", currentLedIndex);
        logger.info("Controls:");
        logger.info("  LEFT/RIGHT - Change LED index");
        logger.info("  SPACE - Toggle LED on/off");
        logger.info("  CLICK - Analyze pixel at location");
        logger.info("  Q/ESC - Quit");

        startSession();
        System.exit(0);
    }

    private void startSession() {
        if (!startPixelblazeController()) {
            logger.error("Failed to start Pixelblaze controller");
            return;
        }

        VideoCapture capture = new VideoCapture();
        if (!capture.open(0)) {
            logger.error("Failed to open webcam");
            stopPixelblazeController();
            return;
        }

        capture.set(Videoio.CAP_PROP_FRAME_WIDTH, 1280);
        capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, 720);

        int width = (int) capture.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int height = (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        logger.info("Webcam: {}x{}", width, height);

        JFrame frame = new JFrame("LED Tuning - Click on the LED");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        ImagePanel imagePanel = new ImagePanel();
        frame.add(imagePanel);
        frame.setSize(width + 50, height + 100);
        frame.setVisible(true);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                running = false;
            }
        });

        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_Q, KeyEvent.VK_ESCAPE -> running = false;
                    case KeyEvent.VK_SPACE -> toggleLed();
                    case KeyEvent.VK_LEFT -> changeLed(-1);
                    case KeyEvent.VK_RIGHT -> changeLed(1);
                    case KeyEvent.VK_UP -> changeLed(10);
                    case KeyEvent.VK_DOWN -> changeLed(-10);
                }
            }
        });

        imagePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clickedPoint = new Point(e.getX(), e.getY());
                analyzeClickedPoint();
            }
        });

        // Light the first LED
        lightCurrentLed();

        Mat frameMat = new Mat();
        
        while (running) {
            if (!capture.read(frameMat)) {
                continue;
            }

            lastFrame = frameMat.clone();
            Mat display = processFrame(frameMat);
            
            frame.setTitle(String.format("LED Tuning - Index: %d - %s - Click on LED to analyze", 
                    currentLedIndex, ledOn ? "ON" : "OFF"));

            BufferedImage img = matToBufferedImage(display);
            imagePanel.setImage(img);
            imagePanel.repaint();

            display.release();

            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                break;
            }
        }

        sendPixelblazeCommand("off");
        frameMat.release();
        if (lastFrame != null) lastFrame.release();
        capture.release();
        frame.dispose();
        stopPixelblazeController();
    }

    private Mat processFrame(Mat frame) {
        Mat display = frame.clone();

        // Draw clicked points and their info
        for (Point p : clickedPoints) {
            Imgproc.circle(display, p, 10, new Scalar(0, 255, 255), 2);
            Imgproc.drawMarker(display, p, new Scalar(0, 255, 255), Imgproc.MARKER_CROSS, 20, 2);
        }

        // Draw current click point
        if (clickedPoint != null) {
            Imgproc.circle(display, clickedPoint, 15, new Scalar(255, 0, 255), 3);
        }

        // Draw overlay
        drawOverlay(display);

        return display;
    }

    private void analyzeClickedPoint() {
        if (lastFrame == null || clickedPoint == null) return;

        int x = (int) clickedPoint.x;
        int y = (int) clickedPoint.y;

        if (x < 0 || x >= lastFrame.cols() || y < 0 || y >= lastFrame.rows()) {
            logger.warn("Click outside frame bounds");
            return;
        }

        // Get BGR value
        double[] bgr = lastFrame.get(y, x);
        
        // Convert to HSV for that pixel region
        Mat roi = lastFrame.submat(
                Math.max(0, y - 10), Math.min(lastFrame.rows(), y + 10),
                Math.max(0, x - 10), Math.min(lastFrame.cols(), x + 10)
        );
        Mat hsvRoi = new Mat();
        Imgproc.cvtColor(roi, hsvRoi, Imgproc.COLOR_BGR2HSV);
        
        // Get center HSV value
        double[] hsv = hsvRoi.get(Math.min(10, hsvRoi.rows()-1), Math.min(10, hsvRoi.cols()-1));

        // Calculate average HSV in region
        Scalar avgHsv = Core.mean(hsvRoi);

        logger.info("=== CLICKED POINT ANALYSIS ===");
        logger.info("Position: ({}, {})", x, y);
        logger.info("BGR: B={:.0f}, G={:.0f}, R={:.0f}", bgr[0], bgr[1], bgr[2]);
        if (hsv != null) {
            logger.info("HSV: H={:.0f}, S={:.0f}, V={:.0f}", hsv[0], hsv[1], hsv[2]);
        }
        logger.info("Avg HSV (10x10 region): H={:.1f}, S={:.1f}, V={:.1f}", avgHsv.val[0], avgHsv.val[1], avgHsv.val[2]);
        
        // Check if saturated (white)
        if (bgr[0] > 200 && bgr[1] > 200 && bgr[2] > 200) {
            logger.info(">>> SATURATED (appears white) <<<");
        }

        // Brightness
        double brightness = (bgr[0] + bgr[1] + bgr[2]) / 3;
        logger.info("Brightness: {:.1f}", brightness);

        clickedPoints.add(new Point(x, y));
        
        hsvRoi.release();
        roi.release();
    }

    private void drawOverlay(Mat display) {
        int y = 30;
        Imgproc.rectangle(display, new Point(10, 10), new Point(450, 160), new Scalar(0, 0, 0), -1);
        Imgproc.rectangle(display, new Point(10, 10), new Point(450, 160), new Scalar(255, 255, 255), 1);

        Imgproc.putText(display, String.format("LED Index: %d / %d", currentLedIndex, totalLeds - 1), 
                new Point(20, y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(0, 255, 0), 2);
        y += 25;

        Imgproc.putText(display, String.format("LED State: %s", ledOn ? "ON" : "OFF"), 
                new Point(20, y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, 
                ledOn ? new Scalar(0, 255, 0) : new Scalar(0, 0, 255), 1);
        y += 22;

        Imgproc.putText(display, "LEFT/RIGHT = change LED, SPACE = toggle", 
                new Point(20, y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, new Scalar(200, 200, 200), 1);
        y += 18;

        Imgproc.putText(display, "UP/DOWN = +/-10 LEDs, CLICK = analyze pixel", 
                new Point(20, y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, new Scalar(200, 200, 200), 1);
        y += 18;

        Imgproc.putText(display, "Q/ESC = quit", 
                new Point(20, y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, new Scalar(200, 200, 200), 1);
        y += 22;

        if (clickedPoint != null) {
            Imgproc.putText(display, String.format("Last click: (%.0f, %.0f)", clickedPoint.x, clickedPoint.y), 
                    new Point(20, y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, new Scalar(255, 255, 0), 1);
        }
    }

    private void toggleLed() {
        ledOn = !ledOn;
        if (ledOn) {
            lightCurrentLed();
        } else {
            sendPixelblazeCommand("off");
        }
        logger.info("LED {} is now {}", currentLedIndex, ledOn ? "ON" : "OFF");
    }

    private void changeLed(int delta) {
        currentLedIndex = Math.max(0, Math.min(totalLeds - 1, currentLedIndex + delta));
        clickedPoints.clear();
        if (ledOn) {
            lightCurrentLed();
        }
        logger.info("Changed to LED {}", currentLedIndex);
    }

    private void lightCurrentLed() {
        // Use green for middle LEDs - but since they appear white when saturated,
        // we're testing with whatever color works best
        sendPixelblazeCommand("green " + currentLedIndex);
    }

    private boolean startPixelblazeController() {
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "pixelblaze_controller.py");
            pb.redirectErrorStream(true);
            pixelblazeProcess = pb.start();
            pixelblazeWriter = new PrintWriter(pixelblazeProcess.getOutputStream(), true);
            pixelblazeReader = new BufferedReader(new InputStreamReader(pixelblazeProcess.getInputStream()));

            String response = pixelblazeReader.readLine();
            if ("READY".equals(response)) {
                logger.info("Pixelblaze controller ready");
                return true;
            }
            logger.error("Pixelblaze response: {}", response);
            return false;
        } catch (Exception e) {
            logger.error("Failed to start Pixelblaze controller: {}", e.getMessage());
            return false;
        }
    }

    private void sendPixelblazeCommand(String cmd) {
        if (pixelblazeWriter != null) {
            pixelblazeWriter.println(cmd);
            try {
                String response = pixelblazeReader.readLine();
                if (!"OK".equals(response)) {
                    logger.warn("Pixelblaze response: {}", response);
                }
            } catch (IOException e) {
                logger.error("Error reading Pixelblaze response: {}", e.getMessage());
            }
        }
    }

    private void stopPixelblazeController() {
        if (pixelblazeWriter != null) {
            pixelblazeWriter.println("quit");
        }
        if (pixelblazeProcess != null) {
            pixelblazeProcess.destroyForcibly();
        }
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_3BYTE_BGR;
        if (mat.channels() == 1) {
            type = BufferedImage.TYPE_BYTE_GRAY;
        }
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        mat.get(0, 0, data);
        return image;
    }

    private static class ImagePanel extends JPanel {
        private BufferedImage image;
        public void setImage(BufferedImage image) { this.image = image; }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) g.drawImage(image, 0, 0, null);
        }
    }
}

