package com.xmas.analysis;

import com.xmas.video.VideoLoader;
import com.xmas.video.VideoMetadata;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Debug tool specifically for analyzing blue and red LED detection.
 * Outputs individual frames and color masks for inspection.
 */
public class BlueRedDebugger {

    private static final Logger logger = LoggerFactory.getLogger(BlueRedDebugger.class);

    // HSV ranges - same as LedDetector
    // Blue LED
    private static final Scalar BLUE_LOW = new Scalar(100, 100, 100);
    private static final Scalar BLUE_HIGH = new Scalar(130, 255, 255);

    // Red LED (wraps around in HSV)
    private static final Scalar RED_LOW_1 = new Scalar(0, 100, 100);
    private static final Scalar RED_HIGH_1 = new Scalar(10, 255, 255);
    private static final Scalar RED_LOW_2 = new Scalar(160, 100, 100);
    private static final Scalar RED_HIGH_2 = new Scalar(180, 255, 255);

    private static final double MIN_LED_AREA = 10;
    private static final double MAX_LED_AREA = 5000;

    private final Path outputDir;

    public BlueRedDebugger(Path outputDir) {
        this.outputDir = outputDir;
        outputDir.toFile().mkdirs();
    }

    /**
     * Analyzes first N frames and outputs debug images.
     */
    public void analyzeFrames(VideoLoader loader, int framesToAnalyze) {
        VideoMetadata metadata = loader.getMetadata();
        logger.info("=== BLUE/RED LED DEBUGGER ===");
        logger.info("Video: {}x{}, {} frames", metadata.width(), metadata.height(), metadata.frameCount());
        logger.info("Analyzing first {} frames", framesToAnalyze);
        logger.info("Output directory: {}", outputDir);

        loader.reset();
        Mat frame;
        int frameNumber = 0;

        while ((frame = loader.readFrame()) != null && frameNumber < framesToAnalyze) {
            frameNumber++;
            analyzeFrame(frame, frameNumber);
            frame.release();
        }

        logger.info("=== DEBUG COMPLETE ===");
        logger.info("Check output directory for debug images: {}", outputDir);
    }

    private void analyzeFrame(Mat frame, int frameNumber) {
        logger.info("\n--- Frame {} ---", frameNumber);

        Mat hsv = new Mat();
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);

        // Detect Blue
        Mat blueMask = new Mat();
        Core.inRange(hsv, BLUE_LOW, BLUE_HIGH, blueMask);
        List<DetectedBlob> blueBlobs = findBlobs(blueMask, hsv, "BLUE");
        
        // Detect Red
        Mat redMask1 = new Mat();
        Mat redMask2 = new Mat();
        Mat redMask = new Mat();
        Core.inRange(hsv, RED_LOW_1, RED_HIGH_1, redMask1);
        Core.inRange(hsv, RED_LOW_2, RED_HIGH_2, redMask2);
        Core.bitwise_or(redMask1, redMask2, redMask);
        List<DetectedBlob> redBlobs = findBlobs(redMask, hsv, "RED");

        // Log findings
        logger.info("BLUE blobs found: {}", blueBlobs.size());
        for (DetectedBlob blob : blueBlobs) {
            logger.info("  BLUE at ({}, {}) - area: {}, brightness: {}", 
                    String.format("%.1f", blob.center.x), 
                    String.format("%.1f", blob.center.y), 
                    String.format("%.1f", blob.area), 
                    String.format("%.1f", blob.brightness));
        }

        logger.info("RED blobs found: {}", redBlobs.size());
        for (DetectedBlob blob : redBlobs) {
            logger.info("  RED at ({}, {}) - area: {}, brightness: {}", 
                    String.format("%.1f", blob.center.x), 
                    String.format("%.1f", blob.center.y), 
                    String.format("%.1f", blob.area), 
                    String.format("%.1f", blob.brightness));
        }

        // Save debug images
        saveDebugImages(frame, hsv, blueMask, redMask, blueBlobs, redBlobs, frameNumber);

