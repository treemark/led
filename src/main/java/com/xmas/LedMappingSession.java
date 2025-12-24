package com.xmas;

import org.opencv.core.*;
import org.opencv.core.MatOfPoint2f;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.xmas.pixelblaze.PixelblazeController;

import javax.swing.*;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.*;
import java.util.*;

/**
 * LED Mapping with Brightness Stepping
 * 
 * Strategy:
 * 1. Capture baseline frame (all LEDs off)
 * 2. Turn on LED at 100% brightness
 * 3. Try to detect a circular blob
 * 4. If detection fails, reduce brightness by 10% and retry
 * 5. Continue until detection succeeds or brightness hits 1%
 * 
 * Usage: ./gradlew bootRun --args="--map-leds <led_count>"
 */
@Component
@Order(0)
public class LedMappingSession implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LedMappingSession.class);

    // Detection parameters
    private static final int DIFF_THRESHOLD = 25;      // Min brightness change to detect
    private static final double MIN_AREA = 5;          // Ignore tiny noise
    private static final int SETTLE_FRAMES = 6;        // Frames to wait for camera
    private static final int CONFIRM_FRAMES = 3;       // Frames to confirm detection
    
    // Configuration from application.properties
    @Value("${pixelblaze.host:192.168.86.65}")
    private String pixelblazeHost;
    
    @Value("${pixelblaze.led-count:300}")
    private int defaultLedCount;
    
    @Value("${pixelblaze.brightness:50}")
    private int defaultBrightness;
    
    // Brightness stepping - configurable start, increase until we see the LED
    private int brightnessStart;
    private static final int BRIGHTNESS_MAX = 100;
    private static final int BRIGHTNESS_STEP = 1;

    private volatile boolean running = true;
    private volatile boolean mappingActive = false;
    private volatile int currentLedIndex = -1;
    private final Map<Integer, Point> detectedPositions = Collections.synchronizedMap(new LinkedHashMap<>());
    private int totalLeds;
    
    // State machine
    private enum State { IDLE, CAPTURING_BASELINE, WAITING_FOR_LED, CONFIRMING }
    private State state = State.IDLE;
    private Mat baselineFrame = null;
    private int frameCounter = 0;
    private int currentBrightness;
    private List<Point> candidatePoints = new ArrayList<>();
    private volatile boolean saveDebugFrame = false;
    
    // Maximum expected distance between consecutive LEDs (in pixels)
    // Looking at correct detections: LEDs 0-7 span X=553 to X=439 = ~114px over 7 LEDs = ~16px each
    // Allow 50px for normal spacing to reject false positives
    private static final double MAX_LED_DISTANCE = 50.0;

    private PixelblazeController pixelblazeController;

    @Override
    public void run(String... args) throws Exception {
        if (args.length < 1 || !"--map-leds".equals(args[0])) {
            return;
        }

        // Use defaults from application.properties, allow command-line overrides
        totalLeds = defaultLedCount;
        brightnessStart = defaultBrightness;
        
        if (args.length > 1) {
            totalLeds = Integer.parseInt(args[1]);
        }
        
        if (args.length > 2) {
            brightnessStart = Integer.parseInt(args[2]);
        }
        brightnessStart = Math.max(1, Math.min(BRIGHTNESS_MAX, brightnessStart));
        currentBrightness = brightnessStart;

        logger.info("=== LED MAPPING with BRIGHTNESS STEPPING ===");
        logger.info("Pixelblaze host: {}", pixelblazeHost);
        logger.info("LED count: {}, Brightness: {}% to {}%", totalLeds, brightnessStart, BRIGHTNESS_MAX);
        logger.info("Usage: --map-leds [led_count] [brightness]  (defaults from application.properties)");
        logger.info("Controls: SPACE=Start/Stop, S=Save, R=Reset, Q=Quit");

        startSession();
        System.exit(0);
    }

    private void initBlobDetector() {
        // We use contour-based detection - simply pick the BIGGEST blob
        logger.info("Using contour-based blob detection (picks biggest blob)");
    }

    private void startSession() {
        if (!startPixelblazeController()) {
            logger.error("Failed to start Pixelblaze controller");
            return;
        }

        initBlobDetector();

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

        JFrame frame = new JFrame("LED Mapping");
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
                    case KeyEvent.VK_SPACE -> toggleMapping();
                    case KeyEvent.VK_S -> savePositions();
                    case KeyEvent.VK_R -> resetMapping();
                    case KeyEvent.VK_D -> saveDebugFrame = true;  // Save next frame for analysis
                }
            }
        });

        sendPixelblazeCommand("off");

        Mat frameMat = new Mat();
        
        while (running) {
            if (!capture.read(frameMat)) {
                continue;
            }

            Mat display = processFrame(frameMat);
            
            String status = String.format("LED %d @ %d%% | %d/%d detected", 
                    currentLedIndex, currentBrightness, detectedPositions.size(), totalLeds);
            frame.setTitle("LED Mapping - " + status);

            BufferedImage img = matToBufferedImage(display);
            imagePanel.setImage(img);
            imagePanel.repaint();

            display.release();

            try {
                Thread.sleep(33);
            } catch (InterruptedException e) {
                break;
            }
        }

        sendPixelblazeCommand("off");
        frameMat.release();
        if (baselineFrame != null) baselineFrame.release();
        capture.release();
        frame.dispose();
        stopPixelblazeController();

        logger.info("Session ended. Detected {} LED positions.", detectedPositions.size());
    }

    private Mat processFrame(Mat frame) {
        Mat display = frame.clone();

        switch (state) {
            case CAPTURING_BASELINE -> {
                frameCounter++;
                Imgproc.putText(display, "Capturing baseline...", new Point(450, 50),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(0, 255, 255), 2);
                
                if (frameCounter >= SETTLE_FRAMES) {
                    if (baselineFrame != null) baselineFrame.release();
                    baselineFrame = new Mat();
                    Imgproc.cvtColor(frame, baselineFrame, Imgproc.COLOR_BGR2GRAY);
                    
                    // Turn on LED at current brightness
                    lightCurrentLed();
                    state = State.WAITING_FOR_LED;
                    frameCounter = 0;
                    logger.debug("Baseline captured, LED {} at {}%", currentLedIndex, currentBrightness);
                }
            }
            
            case WAITING_FOR_LED -> {
                frameCounter++;
                Imgproc.putText(display, String.format("LED %d @ %d%% - waiting...", currentLedIndex, currentBrightness), 
                        new Point(450, 50), Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(0, 255, 0), 2);
                
                if (frameCounter >= SETTLE_FRAMES) {
                    state = State.CONFIRMING;
                    frameCounter = 0;
                    candidatePoints.clear();
                }
            }
            
            case CONFIRMING -> {
                frameCounter++;
                
                // Try to detect blob (picks biggest one)
                DetectionResult result = detectLedBlob(frame, display);
                if (result.point != null) {
                    candidatePoints.add(result.point);
                }
                
                Imgproc.putText(display, String.format("LED %d @ %d%% - detecting (%d/%d)...", 
                        currentLedIndex, currentBrightness, candidatePoints.size(), CONFIRM_FRAMES), 
                        new Point(450, 50), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(0, 255, 0), 2);
                
                if (frameCounter >= CONFIRM_FRAMES) {
                    Point confirmed = confirmDetection();
                    
                    if (confirmed != null) {
                        // Success! Record position and move to next LED
                        detectedPositions.put(currentLedIndex, confirmed);
                        logger.info("LED {} detected at ({}, {}) @ {}%", 
                                currentLedIndex,
                                String.format("%.1f", confirmed.x), 
                                String.format("%.1f", confirmed.y),
                                currentBrightness);
                        advanceToNextLed();
                    } else {
                        // No consistent detection - try higher brightness
                        currentBrightness += BRIGHTNESS_STEP;
                        
                        if (currentBrightness > BRIGHTNESS_MAX) {
                            // Exhausted all brightness levels - skip this LED
                            logger.warn("LED {} NOT detected at any brightness", currentLedIndex);
                            advanceToNextLed();
                        } else {
                            logger.debug("LED {} trying brightness {}%", currentLedIndex, currentBrightness);
                            lightCurrentLed();
                            state = State.WAITING_FOR_LED;
                            frameCounter = 0;
                            candidatePoints.clear();
                        }
                    }
                }
            }
            
            default -> {
                // IDLE - just show video
            }
        }

        // Draw all detected positions
        for (Map.Entry<Integer, Point> entry : detectedPositions.entrySet()) {
            Point p = entry.getValue();
            Imgproc.circle(display, p, 5, new Scalar(0, 255, 255), -1);
            Imgproc.putText(display, String.valueOf(entry.getKey()), 
                    new Point(p.x + 8, p.y + 4),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.3, new Scalar(0, 255, 255), 1);
        }

        drawOverlay(display);
        
        // Save debug frame if requested (press D)
        if (saveDebugFrame) {
            saveDebugFrame = false;
            String filename = String.format("debug_led_%02d.png", currentLedIndex);
            Imgcodecs.imwrite(filename, display);
            logger.info("Saved debug frame: {}", filename);
        }
        
        return display;
    }

    private static class DetectionResult {
        Point point;
        double area;
        double circularity;
    }

    private static class BlobCandidate {
        Point center;
        double area;
        double circularity;
        double centerBrightness;  // Brightness at center (in diff image)
        double avgBrightness;     // Average brightness of blob
        double totalBrightness;   // Total light output (area * avgBrightness)
        double falloffRatio;      // Center brightness / edge brightness (higher = more LED-like)
        double distanceFromLast;  // Distance from last detected LED
        double distanceFromPredicted; // Distance from predicted position (based on pattern)
        double score;             // Combined score
        MatOfPoint contour;
    }

    private DetectionResult detectLedBlob(Mat frame, Mat display) {
        DetectionResult result = new DetectionResult();
        if (baselineFrame == null) return result;

        Mat gray = new Mat();
        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);

        Mat diff = new Mat();
        Core.absdiff(gray, baselineFrame, diff);

        Mat thresh = new Mat();
        Imgproc.threshold(diff, thresh, DIFF_THRESHOLD, 255, Imgproc.THRESH_BINARY);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_OPEN, kernel);

        // Find contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Analyze all blobs
        List<BlobCandidate> candidates = new ArrayList<>();
        double maxAreaFound = 0;
        
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area < MIN_AREA) continue;
            maxAreaFound = Math.max(maxAreaFound, area);
            
            // Calculate circularity: 1.0 = perfect circle
            double perimeter = Imgproc.arcLength(new MatOfPoint2f(contour.toArray()), true);
            double circularity = (perimeter > 0) ? (4 * Math.PI * area) / (perimeter * perimeter) : 0;
            
                // Get centroid
                org.opencv.imgproc.Moments moments = Imgproc.moments(contour);
                if (moments.m00 > 0) {
                    int cx = (int) (moments.m10 / moments.m00);
                    int cy = (int) (moments.m01 / moments.m00);
                    
                    // Check baseline brightness at this position
                    double baselineBrightness = 0;
                    if (cx >= 0 && cx < baselineFrame.cols() && cy >= 0 && cy < baselineFrame.rows()) {
                        baselineBrightness = baselineFrame.get(cy, cx)[0];
                    }
                    
                    // Don't reject based on baseline - the LED might be near a previously lit position
                    // Instead, we'll use the diff image which already accounts for baseline
                    
                    BlobCandidate candidate = new BlobCandidate();
                    candidate.center = new Point(cx, cy);
                    candidate.area = area;
                    candidate.circularity = Math.min(circularity, 1.0);
                    candidate.contour = contour;
                    
                    // Get brightness at center point in diff image
                    if (cx >= 0 && cx < diff.cols() && cy >= 0 && cy < diff.rows()) {
                        candidate.centerBrightness = diff.get(cy, cx)[0];
                    }
                    
                    // CRITICAL: Get brightness in RAW frame - actual LEDs are VERY bright
                    // Desk reflections are dimmer in the raw image
                    double rawCenterBrightness = 0;
                    if (cx >= 0 && cx < gray.cols() && cy >= 0 && cy < gray.rows()) {
                        rawCenterBrightness = gray.get(cy, cx)[0];
                    }
                    
                    // Calculate average brightness of the blob region
                    Mat mask = Mat.zeros(diff.size(), CvType.CV_8UC1);
                    Imgproc.drawContours(mask, Arrays.asList(contour), 0, new Scalar(255), -1);
                    Scalar meanVal = Core.mean(diff, mask);
                    candidate.avgBrightness = meanVal.val[0];
                    
                    // Also get raw brightness average
                    Scalar rawMeanVal = Core.mean(gray, mask);
                    double rawAvgBrightness = rawMeanVal.val[0];
                    
                    // Total brightness: heavily weight RAW brightness
                    // Real LEDs: center is very bright (200-255)
                    // Reflections: typically dimmer (50-150)
                    // Use power of 2 to make bright LEDs dominate
                    double rawBrightnessMultiplier = Math.pow(rawAvgBrightness / 100.0, 2);
                    candidate.totalBrightness = area * candidate.avgBrightness * rawBrightnessMultiplier;
                    mask.release();
                    
                    logger.debug("Candidate ({},{}): area={}, diffAvg={:.1f}, rawAvg={:.1f}, rawMult={:.2f}, total={:.0f}",
                        cx, cy, (int)area, candidate.avgBrightness, rawAvgBrightness, rawBrightnessMultiplier, candidate.totalBrightness);
                    
                    // Calculate brightness falloff (LED has bright center, dims toward edges)
                    // Sample brightness at edge of blob and compare to center
                    double radius = Math.sqrt(area / Math.PI);
                    double edgeBrightness = 0;
                    int edgeSamples = 0;
                    for (int angle = 0; angle < 360; angle += 45) {
                        int ex = (int)(cx + radius * 0.8 * Math.cos(Math.toRadians(angle)));
                        int ey = (int)(cy + radius * 0.8 * Math.sin(Math.toRadians(angle)));
                        if (ex >= 0 && ex < diff.cols() && ey >= 0 && ey < diff.rows()) {
                            edgeBrightness += diff.get(ey, ex)[0];
                            edgeSamples++;
                        }
                    }
                    if (edgeSamples > 0) {
                        edgeBrightness /= edgeSamples;
                    }
                    // Falloff ratio: higher means center is much brighter than edges (more LED-like)
                    candidate.falloffRatio = (edgeBrightness > 0) ? candidate.centerBrightness / edgeBrightness : 1.0;
                    
                    // No distance filtering - rely purely on blob characteristics
                    candidate.distanceFromLast = 0;
                    candidate.distanceFromPredicted = 0;
                    
                    candidates.add(candidate);
                }
        }

        // Find max values for normalization
        double maxTotalBrightness = 0;
        for (BlobCandidate c : candidates) {
            maxTotalBrightness = Math.max(maxTotalBrightness, c.totalBrightness);
        }
        
        // Calculate scores for each candidate - NO DISTANCE, pure blob characteristics
        for (BlobCandidate c : candidates) {
            // TOTAL BRIGHTNESS: Real LEDs pump out WAY more light than reflections
            // This should be the dominant factor
            double totalBrightnessScore = maxTotalBrightness > 0 ? c.totalBrightness / maxTotalBrightness : 0;
            
            // Size score: larger is better (normalized to max found)
            double sizeScore = maxAreaFound > 0 ? c.area / maxAreaFound : 0;
            
            // Circularity: LEDs are round, reflections are often irregular
            double circScore = c.circularity;
            
            // Combined score - TOTAL BRIGHTNESS IS KING
            // The real LED should have massively more total light output
            c.score = (totalBrightnessScore * 0.70) + (sizeScore * 0.20) + (circScore * 0.10);
            
            // Log candidate details for debugging
            logger.debug("Candidate at ({}, {}): area={}, totalBrightness={}, circ={:.2f}, score={:.3f}",
                (int)c.center.x, (int)c.center.y, (int)c.area, (int)c.totalBrightness, c.circularity, c.score);
        }

        // Sort by score (highest first)
        candidates.sort((a, b) -> Double.compare(b.score, a.score));

        // Pick the highest scoring blob
        BlobCandidate best = candidates.isEmpty() ? null : candidates.get(0);

        // Draw all candidates (sorted by score, index 0 = best)
        for (int i = 0; i < Math.min(candidates.size(), 10); i++) {
            BlobCandidate c = candidates.get(i);
            Scalar color;
            int thickness;
            
            if (i == 0) {
                color = new Scalar(0, 255, 0);  // Green for selected (best score)
                thickness = 3;
            } else {
                color = new Scalar(0, 0, 255);  // Red for others
                thickness = 1;
            }
            
            int radius = (int)Math.sqrt(c.area / Math.PI);
            Imgproc.circle(display, c.center, Math.max(radius, 5), color, thickness);
            
            // Show info for top 5 candidates
            if (i < 5) {
                String info = String.format("#%d S=%.2f T=%.0fk A=%.0f", 
                        i + 1, c.score, c.totalBrightness / 1000, c.area);
                Imgproc.putText(display, info, 
                        new Point(c.center.x + radius + 5, c.center.y + 5),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.35, color, 1);
            }
        }

        if (best != null) {
            result.point = best.center;
            result.area = best.area;
            result.circularity = best.circularity;
            
            // Highlight selected
            Imgproc.circle(display, best.center, 5, new Scalar(0, 255, 0), -1);
        }

        // Show diff in corner
        Mat diffSmall = new Mat();
        Imgproc.resize(thresh, diffSmall, new Size(320, 180));
        Imgproc.cvtColor(diffSmall, diffSmall, Imgproc.COLOR_GRAY2BGR);
        diffSmall.copyTo(display.submat(display.rows() - 190, display.rows() - 10, 
                display.cols() - 330, display.cols() - 10));
        Imgproc.rectangle(display, 
                new Point(display.cols() - 332, display.rows() - 192),
                new Point(display.cols() - 8, display.rows() - 8),
                new Scalar(255, 255, 255), 1);

        // Cleanup
        gray.release();
        diff.release();
        thresh.release();
        kernel.release();
        hierarchy.release();
        diffSmall.release();

        return result;
    }

    private Point confirmDetection() {
        if (candidatePoints.isEmpty()) {
            return null;
        }

        if (candidatePoints.size() < 2) {
            // Need at least 2 detections
            return null;
        }

        // Calculate average
        double sumX = 0, sumY = 0;
        for (Point p : candidatePoints) {
            sumX += p.x;
            sumY += p.y;
        }
        double avgX = sumX / candidatePoints.size();
        double avgY = sumY / candidatePoints.size();

        // Check consistency
        double maxDist = 0;
        for (Point p : candidatePoints) {
            double dist = Math.sqrt(Math.pow(p.x - avgX, 2) + Math.pow(p.y - avgY, 2));
            maxDist = Math.max(maxDist, dist);
        }

        if (maxDist > 15) {
            logger.debug("Inconsistent: spread={}", maxDist);
            return null;
        }

        return new Point(avgX, avgY);
    }

    private void drawOverlay(Mat display) {
        int y = 30;
        Imgproc.rectangle(display, new Point(10, 10), new Point(400, 140), new Scalar(0, 0, 0), -1);
        Imgproc.rectangle(display, new Point(10, 10), new Point(400, 140), new Scalar(255, 255, 255), 1);

        String statusText = mappingActive ? "MAPPING" : "READY";
        Scalar statusColor = mappingActive ? new Scalar(0, 255, 0) : new Scalar(200, 200, 200);
        Imgproc.putText(display, "Status: " + statusText, new Point(20, y),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, statusColor, 2);
        y += 25;

        Imgproc.putText(display, String.format("LED: %d @ %d%% brightness", 
                Math.max(0, currentLedIndex), currentBrightness), new Point(20, y),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(200, 200, 200), 1);
        y += 20;

        Imgproc.putText(display, String.format("Detected: %d / %d", 
                detectedPositions.size(), totalLeds), new Point(20, y),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(200, 200, 200), 1);
        y += 20;

        Imgproc.putText(display, String.format("State: %s", state), new Point(20, y),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, new Scalar(150, 150, 150), 1);
        y += 18;

        Imgproc.putText(display, "SPACE=Start  S=Save  R=Reset  Q=Quit", new Point(20, y),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, new Scalar(150, 150, 150), 1);
    }

    private void toggleMapping() {
        if (mappingActive) {
            mappingActive = false;
            state = State.IDLE;
            sendPixelblazeCommand("off");
            logger.info("Mapping stopped at LED {}", currentLedIndex);
        } else {
            mappingActive = true;
            currentLedIndex = 0;
            currentBrightness = brightnessStart;
            startLedCapture();
            logger.info("Mapping started");
        }
    }

    private void startLedCapture() {
        sendPixelblazeCommand("off");
        state = State.CAPTURING_BASELINE;
        frameCounter = 0;
        currentBrightness = brightnessStart;
        candidatePoints.clear();
    }

    private void advanceToNextLed() {
        currentLedIndex++;
        if (currentLedIndex >= totalLeds) {
            mappingActive = false;
            state = State.IDLE;
            sendPixelblazeCommand("off");
            logger.info("Mapping complete! Detected {} of {} LEDs.", detectedPositions.size(), totalLeds);
            savePositions();
        } else {
            startLedCapture();
        }
    }

    private void lightCurrentLed() {
        sendPixelblazeCommand("pixel " + currentLedIndex + " " + currentBrightness);
    }

    private void resetMapping() {
        mappingActive = false;
        state = State.IDLE;
        currentLedIndex = -1;
        currentBrightness = brightnessStart;
        detectedPositions.clear();
        candidatePoints.clear();
        sendPixelblazeCommand("off");
        if (baselineFrame != null) {
            baselineFrame.release();
            baselineFrame = null;
        }
        logger.info("Mapping reset");
    }

    private void savePositions() {
        try {
            String filename = "pixelmap_" + System.currentTimeMillis() + ".js";
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                // Find bounds for normalization
                double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
                double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
                
                for (Point p : detectedPositions.values()) {
                    if (p != null) {
                        minX = Math.min(minX, p.x);
                        maxX = Math.max(maxX, p.x);
                        minY = Math.min(minY, p.y);
                        maxY = Math.max(maxY, p.y);
                    }
                }
                
                double rangeX = maxX - minX;
                double rangeY = maxY - minY;
                
                // Pixelblaze pixel mapper function format
                writer.println("// Pixelblaze Pixel Mapper - Generated by LED Mapping Session");
                writer.println("// Detected: " + detectedPositions.size() + " of " + totalLeds + " LEDs");
                writer.println("// Original bounds: X=[" + String.format("%.0f", minX) + "-" + String.format("%.0f", maxX) + 
                              "], Y=[" + String.format("%.0f", minY) + "-" + String.format("%.0f", maxY) + "]");
                writer.println("function (pixelCount) {");
                writer.println("  var map = []");
                
                for (int i = 0; i < totalLeds; i++) {
                    Point p = detectedPositions.get(i);
                    if (p != null) {
                        // Normalize to 0-1 range
                        double normX = rangeX > 0 ? (p.x - minX) / rangeX : 0.5;
                        double normY = rangeY > 0 ? (p.y - minY) / rangeY : 0.5;
                        writer.printf("  map.push([%.4f, %.4f]) // %d%n", normX, normY, i);
                    } else {
                        // Use interpolated position or center for missing LEDs
                        writer.printf("  map.push([0.5, 0.5]) // %d (not detected)%n", i);
                    }
                }
                
                writer.println("  return map");
                writer.println("}");
            }
            logger.info("Pixelblaze pixel map saved to {}", filename);
        } catch (IOException e) {
            logger.error("Failed to save positions: {}", e.getMessage());
        }
    }

    private boolean startPixelblazeController() {
        try {
            pixelblazeController = new PixelblazeController(pixelblazeHost, totalLeds);
            if (pixelblazeController.connect()) {
                logger.info("Pixelblaze controller ready (Java WebSocket)");
                return true;
            }
            logger.error("Failed to connect to Pixelblaze");
            return false;
        } catch (Exception e) {
            logger.error("Failed to start Pixelblaze controller: {}", e.getMessage());
            return false;
        }
    }

    private void sendPixelblazeCommand(String cmd) {
        if (pixelblazeController == null || !pixelblazeController.isConnected()) {
            return;
        }
        
        try {
            if ("off".equals(cmd)) {
                pixelblazeController.clearAll();
            } else if (cmd.startsWith("pixel ")) {
                // Parse: "pixel <index> <brightness>"
                String[] parts = cmd.split(" ");
                if (parts.length >= 3) {
                    int index = Integer.parseInt(parts[1]);
                    int brightness = Integer.parseInt(parts[2]);
                    // Convert brightness 1-50 to RGB 0-255 (roughly 5x)
                    int rgbValue = Math.min(255, brightness * 5);
                    pixelblazeController.lightOnlyPixel(index, rgbValue, rgbValue, rgbValue);
                }
            }
        } catch (Exception e) {
            logger.error("Error sending Pixelblaze command: {}", e.getMessage());
        }
    }

    private void stopPixelblazeController() {
        if (pixelblazeController != null) {
            pixelblazeController.close();
            pixelblazeController = null;
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

