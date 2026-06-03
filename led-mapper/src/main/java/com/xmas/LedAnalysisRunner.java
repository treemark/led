package com.xmas;

import com.xmas.analysis.HtmlVisualizationGenerator;
import com.xmas.analysis.LedSequenceAnalyzer;
import com.xmas.video.VideoLoader;
import com.xmas.video.VideoMetadata;
import org.opencv.core.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Analyzes the video to detect LED positions and outputs JavaScript array.
 * 
 * Usage: ./gradlew bootRun --args="<video_file> <led_count> [debug]"
 * Example: ./gradlew bootRun --args="IMG_0285.mov 100"
 * Example with debug: ./gradlew bootRun --args="IMG_0285.mov 100 debug"
 */
@Component
@Order(1)
public class LedAnalysisRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LedAnalysisRunner.class);

    private static final String DEFAULT_VIDEO_FILE = "IMG_0285.mov";
    private static final int DEFAULT_LED_COUNT = 0; // 0 means auto-detect (no limit)

    @Override
    public void run(String... args) throws Exception {
        // Parse command line arguments
        String videoFile = DEFAULT_VIDEO_FILE;
        int expectedLedCount = DEFAULT_LED_COUNT;
        boolean debugMode = false;

        if (args.length >= 1) {
            videoFile = args[0];
        }
        if (args.length >= 2) {
            try {
                expectedLedCount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                logger.error("Invalid LED count '{}'. Must be a number.", args[1]);
                return;
            }
        }
        if (args.length >= 3 && "debug".equalsIgnoreCase(args[2])) {
            debugMode = true;
        }

        Path videoPath = Paths.get(videoFile).toAbsolutePath();
        
        logger.info("=== LED Position Analysis ===");
        logger.info("Input video: {}", videoPath);
        logger.info("Expected LED count: {}", expectedLedCount > 0 ? expectedLedCount : "auto-detect");
        logger.info("Debug mode: {}", debugMode ? "ENABLED" : "disabled");

        if (!videoPath.toFile().exists()) {
            logger.error("Video file not found: {}", videoPath);
            logger.info("Usage: ./gradlew bootRun --args=\"<video_file> <led_count>\"");
            logger.info("Example: ./gradlew bootRun --args=\"my_video.mov 100\"");
            return;
        }

        // Derive base name for output files
        String baseName = videoPath.getFileName().toString();
        if (baseName.contains(".")) {
            baseName = baseName.substring(0, baseName.lastIndexOf('.'));
        }

        try (VideoLoader loader = VideoLoader.open(videoPath)) {
            VideoMetadata metadata = loader.getMetadata();
            logger.info("Video: {}x{}, {} frames, {:.2f}s", 
                    metadata.width(), metadata.height(),
                    metadata.frameCount(), 
                    metadata.durationSeconds());

            LedSequenceAnalyzer analyzer = new LedSequenceAnalyzer();
            analyzer.setExpectedLedCount(expectedLedCount);
            
            // Enable debug video output if requested
            if (debugMode) {
                String debugFileName = baseName + "_debug.mp4";
                Path debugPath = Paths.get(debugFileName).toAbsolutePath();
                analyzer.setDebugVideoOutput(debugPath);
                logger.info("Debug video will be written to: {}", debugPath);
            }

            // Analyze the video
            logger.info("Starting analysis...");
            long startTime = System.currentTimeMillis();
            Map<Integer, Point> rawPositions = analyzer.analyze(loader);
            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Analysis completed in {}ms", elapsed);

            if (rawPositions.isEmpty()) {
                logger.error("No LEDs detected! Check the color detection thresholds.");
                return;
            }

            // Normalize positions
            Map<Integer, double[]> normalizedPositions = analyzer.normalizePositions(rawPositions);

            // Generate JavaScript output
            String jsOutput = analyzer.toJavaScriptArray(normalizedPositions);
            String jsRawOutput = analyzer.toJavaScriptArrayRaw(rawPositions);

            // Print to console
            logger.info("\n=== Normalized LED Positions (0-1 range) ===\n{}", jsOutput);
            logger.info("\n=== Raw LED Positions (camera pixels) ===\n{}", jsRawOutput);

            // Write to file
            String outputFileName = baseName + "_led_positions.js";
            Path outputPath = Paths.get(outputFileName).toAbsolutePath();
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath.toFile()))) {
                writer.println("// LED positions detected from " + videoPath.getFileName());
                writer.println("// Generated by LED Sequence Analyzer");
                writer.println("// Total LEDs: " + normalizedPositions.size());
                writer.println("// Expected LEDs: " + (expectedLedCount > 0 ? expectedLedCount : "auto-detect"));
                writer.println();
                writer.println("// Normalized positions (0,0 = bottom-left, 1,1 = top-right)");
                writer.println(jsOutput);
                writer.println();
                writer.println("// Raw camera positions (pixels)");
                writer.println(jsRawOutput);
            }
            logger.info("JavaScript output written to: {}", outputPath);

            // Generate HTML visualization
            String htmlFileName = baseName + "_visualization.html";
            Path htmlOutputPath = Paths.get(htmlFileName).toAbsolutePath();
            HtmlVisualizationGenerator htmlGenerator = new HtmlVisualizationGenerator();
            htmlGenerator.generate(normalizedPositions, htmlOutputPath, videoPath.getFileName().toString());
            logger.info("HTML visualization written to: {}", htmlOutputPath);
        }

        logger.info("=== Analysis Complete ===");
    }
}
