package com.cipher.core.encryption;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

@Component
public class XOR {
    protected static BufferedImage performXOR(BufferedImage image1, BufferedImage image2) {
        int width = image1.getWidth();
        int height = image1.getHeight();
        BufferedImage resultImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb1 = image1.getRGB(x, y);
                int rgb2 = image2.getRGB(x, y);
                int xorRGB = rgb1 ^ rgb2;
                resultImage.setRGB(x, y, xorRGB);
            }
        }
        return resultImage;
    }
}
