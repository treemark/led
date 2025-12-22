package com.xmas.analysis;

import com.xmas.video.VideoLoader;
import com.xmas.video.VideoMetadata;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

/**
 * Analyzes video to detect LED light positions in sequence.
 * 
 * Expected pattern:
 * - Blue LED: First pixel (index 0), always on
 * - Red LED: Last pixel (last index), always on
 * - Green LEDs: Blink in sequence from index 1 to n-1, one at a time
 */
public class LedSequenceAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(LedSequenceAnalyzer.class);

    // Distance threshold - if a detected point is within this distance of an existing one, it's considered the same LED
    private static final double SAME_LED_DISTANCE = 15.0;
    
    // For refraction detection: a point must be within this distance of ANY known LED to be considered valid
    // This filters out lens refractions that appear far away from the LED strip entirely
    // Set to 0 to disable this filter
    private static final double MAX_DISTANCE_FROM_STRIP = 0; // DISABLED for debugging
    
    // The first green LED (index 1) must be within this distance of the blue LED to start the sequence
    // This ensures we start tracking from the beginning of the animation, not mid-sequence
    // Set to a very large value to disable this filter
    private static final double SEQUENCE_START_DISTANCE = 10000.0; // DISABLED for debugging

    private final LedDetector detector;
    
    // Expected number of LEDs (0 = auto-detect, no limit)
    private int expectedLedCount = 0;
    
    // Debug video output path (null = disabled)
    private Path debugVideoPath = null;

    public LedSequenceAnalyzer() {
        this.detector = new LedDetector();
    }
    
    /**
     * Sets the expected number of LEDs. When this count is reached, analysis stops.
     * Set to 0 for auto-detect mode (process entire video).
     *
     * @param count expected number of LEDs (including blue and red markers)
     */
    public void setExpectedLedCount(int count) {
        this.expectedLedCount = count;
    }
    
    /**
     * Gets the expected LED count.
     */
    public int getExpectedLedCount() {
        return expectedLedCount;
    }
    
    /**
     * Enables debug video output. The output video will show detected LEDs overlaid on each frame.
     *
     * @param outputPath Path for the debug video file (e.g., "debug_output.mov")
     */
    public void setDebugVideoOutput(Path outputPath) {
        this.debugVideoPath = outputPath;
        logger.info("Debug video output enabled: {}", outputPath);
    }
    
    /**
     * Disables debug video output.
     */
    public void disableDebugVideoOutput() {
        this.debugVideoPath = null;
    }

    /**
     * Analyzes the video and returns LED positions indexed by their sequence number.
     * 
     * SIMPLIFIED VERSION:
     * - Blue LED (index 0): Detected once, stays constant
     * - Red LED (last index): Detected once, stays constant  
     * - Green LEDs: Each new position detected becomes the next index
     * - No sequence start waiting - starts immediately
     * - No wrap-around detection
     *
     * @param loader the video loader
     * @return map of index to Point (in original camera coordinates)
     */
    public Map<Integer, Point> analyze(VideoLoader loader) {
        VideoMetadata metadata = loader.getMetadata();
        logger.info("Analyzing video: {} frames at {} fps", metadata.frameCount(), metadata.fps());
        
        if (expectedLedCount > 0) {
            logger.info("Expected LED count: {} (will stop when found)", expectedLedCount);
        } else {
            logger.info("Auto-detect mode: will scan entire video");
        }

        Map<Integer, Point> ledPositions = new LinkedHashMap<>();
        List<Point> knownGreenPositions = new ArrayList<>();
        
        Point bluePosition = null;
        Point redPosition = null;
        int nextGreenIndex = 1; // Green LEDs start at index 1
        
        // Calculate how many green LEDs we need (total - blue - red)
        int greenLedsNeeded = expectedLedCount > 0 ? expectedLedCount - 2 : Integer.MAX_VALUE;

        loader.reset();
        int frameNumber = 0;
        int lastReportedProgress = 0;

        Mat frame;
        Mat prevFrame = null;
        boolean foundAllLeds = false;
        
        // Debug video writer (if enabled)
        DebugVideoWriter debugWriter = null;
        if (debugVideoPath != null) {
            debugWriter = new DebugVideoWriter(debugVideoPath, 
                    metadata.width(), metadata.height(), metadata.fps());
        }
        
        try {
            while ((frame = loader.readFrame()) != null && !foundAllLeds) {
                frameNumber++;
                Point newGreenThisFrame = null;
                List<Point> rejectedThisFrame = new ArrayList<>();

                // Report progress every 10%
                int progress = (int) ((frameNumber * 100.0) / metadata.frameCount());
                if (progress >= lastReportedProgress + 10) {
                    logger.info("Progress: {}% (frame {}/{}) - Found {} LEDs so far", 
                            progress, frameNumber, metadata.frameCount(), ledPositions.size());
                    lastReportedProgress = progress;
                }

                // Detect blue LED (only use first detection - it should be constant)
                if (bluePosition == null) {
                    List<Point> blues = detector.detectBlue(frame);
                    if (!blues.isEmpty()) {
                        bluePosition = blues.get(0);
                        ledPositions.put(0, bluePosition);
                        logger.info("Found BLUE LED (index 0) at ({}, {})", 
                                String.format("%.1f", bluePosition.x), 
                                String.format("%.1f", bluePosition.y));
                    }
                }

                // Detect red LED (only use first detection - it should be constant)
                if (redPosition == null) {
                    List<Point> reds = detector.detectRed(frame);
                    if (!reds.isEmpty()) {
                        redPosition = reds.get(0);
                        logger.info("Found RED LED (last index) at ({}, {})", 
                                String.format("%.1f", redPosition.x), 
                                String.format("%.1f", redPosition.y));
                    }
                }

                // Detect green LEDs - find any NEW positions
                List<Point> currentGreens = detector.detectGreen(frame);
                
                for (Point green : currentGreens) {
                    // Skip if too close to blue or red (avoid counting them as green)
                    if (bluePosition != null && isNear(green, bluePosition, SAME_LED_DISTANCE)) {
                        continue;
                    }
                    if (redPosition != null && isNear(green, redPosition, SAME_LED_DISTANCE)) {
                        continue;
                    }
                    
                    // Skip if we already know this position
                    if (isNearAny(green, knownGreenPositions, SAME_LED_DISTANCE)) {
                        continue;
                    }
                    
                    // This is a NEW green LED position!
                    knownGreenPositions.add(green);
                    ledPositions.put(nextGreenIndex, green);
                    newGreenThisFrame = green;
                    
                    logger.info("Found GREEN LED (index {}) at ({}, {}) in frame {}", 
                            nextGreenIndex, 
                            String.format("%.1f", green.x), 
                            String.format("%.1f", green.y), 
                            frameNumber);
                    nextGreenIndex++;
                    
                    // Check if we've found all expected green LEDs
                    if (knownGreenPositions.size() >= greenLedsNeeded) {
                        logger.info("Found all {} expected green LEDs!", greenLedsNeeded);
                        foundAllLeds = true;
                        break;
                    }
                }
                
                // Write debug frame
                if (debugWriter != null) {
                    debugWriter.writeFrame(frame, bluePosition, redPosition, 
                            knownGreenPositions, newGreenThisFrame, rejectedThisFrame,
                            nextGreenIndex - 1, true, frameNumber);
                }

                if (prevFrame != null) {
                    prevFrame.release();
                }
                prevFrame = frame;
            }
        } finally {
            // Close debug writer
            if (debugWriter != null) {
                debugWriter.close();
            }
        }
        
        if (prevFrame != null) {
            prevFrame.release();
        }

        // Add red LED as the last index
        if (redPosition != null) {
            int redIndex = expectedLedCount > 0 ? expectedLedCount - 1 : nextGreenIndex;
            ledPositions.put(redIndex, redPosition);
            logger.info("RED LED assigned as index {}", redIndex);
        }

        logger.info("Analysis complete. Found {} LEDs total (blue + {} green + red)", 
                ledPositions.size(), knownGreenPositions.size());
        
        if (expectedLedCount > 0 && ledPositions.size() != expectedLedCount) {
            logger.warn("Expected {} LEDs but found {}!", expectedLedCount, ledPositions.size());
        }
        
        if (debugVideoPath != null) {
            logger.info("Debug video written to: {}", debugVideoPath);
        }
        
        return ledPositions;
    }
    
    /**
     * Calculates Euclidean distance between two points.
     */
    private double distance(Point a, Point b) {
        if (a == null || b == null) return Double.MAX_VALUE;
        return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
    }

    /**
     * Normalizes positions so bottom-left is (0,0) and top-right is (1,1).
     * Note: In image coordinates, Y increases downward, so we flip Y.
     *
     * @param positions raw positions in camera coordinates
     * @return normalized positions
     */
    public Map<Integer, double[]> normalizePositions(Map<Integer, Point> positions) {
        if (positions.isEmpty()) {
            return Collections.emptyMap();
        }

        // Find bounds
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;

        for (Point p : positions.values()) {
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }

        logger.info("Bounds: X=[{:.1f}, {:.1f}], Y=[{:.1f}, {:.1f}]", minX, maxX, minY, maxY);

        double rangeX = maxX - minX;
        double rangeY = maxY - minY;

        // Avoid division by zero
        if (rangeX == 0) rangeX = 1;
        if (rangeY == 0) rangeY = 1;

        Map<Integer, double[]> normalized = new LinkedHashMap<>();

        for (Map.Entry<Integer, Point> entry : positions.entrySet()) {
            Point p = entry.getValue();
            // Normalize X: 0 = left, 1 = right
            double nx = (p.x - minX) / rangeX;
            // Normalize Y: 0 = bottom, 1 = top (flip because image Y increases downward)
            double ny = 1.0 - ((p.y - minY) / rangeY);
            normalized.put(entry.getKey(), new double[]{nx, ny});
        }

        return normalized;
    }

    /**
     * Converts positions to JavaScript array format.
     *
     * @param normalizedPositions normalized positions map
     * @return JavaScript array string
     */
    public String toJavaScriptArray(Map<Integer, double[]> normalizedPositions) {
        if (normalizedPositions.isEmpty()) {
            return "[]";
        }

        // Find max index to size the array properly
        int maxIndex = normalizedPositions.keySet().stream().mapToInt(i -> i).max().orElse(0);

        StringBuilder sb = new StringBuilder();
        sb.append("const ledPositions = [\n");

        for (int i = 0; i <= maxIndex; i++) {
            double[] pos = normalizedPositions.get(i);
            if (pos != null) {
                sb.append(String.format("  [%.6f, %.6f]", pos[0], pos[1]));
            } else {
                sb.append("  null"); // Missing position
            }
            if (i < maxIndex) {
                sb.append(",");
            }
            sb.append(" // index ").append(i).append("\n");
        }

        sb.append("];\n");
        return sb.toString();
    }

    /**
     * Also outputs raw camera coordinates for debugging.
     */
    public String toJavaScriptArrayRaw(Map<Integer, Point> positions) {
        if (positions.isEmpty()) {
            return "[]";
        }

        int maxIndex = positions.keySet().stream().mapToInt(i -> i).max().orElse(0);

        StringBuilder sb = new StringBuilder();
        sb.append("const ledPositionsRaw = [\n");

        for (int i = 0; i <= maxIndex; i++) {
            Point pos = positions.get(i);
            if (pos != null) {
                sb.append(String.format("  [%.1f, %.1f]", pos.x, pos.y));
            } else {
                sb.append("  null");
            }
            if (i < maxIndex) {
                sb.append(",");
            }
            sb.append(" // index ").append(i).append("\n");
        }

        sb.append("];\n");
        return sb.toString();
    }

    private boolean isNear(Point a, Point b, double threshold) {
        if (a == null || b == null) return false;
        double dist = Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
        return dist < threshold;
    }
    
    private boolean isNearAny(Point p, List<Point> others, double threshold) {
        for (Point other : others) {
            if (isNear(p, other, threshold)) {
                return true;
            }
        }
        return false;
    }
}
