package com.xmas.analysis;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Detects LED lights in video frames by color.
 */
public class LedDetector {

    private static final Logger logger = LoggerFactory.getLogger(LedDetector.class);

    // HSV ranges for LED colors (these may need tuning based on actual video)
    // Blue LED
    private static final Scalar BLUE_LOW = new Scalar(100, 100, 100);
    private static final Scalar BLUE_HIGH = new Scalar(130, 255, 255);

    // Green LED
    private static final Scalar GREEN_LOW = new Scalar(35, 100, 100);
    private static final Scalar GREEN_HIGH = new Scalar(85, 255, 255);

    // Red LED (red wraps around in HSV, so we need two ranges)
    private static final Scalar RED_LOW_1 = new Scalar(0, 100, 100);
    private static final Scalar RED_HIGH_1 = new Scalar(10, 255, 255);
    private static final Scalar RED_LOW_2 = new Scalar(160, 100, 100);
    private static final Scalar RED_HIGH_2 = new Scalar(180, 255, 255);

    // Minimum area for a detected blob to be considered an LED
    private static final double MIN_LED_AREA = 10;
    private static final double MAX_LED_AREA = 5000;

    /**
     * Holds information about a detected LED candidate.
     */
    private static class LedCandidate {
        Point center;
        double brightness;   // Average brightness (V channel) at the LED location
        double circularity;  // How circular the blob is (1.0 = perfect circle)
        double area;
        
        LedCandidate(Point center, double brightness, double circularity, double area) {
            this.center = center;
            this.brightness = brightness;
            this.circularity = circularity;
            this.area = area;
        }
        
        /**
         * Score combining brightness and sharpness (circularity).
         * Higher is better. Real LEDs are brighter and more circular than refractions.
         */
        double getScore() {
            // Weight brightness heavily (0-255 range) and circularity (0-1 range)
            // Normalize brightness to 0-1 and combine
            return (brightness / 255.0) * 0.6 + circularity * 0.4;
        }
    }

    /**
     * Detects the blue LED in the frame (index 0, start marker).
     * If multiple candidates are found, returns only the best one (largest area and brightest)
     * to filter out lens refractions. Blue LEDs are typically the brightest/largest.
     *
     * @param frame BGR frame
     * @return list containing at most one LED center point (the best candidate)
     */
    public List<Point> detectBlue(Mat frame) {
        List<LedCandidate> candidates = detectByColorWithScoring(frame, BLUE_LOW, BLUE_HIGH, null, null);
        return selectBestCandidate(candidates, "BLUE");
    }

