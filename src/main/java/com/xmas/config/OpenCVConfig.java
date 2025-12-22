
package com.xmas.config;

import org.opencv.core.Core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class for OpenCV initialization and verification.
 */
@Configuration
public class OpenCVConfig {

    private static final Logger logger = LoggerFactory.getLogger(OpenCVConfig.class);

    @PostConstruct
    public void init() {
        logger.info("OpenCV Configuration initialized");
        logger.info("OpenCV Build Info: {}", Core.getBuildInformation());
    }
}

