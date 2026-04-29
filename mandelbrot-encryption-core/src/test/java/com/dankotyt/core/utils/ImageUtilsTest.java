package com.dankotyt.core.utils;

import com.dankotyt.core.dto.MandelbrotParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class ImageUtilsTest {

    private ImageUtils imageUtils;

    @BeforeEach
    void setUp() {
        imageUtils = new ImageUtils();
    }

    @Test
    void setOriginalImage_shouldStoreImage() {
        BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        imageUtils.setOriginalImage(image);

        assertTrue(imageUtils.hasOriginalImage());
        assertSame(image, imageUtils.getOriginalImage());
    }

    @Test
    void hasOriginalImage_whenNotSet_shouldReturnFalse() {
        assertFalse(imageUtils.hasOriginalImage());
    }

    @Test
    void setMandelbrotImage_shouldStoreImageAndParams() {
        BufferedImage image = new BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB);
        MandelbrotParams params = new MandelbrotParams(10000, -0.5, 0.2, 250);

        imageUtils.setMandelbrotImage(image, params);

        assertTrue(imageUtils.hasMandelbrotImage());
        assertSame(image, imageUtils.getMandelbrotImage());
        assertSame(params, imageUtils.getMandelbrotParams());
    }

    @Test
    void hasMandelbrotImage_whenNotSet_shouldReturnFalse() {
        assertFalse(imageUtils.hasMandelbrotImage());
    }

    @Test
    void convertToARGB_whenAlreadyARGB_shouldReturnSame() {
        BufferedImage argbImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);

        BufferedImage result = ImageUtils.convertToARGB(argbImage);

        assertSame(argbImage, result);
    }

    @Test
    void convertToARGB_whenNotARGB_shouldConvert() {
        BufferedImage rgbImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

        BufferedImage result = ImageUtils.convertToARGB(rgbImage);

        assertNotNull(result);
        assertEquals(BufferedImage.TYPE_INT_ARGB, result.getType());
        assertEquals(100, result.getWidth());
        assertEquals(100, result.getHeight());
    }

    @Test
    void imageToBytes_shouldConvertCorrectly() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFF112233);
        image.setRGB(1, 0, 0xFF445566);
        image.setRGB(0, 1, 0xFF778899);
        image.setRGB(1, 1, 0xFFAABBCC);

        byte[] bytes = imageUtils.imageToBytes(image);

        assertEquals(2 * 2 * 3, bytes.length);

        // Проверяем первый пиксель
        assertEquals(0x11, bytes[0] & 0xFF);
        assertEquals(0x22, bytes[1] & 0xFF);
        assertEquals(0x33, bytes[2] & 0xFF);
    }

    @Test
    void bytesToImage_shouldConvertCorrectly() {
        byte[] bytes = new byte[2 * 2 * 3];
        bytes[0] = 0x11;
        bytes[1] = 0x22;
        bytes[2] = 0x33;
        bytes[3] = 0x44;
        bytes[4] = 0x55;
        bytes[5] = 0x66;
        bytes[6] = 0x77;
        bytes[7] = (byte) 0x88;
        bytes[8] = (byte) 0x99;
        bytes[9] = (byte) 0xAA;
        bytes[10] = (byte) 0xBB;
        bytes[11] = (byte) 0xCC;

        BufferedImage image = imageUtils.bytesToImage(bytes, 2, 2);

        assertNotNull(image);
        assertEquals(2, image.getWidth());
        assertEquals(2, image.getHeight());
        assertEquals(0xFF112233, image.getRGB(0, 0));
        assertEquals(0xFF445566, image.getRGB(1, 0));
        assertEquals(0xFF778899, image.getRGB(0, 1));
        assertEquals(0xFFAABBCC, image.getRGB(1, 1));
    }

    @Test
    void bytesToImage_withInvalidLength_shouldThrow() {
        byte[] bytes = new byte[10];

        assertThrows(IllegalArgumentException.class, () -> {
            imageUtils.bytesToImage(bytes, 2, 2);
        });
    }

    @Test
    void imageToBytes_and_bytesToImage_shouldBeReversible() {
        BufferedImage original = new BufferedImage(50, 40, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 50; x++) {
                original.setRGB(x, y, (y * 50 + x) & 0x00FFFFFF);
            }
        }

        byte[] bytes = imageUtils.imageToBytes(original);
        BufferedImage reconstructed = imageUtils.bytesToImage(bytes, 50, 40);

        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 50; x++) {
                assertEquals(original.getRGB(x, y), reconstructed.getRGB(x, y));
            }
        }
    }
}