package com.xmas;

import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class XmasApplicationTests {

    @BeforeAll
    static void loadOpenCV() {
        OpenCV.loadLocally();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void openCVLoads() {
        assertNotNull(Core.VERSION);
        System.out.println("OpenCV Version: " + Core.VERSION);
    }

    @Test
    void canCreateMat() {
        Mat mat = new Mat(100, 100, CvType.CV_8UC3);
        assertEquals(100, mat.rows());
        assertEquals(100, mat.cols());
        assertEquals(3, mat.channels());
        mat.release();
    }
}

