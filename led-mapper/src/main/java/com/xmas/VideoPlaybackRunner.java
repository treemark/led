package com.xmas;

import com.xmas.video.VideoLoader;
import com.xmas.video.VideoMetadata;
import com.xmas.video.VideoPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Command line runner to play a video file on startup.
 * Disabled by default - enable by uncommenting @Component
 */
// @Component  // Disabled - using LedAnalysisRunner instead
public class VideoPlaybackRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(VideoPlaybackRunner.class);

    // Video file to play
    private static final String VIDEO_FILE = "IMG_0285.mov";

    @Override
    public void run(String... args) throws Exception {
        Path videoPath = Paths.get(VIDEO_FILE).toAbsolutePath();
        logger.info("Loading video: {}", videoPath);

        try (VideoLoader loader = VideoLoader.open(videoPath)) {
            VideoMetadata metadata = loader.getMetadata();
            
            logger.info("=== Video Information ===");
            logger.info("  File: {}", videoPath.getFileName());
            logger.info("  Resolution: {}x{}", metadata.width(), metadata.height());
            logger.info("  Frame Rate: {} fps", String.format("%.2f", metadata.fps()));
            logger.info("  Duration: {} seconds", String.format("%.2f", metadata.durationSeconds()));
            logger.info("  Total Frames: {}", metadata.frameCount());
            logger.info("  Codec: {}", metadata.fourccString());
            logger.info("  Aspect Ratio: {}", String.format("%.2f", metadata.aspectRatio()));
            logger.info("=========================");
            
            // Test reading the first frame
            logger.info("Testing frame read...");
            var testFrame = loader.readFrame();
            if (testFrame == null || testFrame.empty()) {
                logger.error("Cannot read frames from video! The codec may not be supported.");
                logger.error("This OpenCV build uses AVFoundation. Try converting the video:");
                logger.error("  ffmpeg -i {} -c:v libx264 -crf 23 output.mp4", videoPath.getFileName());
                if (testFrame != null) testFrame.release();
                return;
            }
            logger.info("First frame read successfully: {}x{}", testFrame.cols(), testFrame.rows());
            testFrame.release();
            
            loader.reset();
            logger.info("Starting playback... (Press 'q' or ESC to quit, SPACE to pause)");
            
            VideoPlayer player = new VideoPlayer(loader, "IMG_0285.mov");
            player.play();
        }
        
        logger.info("Video playback complete.");
    }
}

