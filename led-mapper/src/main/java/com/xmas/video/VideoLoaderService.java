package com.xmas.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Spring service for loading and managing video files.
 * Supported formats: MP4, MOV
 */
@Service
public class VideoLoaderService {

    private static final Logger logger = LoggerFactory.getLogger(VideoLoaderService.class);

    /**
     * Loads a video file (MP4 or MOV).
     *
     * @param filePath path to the video file
     * @return VideoLoader for reading frames
     * @throws VideoLoadException if the file cannot be loaded
     */
    public VideoLoader load(Path filePath) {
        logger.info("Loading video: {}", filePath);
        return VideoLoader.open(filePath);
    }

    /**
     * Loads a video file from a string path.
     *
     * @param filePath path to the video file
     * @return VideoLoader for reading frames
     * @throws VideoLoadException if the file cannot be loaded
     */
    public VideoLoader load(String filePath) {
        return load(Path.of(filePath));
    }

    /**
     * Loads an MP4 video file.
     *
     * @param filePath path to the MP4 file
     * @return VideoLoader for reading frames
     * @throws VideoLoadException if the file cannot be loaded
     */
    public VideoLoader loadMp4(Path filePath) {
        logger.info("Loading MP4 video: {}", filePath);
        return VideoLoader.open(filePath);
    }

    /**
     * Loads an MP4 video file from a string path.
     *
     * @param filePath path to the MP4 file
     * @return VideoLoader for reading frames
     * @throws VideoLoadException if the file cannot be loaded
     */
    public VideoLoader loadMp4(String filePath) {
        return loadMp4(Path.of(filePath));
    }

    /**
     * Loads a MOV video file.
     *
     * @param filePath path to the MOV file
     * @return VideoLoader for reading frames
     * @throws VideoLoadException if the file cannot be loaded
     */
    public VideoLoader loadMov(Path filePath) {
        logger.info("Loading MOV video: {}", filePath);
        return VideoLoader.open(filePath);
    }

    /**
     * Loads a MOV video file from a string path.
     *
     * @param filePath path to the MOV file
     * @return VideoLoader for reading frames
     * @throws VideoLoadException if the file cannot be loaded
     */
    public VideoLoader loadMov(String filePath) {
        return loadMov(Path.of(filePath));
    }

    /**
     * Checks if a file can be loaded as a video.
     *
     * @param filePath path to check
     * @return true if the file exists and is a supported format
     */
    public boolean canLoad(Path filePath) {
        try (VideoLoader loader = VideoLoader.openAny(filePath)) {
            return loader.isOpen();
        } catch (Exception e) {
            logger.debug("Cannot load video {}: {}", filePath, e.getMessage());
            return false;
        }
    }
}

