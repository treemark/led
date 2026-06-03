package com.xmas.video;

import org.opencv.core.Mat;
import org.opencv.highgui.HighGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

/**
 * Simple video player using Swing for display.
 */
public class VideoPlayer {

    private static final Logger logger = LoggerFactory.getLogger(VideoPlayer.class);

    private final VideoLoader loader;
    private final String windowName;
    private volatile boolean playing = false;
    private volatile boolean paused = false;
    private volatile boolean windowClosed = false;

    public VideoPlayer(VideoLoader loader) {
        this(loader, "Video Player");
    }

    public VideoPlayer(VideoLoader loader, String windowName) {
        this.loader = loader;
        this.windowName = windowName;
    }

    /**
     * Plays the video in a window.
     * Press 'q' or ESC to quit, SPACE to pause/resume.
     */
    public void play() {
        VideoMetadata metadata = loader.getMetadata();
        logger.info("Playing: {} ({}x{}, {} fps, {}s)", 
                loader.getFilePath().getFileName(),
                metadata.width(), 
                metadata.height(),
                String.format("%.2f", metadata.fps()),
                String.format("%.1f", metadata.durationSeconds()));

        // Calculate frame delay in milliseconds
        int frameDelay = (int) (1000.0 / metadata.fps());
        if (frameDelay < 1) frameDelay = 1;

        playing = true;
        loader.reset();

        // Create Swing window
        JFrame frame = new JFrame(windowName);
        ImagePanel imagePanel = new ImagePanel();
        frame.add(imagePanel);
        frame.setSize(metadata.width(), metadata.height() + 30);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                windowClosed = true;
                playing = false;
            }
        });
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKey(e.getKeyCode());
            }
        });
        frame.setVisible(true);

        int frameNumber = 0;
        try {
            Mat mat;
            while (playing && !windowClosed && (mat = loader.readFrame()) != null) {
                frameNumber++;
                
                // Skip empty frames
                if (mat.empty() || mat.rows() == 0 || mat.cols() == 0) {
                    logger.warn("Empty frame at position {}", frameNumber);
                    mat.release();
                    continue;
                }
                
                BufferedImage image = matToBufferedImage(mat);
                imagePanel.setImage(image);
                imagePanel.repaint();
                mat.release();

                // Handle pause
                while (paused && playing && !windowClosed) {
                    Thread.sleep(100);
                }
                
                if (playing && !windowClosed) {
                    Thread.sleep(frameDelay);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            frame.dispose();
        }

        logger.info("Playback finished after {} frames", frameNumber);
    }

    /**
     * Stops playback.
     */
    public void stop() {
        playing = false;
    }

    /**
     * Toggles pause state.
     */
    public void togglePause() {
        paused = !paused;
        logger.info(paused ? "Paused" : "Resumed");
    }

    private void handleKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_Q, KeyEvent.VK_ESCAPE -> stop();
            case KeyEvent.VK_SPACE -> togglePause();
        }
    }

    /**
     * Converts an OpenCV Mat to a BufferedImage.
     */
    private BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        mat.get(0, 0, data);
        return image;
    }

    /**
     * Displays a single frame and waits for a key press.
     */
    public static void showFrame(Mat mat, String windowName) {
        HighGui.imshow(windowName, mat);
        HighGui.waitKey(0);
        HighGui.destroyWindow(windowName);
    }

    /**
     * Simple panel for displaying images.
     */
    private static class ImagePanel extends JPanel {
        private BufferedImage image;

        public void setImage(BufferedImage image) {
            this.image = image;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) {
                g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            }
        }
    }
}

