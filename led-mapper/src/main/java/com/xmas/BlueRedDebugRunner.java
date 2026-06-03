package com.xmas;

import com.xmas.analysis.BlueRedDebugger;
import com.xmas.video.VideoLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Debug runner for blue/red LED detection only.
 * 
 * Usage: ./gradlew bootRun --args="--debug-bluered <video_file> [frames]"
 * Example: ./gradlew bootRun --args="--debug-bluered IMG_0294.MOV 5"
 */
@Component
@Order(0) // Run before LedAnalysisRunner
public class BlueRedDebugRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(BlueRedDebugRunner.class);

    @Override
    public void run(String... args) throws Exception {
        // Check if this is a blue/red debug run
        if (args.length < 2 || !"--debug-bluered".equals(args[0])) {
            return; // Not a debug run, let normal runner handle it
        }

        String videoFile = args[1];
        int framesToAnalyze = 10; // default

        if (args.length >= 3) {
            try {
                framesToAnalyze = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                logger.error("Invalid frame count '{}'. Using default 10.", args[2]);
            }
        }

        Path videoPath = Paths.get(videoFile).toAbsolutePath();
        Path outputDir = Paths.get("debug_output").toAbsolutePath();

        logger.info("=== BLUE/RED LED DEBUG MODE ===");
        logger.info("Video: {}", videoPath);
        logger.info("Frames to analyze: {}", framesToAnalyze);
        logger.info("Output directory: {}", outputDir);

        if (!videoPath.toFile().exists()) {
            logger.error("Video file not found: {}", videoPath);
            System.exit(1);
        }

        try (VideoLoader loader = VideoLoader.open(videoPath)) {
            BlueRedDebugger debugger = new BlueRedDebugger(outputDir);
            debugger.analyzeFrames(loader, framesToAnalyze);
        }

        logger.info("Debug images saved to: {}", outputDir);
        logger.info("=== DEBUG COMPLETE ===");
        
        // Exit after debug to prevent normal analysis from running
        System.exit(0);
    }
}

