package com.xmas;

import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class XmasApplication {

    private static final Logger logger = LoggerFactory.getLogger(XmasApplication.class);

    public static void main(String[] args) {
        // Load OpenCV native library
        OpenCV.loadLocally();
        logger.info("OpenCV loaded successfully. Version: {}", Core.VERSION);
        
        SpringApplication.run(XmasApplication.class, args);
    }
}

