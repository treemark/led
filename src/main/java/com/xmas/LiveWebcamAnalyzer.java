package com.xmas;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;

/**
 * Live webcam analyzer for debugging LED detection.
 * Shows real-time video with blue/red LED detection overlays.
 * 
 * Usage: ./gradlew bootRun --args="--webcam"
 * 
 * Controls:
 * - Q or ESC: Quit
 * - B: Toggle blue detection display
 * - R: Toggle red detection display  
 * - M: Toggle mask view
 * - +/-: Adjust brightness threshold
 */
@Component
@Order(0)
public class LiveWebcamAnalyzer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LiveWebcamAnalyzer.class);

    // HSV ranges for LED colors
    private Scalar blueLow = new Scalar(100, 100, 100);
    private Scalar blueHigh = new Scalar(130, 255, 255);
    
    private Scalar redLow1 = new Scalar(0, 100, 100);
    private Scalar redHigh1 = new Scalar(10, 255, 255);
    private Scalar redLow2 = new Scalar(160, 100, 100);
    private Scalar redHigh2 = new Scalar(180, 255, 255);

    private static final double MIN_LED_AREA = 10;
    private static final double MAX_LED_AREA = 5000;

    // Display toggles
    private volatile boolean showBlue = true;
    private volatile boolean showRed = true;
    private volatile boolean showMasks = false;
    private volatile boolean running = true;
    
    // Adjustable brightness threshold (V in HSV)
    private volatile int brightnessThreshold = 100;

    @Override
    public void run(String... args) throws Exception {
        if (args.length < 1 || !"--webcam".equals(args[0])) {
            return; // Not a webcam run
        }

        logger.info("=== LIVE WEBCAM LED ANALYZER ===");
        logger.info("Controls:");
        logger.info("  Q/ESC - Quit");
        logger.info("  B - Toggle blue detection");
        logger.info("  R - Toggle red detection");
        logger.info("  M - Toggle mask view");
        logger.info("  +/- - Adjust brightness threshold");
        
        startWebcamCapture();
        
        System.exit(0);
    }

    private void startWebcamCapture() {
        VideoCapture capture = new VideoCapture();
        
        // Try to open the default webcam (index 0)
        if (!capture.open(0)) {
            logger.error("Failed to open webcam at index 0");
            // Try index 1
            if (!capture.open(1)) {
                logger.error("Failed to open webcam at index 1 either");
                return;
            }
        }

        // Set resolution if possible
        capture.set(Videoio.CAP_PROP_FRAME_WIDTH, 1280);
        capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, 720);

        int width = (int) capture.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int height = (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        double fps = capture.get(Videoio.CAP_PROP_FPS);
        
        logger.info("Webcam opened: {}x{} @ {} fps", width, height, fps);

        // Create display window
        JFrame frame = new JFrame("LED Detector - Live Webcam");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        ImagePanel imagePanel = new ImagePanel();
        frame.add(imagePanel);
        frame.setSize(width + 400, height + 50); // Extra space for mask display
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
                    case KeyEvent.VK_Q:
                    case KeyEvent.VK_ESCAPE:
                        running = false;
                        break;
                    case KeyEvent.VK_B:
                        showBlue = !showBlue;
                        logger.info("Blue detection: {}", showBlue ? "ON" : "OFF");
                        break;
                    case KeyEvent.VK_R:
                        showRed = !showRed;
                        logger.info("Red detection: {}", showRed ? "ON" : "OFF");
                        break;
                    case KeyEvent.VK_M:
                        showMasks = !showMasks;
                        logger.info("Mask view: {}", showMasks ? "ON" : "OFF");
                        break;
                    case KeyEvent.VK_PLUS:
                    case KeyEvent.VK_EQUALS:
                        brightnessThreshold = Math.min(255, brightnessThreshold + 10);
                        updateThresholds();
                        break;
                    case KeyEvent.VK_MINUS:
                        brightnessThreshold = Math.max(0, brightnessThreshold - 10);
                        updateThresholds();
                        break;
                }
            }
        });

        Mat frameMat = new Mat();
        long frameCount = 0;
        long startTime = System.currentTimeMillis();

        while (running) {
            if (!capture.read(frameMat)) {
                logger.warn("Failed to read frame");
                continue;
            }

            frameCount++;
            
            // Process frame and create display
            Mat display = processFrame(frameMat);
            
            // Convert to BufferedImage and display
            BufferedImage img = matToBufferedImage(display);
            imagePanel.setImage(img);
            imagePanel.repaint();

            display.release();

            // Calculate and display FPS every second
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= 1000) {
                double currentFps = frameCount * 1000.0 / elapsed;
                frame.setTitle(String.format("LED Detector - Live Webcam (%.1f FPS)", currentFps));
                frameCount = 0;
                startTime = System.currentTimeMillis();
            }

            // Small delay to prevent 100% CPU usage
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }

        frameMat.release();
        capture.release();
        frame.dispose();
        
        logger.info("Webcam capture ended");
    }

    private void updateThresholds() {
        // Update the V (brightness) component of the HSV thresholds
        blueLow = new Scalar(100, 100, brightnessThreshold);
        redLow1 = new Scalar(0, 100, brightnessThreshold);
        redLow2 = new Scalar(160, 100, brightnessThreshold);
        logger.info("Brightness threshold: {}", brightnessThreshold);
    }

    private Mat processFrame(Mat frame) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);

        Mat display;
        
        if (showMasks) {
            // Create side-by-side view: original | blue mask | red mask
            Mat blueMask = new Mat();
            Mat redMask = createRedMask(hsv);
            Core.inRange(hsv, blueLow, blueHigh, blueMask);
            
            // Convert masks to color for display
            Mat blueMaskColor = new Mat();
            Mat redMaskColor = new Mat();
            Imgproc.cvtColor(blueMask, blueMaskColor, Imgproc.COLOR_GRAY2BGR);
            Imgproc.cvtColor(redMask, redMaskColor, Imgproc.COLOR_GRAY2BGR);
            
            // Tint the masks
            Core.multiply(blueMaskColor, new Scalar(1, 0.3, 0.3), blueMaskColor);
            Core.multiply(redMaskColor, new Scalar(0.3, 0.3, 1), redMaskColor);
            
            // Scale down for side panel
            int panelWidth = 200;
            int panelHeight = frame.rows() / 2;
            Mat blueSmall = new Mat();
            Mat redSmall = new Mat();
            Imgproc.resize(blueMaskColor, blueSmall, new Size(panelWidth, panelHeight));
            Imgproc.resize(redMaskColor, redSmall, new Size(panelWidth, panelHeight));
            
            // Create display with side panel
            display = new Mat(frame.rows(), frame.cols() + panelWidth, frame.type());
            frame.copyTo(display.submat(0, frame.rows(), 0, frame.cols()));
            blueSmall.copyTo(display.submat(0, panelHeight, frame.cols(), frame.cols() + panelWidth));
            redSmall.copyTo(display.submat(panelHeight, frame.rows(), frame.cols(), frame.cols() + panelWidth));
            
            // Labels
            Imgproc.putText(display, "BLUE MASK", new Point(frame.cols() + 10, 20),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 255, 255), 1);
            Imgproc.putText(display, "RED MASK", new Point(frame.cols() + 10, panelHeight + 20),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 255, 255), 1);
            
            blueMask.release();
            redMask.release();
            blueMaskColor.release();
            redMaskColor.release();
            blueSmall.release();
            redSmall.release();
        } else {
            display = frame.clone();
        }

        // Detect and draw blue LEDs
        if (showBlue) {
            List<DetectedBlob> blueBlobs = detectBlue(hsv);
            for (DetectedBlob blob : blueBlobs) {
                Imgproc.circle(display, blob.center, 20, new Scalar(255, 0, 0), 3);
                Imgproc.circle(display, blob.center, 25, new Scalar(255, 0, 0), 1);
                String label = String.format("BLUE a=%.0f b=%.0f", blob.area, blob.brightness);
                Imgproc.putText(display, label, new Point(blob.center.x + 30, blob.center.y),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 100, 100), 2);
            }
        }

        // Detect and draw red LEDs
        if (showRed) {
            List<DetectedBlob> redBlobs = detectRed(hsv);
            for (DetectedBlob blob : redBlobs) {
                Imgproc.circle(display, blob.center, 20, new Scalar(0, 0, 255), 3);
                Imgproc.circle(display, blob.center, 25, new Scalar(0, 0, 255), 1);
                String label = String.format("RED a=%.0f b=%.0f", blob.area, blob.brightness);
                Imgproc.putText(display, label, new Point(blob.center.x + 30, blob.center.y),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(100, 100, 255), 2);
            }
        }

        // Draw status overlay
        drawStatusOverlay(display);

        hsv.release();
        return display;
    }

    private void drawStatusOverlay(Mat display) {
        int y = 30;
        int lineHeight = 25;
        
        // Background
        Imgproc.rectangle(display, new Point(10, 10), new Point(350, 150), new Scalar(0, 0, 0), -1);
        Imgproc.rectangle(display, new Point(10, 10), new Point(350, 150), new Scalar(255, 255, 255), 1);

        Imgproc.putText(display, "LED DETECTOR - LIVE", new Point(20, y),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(255, 255, 255), 2);
        y += lineHeight;

        Imgproc.putText(display, String.format("Blue: %s  Red: %s", showBlue ? "ON" : "OFF", showRed ? "ON" : "OFF"),
                new Point(20, y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(200, 200, 200), 1);
        y += lineHeight;

        Imgproc.putText(display, String.format("Brightness threshold: %d", brightnessThreshold),
                new Point(20, y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(200, 200, 200), 1);
        y += lineHeight;

        Imgproc.putText(display, "Q=Quit B=Blue R=Red M=Masks +/-=Brightness",
                new Point(20, y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, new Scalar(150, 150, 150), 1);
        y += lineHeight;
        
        Imgproc.putText(display, String.format("Blue HSV: [100-130, 100-255, %d-255]", brightnessThreshold),
                new Point(20, y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, new Scalar(255, 150, 150), 1);
    }

    private Mat createRedMask(Mat hsv) {
        Mat mask1 = new Mat();
        Mat mask2 = new Mat();
        Mat combined = new Mat();
        
        Core.inRange(hsv, redLow1, redHigh1, mask1);
        Core.inRange(hsv, redLow2, redHigh2, mask2);
        Core.bitwise_or(mask1, mask2, combined);
        
        mask1.release();
        mask2.release();
        
        return combined;
    }

    private List<DetectedBlob> detectBlue(Mat hsv) {
        Mat mask = new Mat();
        Core.inRange(hsv, blueLow, blueHigh, mask);
        List<DetectedBlob> blobs = findBlobs(mask, hsv);
        mask.release();
        return blobs;
    }

    private List<DetectedBlob> detectRed(Mat hsv) {
        Mat mask = createRedMask(hsv);
        List<DetectedBlob> blobs = findBlobs(mask, hsv);
        mask.release();
        return blobs;
    }

    private List<DetectedBlob> findBlobs(Mat mask, Mat hsv) {
        List<DetectedBlob> blobs = new ArrayList<>();
        
        // Apply morphological operations
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        Mat processed = new Mat();
        Imgproc.morphologyEx(mask, processed, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(processed, processed, Imgproc.MORPH_CLOSE, kernel);
        kernel.release();

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(processed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            
            if (area >= MIN_LED_AREA && area <= MAX_LED_AREA) {
                org.opencv.imgproc.Moments moments = Imgproc.moments(contour);
                if (moments.m00 > 0) {
                    double cx = moments.m10 / moments.m00;
                    double cy = moments.m01 / moments.m00;
                    
                    // Get brightness
                    double brightness = 0;
                    int px = (int) Math.round(cx);
                    int py = (int) Math.round(cy);
                    if (px >= 0 && px < hsv.cols() && py >= 0 && py < hsv.rows()) {
                        double[] hsvValues = hsv.get(py, px);
                        if (hsvValues != null && hsvValues.length >= 3) {
                            brightness = hsvValues[2];
                        }
                    }
                    
                    blobs.add(new DetectedBlob(new Point(cx, cy), area, brightness));
                }
            }
            contour.release();
        }
        
        hierarchy.release();
        processed.release();
        
        return blobs;
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

    private static class DetectedBlob {
        Point center;
        double area;
        double brightness;

        DetectedBlob(Point center, double area, double brightness) {
            this.center = center;
            this.area = area;
            this.brightness = brightness;
        }
    }

    private static class ImagePanel extends JPanel {
        private BufferedImage image;

        public void setImage(BufferedImage image) {
            this.image = image;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) {
                g.drawImage(image, 0, 0, null);
            }
        }
    }
}

