package com.xmas.video;

/**
 * Exception thrown when a video file cannot be loaded.
 */
public class VideoLoadException extends RuntimeException {

    public VideoLoadException(String message) {
        super(message);
    }

    public VideoLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}

