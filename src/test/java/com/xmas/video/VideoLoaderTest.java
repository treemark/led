package com.xmas.video;

import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VideoLoaderTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void loadOpenCV() {
        OpenCV.loadLocally();
    }

    @Test
    void throwsExceptionForNonExistentFile() {
        Path nonExistent = tempDir.resolve("does-not-exist.mp4");
        
        VideoLoadException exception = assertThrows(
                VideoLoadException.class,
                () -> VideoLoader.open(nonExistent)
        );
        
        assertTrue(exception.getMessage().contains("File not found"));
    }

    @Test
    void throwsExceptionForUnsupportedFormat() throws Exception {
        // Create a file with wrong extension
        Path wrongFormat = tempDir.resolve("video.avi");
        java.nio.file.Files.writeString(wrongFormat, "dummy content");
        
        VideoLoadException exception = assertThrows(
                VideoLoadException.class,
                () -> VideoLoader.open(wrongFormat)
        );
        
        assertTrue(exception.getMessage().contains("Unsupported format"));
    }

    @Test
    void canCreateAndReadTestVideo() {
        // Create a simple test video
        Path videoPath = tempDir.resolve("test.mp4");
        int width = 640;
        int height = 480;
        int frameCount = 30;
        double fps = 30.0;

        // Create test video
        createTestVideo(videoPath, width, height, frameCount, fps);

        // Load and verify
        try (VideoLoader loader = VideoLoader.open(videoPath)) {
            assertTrue(loader.isOpen());
            
            VideoMetadata metadata = loader.getMetadata();
            assertEquals(width, metadata.width());
            assertEquals(height, metadata.height());
            assertEquals(fps, metadata.fps(), 0.1);
            assertEquals(frameCount, metadata.frameCount());

            // Read first frame
            Mat frame = loader.readFrame();
            assertNotNull(frame);
            assertEquals(height, frame.rows());
            assertEquals(width, frame.cols());
            frame.release();
        }
    }

    @Test
    void canReadSpecificFrame() {
        Path videoPath = tempDir.resolve("test-seek.mp4");
        createTestVideo(videoPath, 320, 240, 60, 30.0);

        try (VideoLoader loader = VideoLoader.open(videoPath)) {
            Mat frame = loader.readFrame(30);
            assertNotNull(frame);
            assertEquals(240, frame.rows());
            assertEquals(320, frame.cols());
            frame.release();
        }
    }

    @Test
    void canStreamFrames() {
        Path videoPath = tempDir.resolve("test-stream.mp4");
        int expectedFrames = 10;
        createTestVideo(videoPath, 320, 240, expectedFrames, 30.0);

        try (VideoLoader loader = VideoLoader.open(videoPath)) {
            long count = loader.frames()
                    .peek(Mat::release)
                    .count();
            
            assertEquals(expectedFrames, count);
        }
    }

    @Test
    void metadataCalculations() {
        VideoMetadata metadata = new VideoMetadata(1920, 1080, 30.0, 300, 0);
        
        assertEquals(10.0, metadata.durationSeconds(), 0.01);
        assertEquals(10000, metadata.durationMillis());
        assertEquals(1920.0 / 1080.0, metadata.aspectRatio(), 0.01);
    }

    @Test
    void supportedFormatsIncludesMp4AndMov() {
        assertTrue(VideoLoader.isSupportedFormat("video.mp4"));
        assertTrue(VideoLoader.isSupportedFormat("video.MP4"));
        assertTrue(VideoLoader.isSupportedFormat("video.mov"));
        assertTrue(VideoLoader.isSupportedFormat("video.MOV"));
        assertTrue(VideoLoader.isSupportedFormat("my.video.file.mp4"));
        
        assertFalse(VideoLoader.isSupportedFormat("video.avi"));
        assertFalse(VideoLoader.isSupportedFormat("video.mkv"));
        assertFalse(VideoLoader.isSupportedFormat("video.wmv"));
    }

    @Test
    void canLoadMovFile() {
        // Create a MOV file (same codec works)
        Path videoPath = tempDir.resolve("test.mov");
        createTestVideo(videoPath, 320, 240, 10, 30.0);

        try (VideoLoader loader = VideoLoader.open(videoPath)) {
            assertTrue(loader.isOpen());
            assertEquals(320, loader.getMetadata().width());
            assertEquals(240, loader.getMetadata().height());
            
            Mat frame = loader.readFrame();
            assertNotNull(frame);
            frame.release();
        }
    }

    @Test
    void getSupportedExtensionsReturnsBothFormats() {
        var extensions = VideoLoader.getSupportedExtensions();
        assertTrue(extensions.contains(".mp4"));
        assertTrue(extensions.contains(".mov"));
    }

    private void createTestVideo(Path path, int width, int height, int frameCount, double fps) {
        int fourcc = VideoWriter.fourcc('m', 'p', '4', 'v');
        VideoWriter writer = new VideoWriter(
                path.toString(),
                fourcc,
                fps,
                new Size(width, height)
        );

        if (!writer.isOpened()) {
            // Fallback codec
            fourcc = VideoWriter.fourcc('a', 'v', 'c', '1');
            writer = new VideoWriter(path.toString(), fourcc, fps, new Size(width, height));
        }

        assertTrue(writer.isOpened(), "Failed to create test video");

        Mat frame = new Mat(height, width, CvType.CV_8UC3);
        for (int i = 0; i < frameCount; i++) {
            // Fill with different colors for each frame
            frame.setTo(new org.opencv.core.Scalar(i * 8 % 256, (i * 4) % 256, (i * 2) % 256));
            writer.write(frame);
        }

        frame.release();
        writer.release();
    }
}

