package com.xmas.video;

/**
 * Metadata extracted from a video file.
 *
 * @param width      frame width in pixels
 * @param height     frame height in pixels
 * @param fps        frames per second
 * @param frameCount total number of frames
 * @param fourcc     four character code identifying the codec
 */
public record VideoMetadata(
        int width,
        int height,
        double fps,
        long frameCount,
        int fourcc
) {
    /**
     * Returns the duration of the video in seconds.
     */
    public double durationSeconds() {
        if (fps <= 0) return 0;
        return frameCount / fps;
    }

    /**
     * Returns the duration of the video in milliseconds.
     */
    public long durationMillis() {
        return (long) (durationSeconds() * 1000);
    }

    /**
     * Returns the aspect ratio (width / height).
     */
    public double aspectRatio() {
        if (height == 0) return 0;
        return (double) width / height;
    }

    /**
     * Returns the fourcc codec as a readable string.
     */
    public String fourccString() {
        return new String(new char[]{
                (char) (fourcc & 0xFF),
                (char) ((fourcc >> 8) & 0xFF),
                (char) ((fourcc >> 16) & 0xFF),
                (char) ((fourcc >> 24) & 0xFF)
        });
    }

    @Override
    public String toString() {
        return String.format("VideoMetadata[%dx%d, %.2f fps, %d frames (%.2fs), codec=%s]",
                width, height, fps, frameCount, durationSeconds(), fourccString());
    }
}

