package com.xmas.video;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Loads and provides access to video frames from video files.
 * Supported formats: MP4, MOV
 */
public class VideoLoader implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(VideoLoader.class);
    
    /** Supported video file extensions */
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".mp4", ".mov");

    private final VideoCapture capture;
    private final Path filePath;
    private final VideoMetadata metadata;
    private boolean closed = false;

    private VideoLoader(Path filePath, VideoCapture capture) {
        this.filePath = filePath;
        this.capture = capture;
        this.metadata = extractMetadata(capture);
        logger.info("Loaded video: {} - {}x{} @ {:.2f} fps, {} frames",
                filePath.getFileName(),
                metadata.width(),
                metadata.height(),
                metadata.fps(),
                metadata.frameCount());
    }

    /**
     * Opens a video file for reading.
     * Supported formats: MP4, MOV
     *
     * @param filePath path to the video file
     * @return VideoLoader instance
     * @throws VideoLoadException if the file cannot be opened or format is unsupported
     */
    public static VideoLoader open(Path filePath) {
        if (!Files.exists(filePath)) {
            throw new VideoLoadException("File not found: " + filePath);
        }

        String fileName = filePath.getFileName().toString().toLowerCase();
        if (!isSupportedFormat(fileName)) {
            throw new VideoLoadException("Unsupported format. Expected " + SUPPORTED_EXTENSIONS + " file: " + filePath);
        }

        VideoCapture capture = new VideoCapture(filePath.toString());
        if (!capture.isOpened()) {
            throw new VideoLoadException("Failed to open video file: " + filePath);
        }

        return new VideoLoader(filePath, capture);
    }
    
    /**
     * Checks if a filename has a supported video extension.
     *
     * @param fileName the filename to check (case-insensitive)
     * @return true if the format is supported
     */
    public static boolean isSupportedFormat(String fileName) {
        String lowerName = fileName.toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }
    
    /**
     * Returns the list of supported file extensions.
     *
     * @return list of supported extensions (e.g., ".mp4", ".mov")
     */
    public static List<String> getSupportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    /**
     * Opens a video file, accepting any format supported by OpenCV.
     *
     * @param filePath path to the video file
     * @return VideoLoader instance
     * @throws VideoLoadException if the file cannot be opened
     */
    public static VideoLoader openAny(Path filePath) {
        if (!Files.exists(filePath)) {
            throw new VideoLoadException("File not found: " + filePath);
        }

        VideoCapture capture = new VideoCapture(filePath.toString());
        if (!capture.isOpened()) {
            throw new VideoLoadException("Failed to open video file: " + filePath);
        }

        return new VideoLoader(filePath, capture);
    }

    /**
     * Reads the next frame from the video.
     *
     * @return the next frame, or null if no more frames available or frame is empty
     */
    public Mat readFrame() {
        ensureOpen();
        Mat frame = new Mat();
        if (capture.read(frame) && !frame.empty()) {
            return frame;
        }
        frame.release();
        return null;
    }

    /**
     * Reads a specific frame by index.
     *
     * @param frameIndex zero-based frame index
     * @return the frame at the specified index
     * @throws IllegalArgumentException if frame index is out of bounds
     */
    public Mat readFrame(long frameIndex) {
        ensureOpen();
        if (frameIndex < 0 || frameIndex >= metadata.frameCount()) {
            throw new IllegalArgumentException(
                    "Frame index out of bounds: " + frameIndex + " (total frames: " + metadata.frameCount() + ")");
        }

        capture.set(Videoio.CAP_PROP_POS_FRAMES, frameIndex);
        return readFrame();
    }

    /**
     * Returns a stream of all frames in the video.
     * Note: Frames should be released after use to avoid memory leaks.
     *
     * @return stream of Mat frames
     */
    public Stream<Mat> frames() {
        ensureOpen();
        // Reset to beginning
        capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);

        Iterator<Mat> frameIterator = new Iterator<>() {
            private Mat nextFrame = null;
            private boolean hasNextCalled = false;

            @Override
            public boolean hasNext() {
                if (!hasNextCalled) {
                    nextFrame = readFrame();
                    hasNextCalled = true;
                }
                return nextFrame != null;
            }

            @Override
            public Mat next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No more frames");
                }
                hasNextCalled = false;
                Mat result = nextFrame;
                nextFrame = null;
                return result;
            }
        };

        Spliterator<Mat> spliterator = Spliterators.spliterator(
                frameIterator,
                metadata.frameCount(),
                Spliterator.ORDERED | Spliterator.SIZED | Spliterator.NONNULL
        );

        return StreamSupport.stream(spliterator, false);
    }

    /**
     * Gets the video metadata.
     */
    public VideoMetadata getMetadata() {
        return metadata;
    }

    /**
     * Gets the file path.
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Checks if the video is still open.
     */
    public boolean isOpen() {
        return !closed && capture.isOpened();
    }

    /**
     * Resets playback to the beginning of the video.
     */
    public void reset() {
        ensureOpen();
        capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
    }

    /**
     * Seeks to a specific timestamp in the video.
     *
     * @param milliseconds position in milliseconds from the start
     */
    public void seekTo(double milliseconds) {
        ensureOpen();
        capture.set(Videoio.CAP_PROP_POS_MSEC, milliseconds);
    }

    @Override
    public void close() {
        if (!closed) {
            capture.release();
            closed = true;
            logger.debug("Closed video: {}", filePath.getFileName());
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("VideoLoader has been closed");
        }
    }

    private static VideoMetadata extractMetadata(VideoCapture capture) {
        return new VideoMetadata(
                (int) capture.get(Videoio.CAP_PROP_FRAME_WIDTH),
                (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT),
                capture.get(Videoio.CAP_PROP_FPS),
                (long) capture.get(Videoio.CAP_PROP_FRAME_COUNT),
                (int) capture.get(Videoio.CAP_PROP_FOURCC)
        );
    }
}

