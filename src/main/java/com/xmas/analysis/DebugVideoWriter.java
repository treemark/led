package com.xmas.analysis;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;

/**
 * Writes debug video with detected LED positions overlaid as colored circles.
 */
public class DebugVideoWriter implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DebugVideoWriter.class);

    private final VideoWriter writer;
    private final int width;
    private final int height;
    private final double fps;
    private int frameCount = 0;

    // Colors (BGR format for OpenCV)
    private static final Scalar COLOR_BLUE = new Scalar(255, 0, 0);      // Blue LED (index 0)
    private static final Scalar COLOR_RED = new Scalar(0, 0, 255);       // Red LED (last index)
    private static final Scalar COLOR_GREEN = new Scalar(0, 255, 0);     // Green LEDs (detected this frame)
    private static final Scalar COLOR_KNOWN = new Scalar(0, 255, 255);   // Yellow - previously detected
    private static final Scalar COLOR_REJECTED = new Scalar(0, 165, 255); // Orange - rejected as refraction
    private static final Scalar COLOR_WHITE = new Scalar(255, 255, 255); // White for text

    // Circle sizes
    private static final int RADIUS_MAIN = 15;
    private static final int RADIUS_SMALL = 8;
    private static final int THICKNESS = 2;

    public DebugVideoWriter(Path outputPath, int width, int height, double fps) {
        this.width = width;
        this.height = height;
        this.fps = fps;

        // Try different codecs - 'avc1' works well on macOS for MP4
        int fourcc = VideoWriter.fourcc('a', 'v', 'c', '1');
        
        this.writer = new VideoWriter(
                outputPath.toString(),
                fourcc,
                fps,
                new Size(width, height),
                true // isColor
        );

        // If avc1 fails, try mp4v
        if (!writer.isOpened()) {
            logger.warn("avc1 codec failed, trying mp4v...");
            fourcc = VideoWriter.fourcc('m', 'p', '4', 'v');
            this.writer.open(outputPath.toString(), fourcc, fps, new Size(width, height), true);
        }
        
        // If still not opened, try MJPG
        if (!writer.isOpened()) {
            logger.warn("mp4v codec failed, trying MJPG...");
            fourcc = VideoWriter.fourcc('M', 'J', 'P', 'G');
            String aviPath = outputPath.toString().replace(".mp4", ".avi");
            this.writer.open(aviPath, fourcc, fps, new Size(width, height), true);
            if (writer.isOpened()) {
                logger.info("Using MJPG codec with AVI format: {}", aviPath);
            }
        }

        if (!writer.isOpened()) {
            throw new RuntimeException("Failed to open video writer for: " + outputPath + " (tried avc1, mp4v, MJPG codecs)");
        }

        logger.info("Debug video writer opened: {} ({}x{} @ {} fps)", outputPath, width, height, fps);
    }

    /**
     * Writes a frame with detection overlays.
     *
     * @param frame           The original video frame
     * @param bluePosition    Blue LED position (or null)
     * @param redPosition     Red LED position (or null)
     * @param knownGreens     Previously detected green LED positions
     * @param newGreen        Newly detected green LED this frame (or null)
     * @param rejectedPoints  Points rejected as refractions this frame
     * @param currentGreenIndex Current green LED index being tracked
     * @param sequenceStarted Whether the sequence has started
     * @param frameNumber     Current frame number
     */
    public void writeFrame(Mat frame, 
                          Point bluePosition, 
                          Point redPosition,
                          java.util.List<Point> knownGreens,
                          Point newGreen,
                          java.util.List<Point> rejectedPoints,
                          int currentGreenIndex,
                          boolean sequenceStarted,
                          int frameNumber) {
        
        // Clone frame to avoid modifying original
        Mat annotated = frame.clone();

        try {
            // Draw rejected points first (underneath everything else)
            if (rejectedPoints != null) {
                for (Point p : rejectedPoints) {
                    Imgproc.circle(annotated, p, RADIUS_SMALL, COLOR_REJECTED, 1);
                    // Small X mark
                    Imgproc.line(annotated, 
                            new Point(p.x - 5, p.y - 5), 
                            new Point(p.x + 5, p.y + 5), 
                            COLOR_REJECTED, 1);
                    Imgproc.line(annotated, 
                            new Point(p.x + 5, p.y - 5), 
                            new Point(p.x - 5, p.y + 5), 
                            COLOR_REJECTED, 1);
                }
            }

            // Draw previously known green positions (smaller yellow circles)
            if (knownGreens != null) {
                int idx = 1;
                for (Point p : knownGreens) {
                    Imgproc.circle(annotated, p, RADIUS_SMALL, COLOR_KNOWN, 1);
                    // Draw index number
                    Imgproc.putText(annotated, String.valueOf(idx), 
                            new Point(p.x + 10, p.y - 5),
                            Imgproc.FONT_HERSHEY_SIMPLEX, 0.3, COLOR_KNOWN, 1);
                    idx++;
                }
            }

            // Draw blue LED (always on)
            if (bluePosition != null) {
                Imgproc.circle(annotated, bluePosition, RADIUS_MAIN, COLOR_BLUE, THICKNESS);
                Imgproc.circle(annotated, bluePosition, RADIUS_MAIN + 5, COLOR_BLUE, 1);
                Imgproc.putText(annotated, "0", 
                        new Point(bluePosition.x + 20, bluePosition.y),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, COLOR_BLUE, 2);
            }

            // Draw red LED (always on)
            if (redPosition != null) {
                Imgproc.circle(annotated, redPosition, RADIUS_MAIN, COLOR_RED, THICKNESS);
                Imgproc.circle(annotated, redPosition, RADIUS_MAIN + 5, COLOR_RED, 1);
                Imgproc.putText(annotated, "255", 
                        new Point(redPosition.x + 20, redPosition.y),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, COLOR_RED, 2);
            }

            // Draw newly detected green LED (highlighted)
            if (newGreen != null) {
                Imgproc.circle(annotated, newGreen, RADIUS_MAIN, COLOR_GREEN, THICKNESS);
                Imgproc.circle(annotated, newGreen, RADIUS_MAIN + 8, COLOR_GREEN, 2);
                Imgproc.putText(annotated, String.valueOf(currentGreenIndex), 
                        new Point(newGreen.x + 20, newGreen.y),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, COLOR_GREEN, 2);
            }

            // Draw status text overlay
            drawStatusOverlay(annotated, frameNumber, currentGreenIndex, sequenceStarted, 
                    knownGreens != null ? knownGreens.size() : 0,
                    rejectedPoints != null ? rejectedPoints.size() : 0);

            // Write frame
            writer.write(annotated);
            frameCount++;

        } finally {
            annotated.release();
        }
    }

    private void drawStatusOverlay(Mat frame, int frameNumber, int currentIndex, 
                                   boolean sequenceStarted, int knownCount, int rejectedCount) {
        // Semi-transparent background for text
        Imgproc.rectangle(frame, 
                new Point(10, 10), 
                new Point(350, 120), 
                new Scalar(0, 0, 0), -1);
        Imgproc.rectangle(frame, 
                new Point(10, 10), 
                new Point(350, 120), 
                COLOR_WHITE, 1);

        int y = 30;
        int lineHeight = 22;

        Imgproc.putText(frame, String.format("Frame: %d", frameNumber),
                new Point(20, y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, COLOR_WHITE, 1);
        y += lineHeight;

        String status = sequenceStarted ? "TRACKING" : "WAITING FOR START";
        Scalar statusColor = sequenceStarted ? COLOR_GREEN : COLOR_REJECTED;
        Imgproc.putText(frame, String.format("Status: %s", status),
                new Point(20, y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, statusColor, 1);
        y += lineHeight;

        Imgproc.putText(frame, String.format("LEDs found: %d", knownCount + 1), // +1 for blue
                new Point(20, y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, COLOR_KNOWN, 1);
        y += lineHeight;

        Imgproc.putText(frame, String.format("Rejected: %d", rejectedCount),
                new Point(20, y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, COLOR_REJECTED, 1);

        // Legend at bottom
        int legendY = height - 80;
        Imgproc.rectangle(frame, 
                new Point(10, legendY - 10), 
                new Point(300, height - 10), 
                new Scalar(0, 0, 0), -1);

        Imgproc.circle(frame, new Point(30, legendY + 10), 8, COLOR_BLUE, -1);
        Imgproc.putText(frame, "Blue (start)", new Point(45, legendY + 15), 
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, COLOR_WHITE, 1);

        Imgproc.circle(frame, new Point(130, legendY + 10), 8, COLOR_RED, -1);
        Imgproc.putText(frame, "Red (end)", new Point(145, legendY + 15), 
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, COLOR_WHITE, 1);

        Imgproc.circle(frame, new Point(30, legendY + 35), 8, COLOR_GREEN, -1);
        Imgproc.putText(frame, "New green", new Point(45, legendY + 40), 
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, COLOR_WHITE, 1);

        Imgproc.circle(frame, new Point(130, legendY + 35), 8, COLOR_KNOWN, -1);
        Imgproc.putText(frame, "Known", new Point(145, legendY + 40), 
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, COLOR_WHITE, 1);

        Imgproc.circle(frame, new Point(210, legendY + 35), 8, COLOR_REJECTED, 1);
        Imgproc.putText(frame, "Rejected", new Point(225, legendY + 40), 
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, COLOR_WHITE, 1);
    }

    @Override
    public void close() {
        if (writer != null && writer.isOpened()) {
            writer.release();
            logger.info("Debug video closed. Wrote {} frames.", frameCount);
        }
    }

    public int getFrameCount() {
        return frameCount;
    }
}

