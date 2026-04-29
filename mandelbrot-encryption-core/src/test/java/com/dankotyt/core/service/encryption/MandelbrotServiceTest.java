package com.dankotyt.core.service.encryption;

import com.dankotyt.core.dto.MandelbrotParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class MandelbrotServiceTest {

    private MandelbrotService service;

    @BeforeEach
    void setUp() {
        service = new MandelbrotService();
    }

    @Test
    void generateParams_shouldProduceValidValues() throws Exception {
        SecureRandom prng = SecureRandom.getInstance("SHA1PRNG");
        MandelbrotParams params = service.generateParams(prng);
        assertTrue(params.zoom() >= 10_000);
        assertTrue(params.zoom() <= 10_000 + 700 * 140);
        assertTrue(params.offsetX() >= -0.9998);
        assertTrue(params.offsetX() <= 0.45);
        boolean valid = (params.offsetY() >= -0.7 && params.offsetY() <= -0.1) ||
                (params.offsetY() >= 0.1 && params.offsetY() <= 0.7);
        assertTrue(valid);
        assertTrue(params.maxIter() >= 250);
        assertTrue(params.maxIter() <= 250 + 100 * 10);
    }

    @Test
    void generateImage_shouldCreateImageOfCorrectSize() {
        BufferedImage img = service.generateImage(100, 80, 10000, -0.5, 0.0, 250);
        assertNotNull(img);
        assertEquals(100, img.getWidth());
        assertEquals(80, img.getHeight());
    }

    @Test
    void isFractalValid_withValidFractal_shouldReturnTrue() {
        // Создаём изображение с равномерным распределением цветов
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 100; y++) {
            for (int x = 0; x < 100; x++) {
                int hue = (x * 36 / 100) % 36; // 36 бинов
                int rgb = Color.HSBtoRGB(hue / 36f, 1.0f, 1.0f);
                img.setRGB(x, y, rgb);
            }
        }
        assertTrue(service.isFractalValid(img));
    }

    @Test
    void isFractalValid_withTooManyDarkBlue_shouldReturnFalse() {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < 100; i++) {
            if (i < 30) {
                img.setRGB(i % 10, i / 10, 0x000040);
            } else {
                img.setRGB(i % 10, i / 10, 0xFF0000);
            }
        }
        assertFalse(service.isFractalValid(img));
    }

    @Test
    void isFractalValid_withPoorDistribution_shouldReturnFalse() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        int rgb = Color.HSBtoRGB(0.0f, 1.0f, 1.0f);
        for (int y = 0; y < 100; y++) {
            for (int x = 0; x < 100; x++) {
                img.setRGB(x, y, rgb);
            }
        }
        assertFalse(service.isFractalValid(img));
    }

    @Test
    void isFractalValid_withNoConsideredPixels_shouldReturnFalse() {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                img.setRGB(x, y, 0x000040);
            }
        }
        assertFalse(service.isFractalValid(img));
    }

    @Test
    void isFractalValid_withBlackPixels_shouldExcludeThem() throws Exception {
        BufferedImage img = new BufferedImage(360, 360, BufferedImage.TYPE_INT_RGB);
        // заполняем 360*360 = 129600 пикселей
        // 36 оттенков, каждый по 3600 пикселей (3600/129600 ≈ 2.78%)
        for (int y = 0; y < 360; y++) {
            for (int x = 0; x < 360; x++) {
                // hue от 0 до 1 с шагом 1/36
                float hue = ((x % 36) / 36.0f);
                int rgb = Color.HSBtoRGB(hue, 1.0f, 1.0f);
                // чередуем чёрные пиксели: каждый второй пиксель чёрный
                if ((x + y) % 2 == 0) {
                    img.setRGB(x, y, 0x000000);
                } else {
                    img.setRGB(x, y, rgb);
                }
            }
        }
        assertTrue(service.isFractalValid(img));
    }

    @Test
    void isFractalValid_withOnlyBlackAndDarkBlue_shouldReturnFalse() {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < 100; i++) {
            if (i < 50) {
                img.setRGB(i % 10, i / 10, 0x000040);
            } else {
                img.setRGB(i % 10, i / 10, 0x000000);
            }
        }
        assertFalse(service.isFractalValid(img));
    }

    @Test
    void generateImage_withInterruptedException_shouldHandleGracefully() {
        // Не можем легко сгенерировать InterruptedException, но проверим, что метод не падает
        assertDoesNotThrow(() -> {
            service.generateImage(100, 100, 10000, -0.5, 0.0, 250);
        });
    }

    @Test
    void setTargetWidth_shouldSetValue() {
        int expectedWidth = 800;
        service.setTargetWidth(expectedWidth);
        assertEquals(expectedWidth, service.getTargetWidth());
    }

    @Test
    void setTargetHeight_shouldSetValue() {
        int expectedHeight = 600;
        service.setTargetHeight(expectedHeight);
        assertEquals(expectedHeight, service.getTargetHeight());
    }

    @Test
    void targetWidth_defaultValue_shouldBeZero() {
        assertEquals(0, service.getTargetWidth());
    }

    @Test
    void targetHeight_defaultValue_shouldBeZero() {
        assertEquals(0, service.getTargetHeight());
    }

    @Test
    void setTargetWidth_withNegativeValue_shouldSet() {
        service.setTargetWidth(-100);
        assertEquals(-100, service.getTargetWidth());
    }

    @Test
    void setTargetHeight_withNegativeValue_shouldSet() {
        service.setTargetHeight(-200);
        assertEquals(-200, service.getTargetHeight());
    }

    @Test
    void setTargetWidth_and_setTargetHeight_shouldWorkIndependently() {
        service.setTargetWidth(1024);
        service.setTargetHeight(768);

        assertEquals(1024, service.getTargetWidth());
        assertEquals(768, service.getTargetHeight());
    }

    @Test
    void offsetY_bothRangesOccur() {
        SecureRandom prng = new SecureRandom();
        boolean seenNegative = false, seenPositive = false;
        for (int i = 0; i < 1000; i++) {
            MandelbrotParams p = service.generateParams(prng);
            if (p.offsetY() >= -0.7 && p.offsetY() <= -0.1) seenNegative = true;
            if (p.offsetY() >= 0.1 && p.offsetY() <= 0.7) seenPositive = true;
            if (seenNegative && seenPositive) break;
        }
        assertTrue(seenNegative && seenPositive, "Оба диапазона offsetY должны быть представлены");
    }

    @Test
    void generateParams_boundaryZoom() {
        SecureRandom prng = new SecureRandom();
        // Проверка, что zoom всегда положительный и в диапазоне
        for (int i = 0; i < 100; i++) {
            MandelbrotParams params = service.generateParams(prng);
            assertTrue(params.zoom() > 0);
            assertTrue(params.zoom() >= 10_000);
            assertTrue(params.zoom() <= 10_000 + 700 * 140);
        }
    }

    @Test
    void generateParams_negativeValuesNotProduced() {
        SecureRandom prng = new SecureRandom();
        for (int i = 0; i < 100; i++) {
            MandelbrotParams params = service.generateParams(prng);
            assertTrue(params.zoom() > 0);
            assertTrue(params.maxIter() > 0);
            // offsetX и offsetY могут быть отрицательными – это нормально
            assertTrue(params.offsetX() >= -1.0 && params.offsetX() <= 0.5);
            assertTrue(params.offsetY() >= -0.8 && params.offsetY() <= 0.8);
        }
    }

    @Test
    void generateImage_withNegativeWidth_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.generateImage(-100, 100, 10000, -0.5, 0.0, 250);
        });
    }

    @Test
    void generateImage_withZeroWidth_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.generateImage(0, 100, 10000, -0.5, 0.0, 250);
        });
    }

    @Test
    void generateImage_withZeroZoom_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                service.generateImage(100, 100, 0, -0.5, 0.0, 250));
    }

    @Test
    void generateImage_withNegativeZoom_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                service.generateImage(100, 100, -1000, -0.5, 0.0, 250));
    }

    @Test
    void generateImage_withZeroMaxIter_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                service.generateImage(100, 100, 10000, -0.5, 0.0, 0));
    }

    @Test
    void generateImage_withNegativeMaxIter_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                service.generateImage(100, 100, 10000, -0.5, 0.0, -100));
    }

    @Test
    void generateParams_withNullPrng_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                service.generateParams(null));
    }
}