        // Cleanup
        hsv.release();
        blueMask.release();
        redMask1.release();
        redMask2.release();
        redMask.release();
    }

    private List<DetectedBlob> findBlobs(Mat mask, Mat hsv, String colorName) {
        List<DetectedBlob> blobs = new ArrayList<>();
        
        // Apply morphological operations
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        Mat processedMask = new Mat();
        Imgproc.morphologyEx(mask, processedMask, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(processedMask, processedMask, Imgproc.MORPH_CLOSE, kernel);
        kernel.release();

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(processedMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            
            // Log all contours, even if outside area threshold
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

                if (area >= MIN_LED_AREA && area <= MAX_LED_AREA) {
                    blobs.add(new DetectedBlob(new Point(cx, cy), area, brightness));
                } else {
                    logger.debug("  {} contour REJECTED - area: {:.1f} (outside range {}-{})", 
                            colorName, area, MIN_LED_AREA, MAX_LED_AREA);
                }
            }
            contour.release();
        }
        
        hierarchy.release();
        processedMask.release();
        
        return blobs;
    }

    private void saveDebugImages(Mat frame, Mat hsv, Mat blueMask, Mat redMask,
                                  List<DetectedBlob> blueBlobs, List<DetectedBlob> redBlobs, 
                                  int frameNumber) {
        // Create annotated frame
        Mat annotated = frame.clone();

        // Draw blue detections
        for (DetectedBlob blob : blueBlobs) {
            Imgproc.circle(annotated, blob.center, 20, new Scalar(255, 0, 0), 3);
            Imgproc.putText(annotated, "BLUE", 
                    new Point(blob.center.x + 25, blob.center.y),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(255, 0, 0), 2);
        }

        // Draw red detections
        for (DetectedBlob blob : redBlobs) {
            Imgproc.circle(annotated, blob.center, 20, new Scalar(0, 0, 255), 3);
            Imgproc.putText(annotated, "RED", 
                    new Point(blob.center.x + 25, blob.center.y),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(0, 0, 255), 2);
        }

        // Add HSV range info
        Imgproc.putText(annotated, String.format("Frame %d", frameNumber), 
                new Point(20, 40), Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(255, 255, 255), 2);
        Imgproc.putText(annotated, String.format("Blue HSV: [100-130, 100-255, 100-255]"), 
                new Point(20, 80), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(255, 100, 100), 2);
        Imgproc.putText(annotated, String.format("Red HSV: [0-10 or 160-180, 100-255, 100-255]"), 
                new Point(20, 110), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(100, 100, 255), 2);
        Imgproc.putText(annotated, String.format("Blue: %d, Red: %d detected", blueBlobs.size(), redBlobs.size()), 
                new Point(20, 150), Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(255, 255, 255), 2);

        // Save images
        String prefix = String.format("frame_%03d_", frameNumber);
        Imgcodecs.imwrite(outputDir.resolve(prefix + "annotated.jpg").toString(), annotated);
        Imgcodecs.imwrite(outputDir.resolve(prefix + "blue_mask.jpg").toString(), blueMask);
        Imgcodecs.imwrite(outputDir.resolve(prefix + "red_mask.jpg").toString(), redMask);
        
        // Also save the original frame
        Imgcodecs.imwrite(outputDir.resolve(prefix + "original.jpg").toString(), frame);

        // Create combined mask visualization
        Mat combinedViz = new Mat(frame.rows(), frame.cols(), CvType.CV_8UC3, new Scalar(0, 0, 0));
        
        // Blue mask in blue channel
        Mat blueChannel = new Mat();
        blueMask.copyTo(blueChannel);
        
        // Red mask in red channel
        Mat redChannel = new Mat();
        redMask.copyTo(redChannel);
        
        List<Mat> channels = new ArrayList<>();
        channels.add(blueChannel);          // B
        channels.add(new Mat(frame.rows(), frame.cols(), CvType.CV_8UC1, new Scalar(0))); // G
        channels.add(redChannel);           // R
        Core.merge(channels, combinedViz);
        
        Imgcodecs.imwrite(outputDir.resolve(prefix + "combined_masks.jpg").toString(), combinedViz);

        // Cleanup
        annotated.release();
        combinedViz.release();
        blueChannel.release();
        redChannel.release();
        for (Mat ch : channels) {
            ch.release();
        }
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

    /**
     * Run standalone debug analysis.
     */
    public static void runDebug(String videoPath, int framesToAnalyze) throws Exception {
        Path video = Paths.get(videoPath).toAbsolutePath();
        Path outputDir = Paths.get("debug_output").toAbsolutePath();

        try (VideoLoader loader = VideoLoader.open(video)) {
            BlueRedDebugger debugger = new BlueRedDebugger(outputDir);
            debugger.analyzeFrames(loader, framesToAnalyze);
        }
    }
}