    /**
     * Detects green LEDs in the frame.
     * If multiple candidates are found, returns only the best one (brightest and sharpest)
     * to filter out lens refractions.
     *
     * @param frame BGR frame
     * @return list containing at most one LED center point (the best candidate)
     */
    public List<Point> detectGreen(Mat frame) {
        List<LedCandidate> candidates = detectByColorWithScoring(frame, GREEN_LOW, GREEN_HIGH, null, null);
        
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (candidates.size() == 1) {
            List<Point> result = new ArrayList<>();
            result.add(candidates.get(0).center);
            return result;
        }
        
        // Multiple candidates detected - pick the best one (brightest and sharpest)
        LedCandidate best = candidates.stream()
                .max(Comparator.comparingDouble(LedCandidate::getScore))
                .orElse(null);
        
        if (best != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Multiple green candidates ({}), selected best with brightness={:.1f}, circularity={:.3f}, score={:.3f}",
                        candidates.size(), best.brightness, best.circularity, best.getScore());
            }
            List<Point> result = new ArrayList<>();
            result.add(best.center);
            return result;
        }
        
        return new ArrayList<>();
    }

    /**
     * Detects the red LED in the frame (last index, end marker).
     * If multiple candidates are found, returns only the best one (largest area and brightest)
     * to filter out lens refractions and other red objects.
     *
     * @param frame BGR frame
     * @return list containing at most one LED center point (the best candidate)
     */
    public List<Point> detectRed(Mat frame) {
        List<LedCandidate> candidates = detectByColorWithScoring(frame, RED_LOW_1, RED_HIGH_1, RED_LOW_2, RED_HIGH_2);
        return selectBestCandidate(candidates, "RED");
    }

    /**
     * Detects all lit LEDs (any bright spot) in the frame.
     *
     * @param frame BGR frame
     * @return list of LED center points
     */
    public List<Point> detectAnyBright(Mat frame) {
        Mat hsv = new Mat();
        Mat mask = new Mat();
        
        try {
            Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);
            
            // Detect any bright pixels (high value in HSV)
            Core.inRange(hsv, new Scalar(0, 0, 200), new Scalar(180, 255, 255), mask);
            
            return findLedCenters(mask, null);
        } finally {
            hsv.release();
            mask.release();
        }
    }

    /**
     * Selects the best blue LED candidate (index 0, start marker).
     * Blue LEDs are typically the largest blob in the blue detection mask.
     */
    private List<Point> selectBestBlueCandidate(List<LedCandidate> candidates) {
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (candidates.size() == 1) {
            List<Point> result = new ArrayList<>();
            result.add(candidates.get(0).center);
            return result;
        }
        
        // For blue, prioritize by area (real LED is typically largest)
        LedCandidate best = candidates.stream()
                .max(Comparator.comparingDouble((LedCandidate c) -> c.area))
                .orElse(null);
        
        if (best != null) {
            logger.info("Multiple BLUE candidates ({}), selected best at ({}, {}) with area={}, brightness={}", 
                    candidates.size(),
                    String.format("%.1f", best.center.x), 
                    String.format("%.1f", best.center.y),
                    String.format("%.1f", best.area),
                    String.format("%.1f", best.brightness));
            List<Point> result = new ArrayList<>();
            result.add(best.center);
            return result;
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Selects the best red LED candidate (last index, end marker).
     * Red detection is tricky because there may be other red objects in the scene.
     * We filter to reasonable LED sizes and prioritize brightness.
     */
    private List<Point> selectBestRedCandidate(List<LedCandidate> candidates) {
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (candidates.size() == 1) {
            List<Point> result = new ArrayList<>();
            result.add(candidates.get(0).center);
            return result;
        }
        
        // Filter out very large blobs (likely not LEDs) - LEDs typically have area < 500
        List<LedCandidate> filtered = candidates.stream()
                .filter(c -> c.area < 500)
                .toList();
        
        if (filtered.isEmpty()) {
            // Fallback to original list if all were filtered
            logger.warn("All RED candidates filtered out (area >= 500). Using unfiltered list.");
            filtered = candidates;
        }
        
        // For red, prioritize by area among the filtered candidates
        LedCandidate best = filtered.stream()
                .max(Comparator.comparingDouble((LedCandidate c) -> c.area))
                .orElse(null);
        
        if (best != null) {
            logger.info("Multiple RED candidates ({}), filtered to {} reasonable size, selected at ({}, {}) with area={}, brightness={}", 
                    candidates.size(), filtered.size(),
                    String.format("%.1f", best.center.x), 
                    String.format("%.1f", best.center.y),
                    String.format("%.1f", best.area),
                    String.format("%.1f", best.brightness));
            List<Point> result = new ArrayList<>();
            result.add(best.center);
            return result;
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Generic candidate selection based on score.
     */
    private List<Point> selectBestCandidate(List<LedCandidate> candidates, String colorName) {
        if ("BLUE".equals(colorName)) {
            return selectBestBlueCandidate(candidates);
        } else if ("RED".equals(colorName)) {
            return selectBestRedCandidate(candidates);
        }
        
        // Default: use score-based selection
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (candidates.size() == 1) {
            List<Point> result = new ArrayList<>();
            result.add(candidates.get(0).center);
            return result;
        }
        
        LedCandidate best = candidates.stream()
                .max(Comparator.comparingDouble(LedCandidate::getScore))
                .orElse(null);
        
        if (best != null) {
            List<Point> result = new ArrayList<>();
            result.add(best.center);
            return result;
        }
        
        return new ArrayList<>();
    }
    
    private List<Point> detectByColor(Mat frame, Scalar low1, Scalar high1, Scalar low2, Scalar high2) {
        List<LedCandidate> candidates = detectByColorWithScoring(frame, low1, high1, low2, high2);
        List<Point> result = new ArrayList<>();
        for (LedCandidate c : candidates) {
            result.add(c.center);
        }
        return result;
    }
    
    private List<LedCandidate> detectByColorWithScoring(Mat frame, Scalar low1, Scalar high1, Scalar low2, Scalar high2) {
        Mat hsv = new Mat();
        Mat mask1 = new Mat();
        Mat mask2 = new Mat();
        Mat mask = new Mat();

        try {
            Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);

            // First range
            Core.inRange(hsv, low1, high1, mask1);

            // Second range (for red which wraps around)
            if (low2 != null && high2 != null) {
                Core.inRange(hsv, low2, high2, mask2);
                Core.bitwise_or(mask1, mask2, mask);
            } else {
                mask1.copyTo(mask);
            }

            // Apply morphological operations to clean up the mask
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);
            kernel.release();

            return findLedCandidates(mask, hsv);
        } finally {
            hsv.release();
            mask1.release();
            mask2.release();
            mask.release();
        }
    }

    private List<Point> findLedCenters(Mat mask, Mat hsv) {
        List<LedCandidate> candidates = findLedCandidates(mask, hsv);
        List<Point> result = new ArrayList<>();
        for (LedCandidate c : candidates) {
            result.add(c.center);
        }
        return result;
    }
    
    private List<LedCandidate> findLedCandidates(Mat mask, Mat hsv) {
        List<LedCandidate> candidates = new ArrayList<>();
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        try {
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            for (MatOfPoint contour : contours) {
                double area = Imgproc.contourArea(contour);
                if (area >= MIN_LED_AREA && area <= MAX_LED_AREA) {
                    Moments moments = Imgproc.moments(contour);
                    if (moments.m00 > 0) {
                        double cx = moments.m10 / moments.m00;
                        double cy = moments.m01 / moments.m00;
                        Point center = new Point(cx, cy);
                        
                        // Calculate circularity: 4*pi*area / perimeter^2
                        // Perfect circle = 1.0, less circular shapes have lower values
                        double perimeter = Imgproc.arcLength(new MatOfPoint2f(contour.toArray()), true);
                        double circularity = 0;
                        if (perimeter > 0) {
                            circularity = (4 * Math.PI * area) / (perimeter * perimeter);
                            circularity = Math.min(circularity, 1.0); // Cap at 1.0
                        }
                        
                        // Get brightness at the center point from HSV V channel
                        double brightness = 0;
                        if (hsv != null) {
                            int px = (int) Math.round(cx);
                            int py = (int) Math.round(cy);
                            if (px >= 0 && px < hsv.cols() && py >= 0 && py < hsv.rows()) {
                                double[] hsvValues = hsv.get(py, px);
                                if (hsvValues != null && hsvValues.length >= 3) {
                                    brightness = hsvValues[2]; // V channel
                                }
                            }
                        }
                        
                        candidates.add(new LedCandidate(center, brightness, circularity, area));
                    }
                }
                contour.release();
            }
        } finally {
            hierarchy.release();
        }

        return candidates;
    }

    /**
     * Finds the closest point to a reference point from a list.
     */
    public Point findClosest(Point reference, List<Point> candidates) {
        if (candidates.isEmpty()) return null;

        Point closest = null;
        double minDist = Double.MAX_VALUE;

        for (Point p : candidates) {
            double dist = Math.sqrt(Math.pow(p.x - reference.x, 2) + Math.pow(p.y - reference.y, 2));
            if (dist < minDist) {
                minDist = dist;
                closest = p;
            }
        }

        return closest;
    }
}

