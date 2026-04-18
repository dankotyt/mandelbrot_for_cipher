package com.dankotyt.core.service.encryption;

import com.dankotyt.core.service.encryption.util.XOR;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XORTest {

    @Test
    @DisplayName("XOR of two images should apply XOR to RGB components")
    void performXOR_withTwoImages_shouldXorRGB() {
        BufferedImage img1 = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        BufferedImage img2 = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        // Используем TYPE_INT_RGB, чтобы избежать проблем с альфа-каналом
        img1.setRGB(0, 0, 0x112233);
        img2.setRGB(0, 0, 0x445566);
        BufferedImage result = XOR.performXOR(img1, img2);

        int actual = result.getRGB(0, 0) & 0x00FFFFFF; // Убираем альфа-канал
        int expected = (0x112233 ^ 0x445566) & 0x00FFFFFF;
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("XOR should preserve alpha channel from first image")
    void performXOR_withAlpha_shouldPreserveAlphaFromFirst() {
        BufferedImage img1 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        BufferedImage img2 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img1.setRGB(0, 0, 0x80112233);
        img2.setRGB(0, 0, 0xFF445566);
        int result = XOR.performXOR(img1, img2).getRGB(0, 0);
        int expected = (0x80 << 24) | ((0x112233 ^ 0x445566) & 0x00FFFFFF);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("XOR with different image sizes should throw")
    void performXOR_withDifferentSizes_shouldThrow() {
        BufferedImage img1 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        BufferedImage img2 = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        assertThrows(IllegalArgumentException.class, () -> XOR.performXOR(img1, img2));
    }
}