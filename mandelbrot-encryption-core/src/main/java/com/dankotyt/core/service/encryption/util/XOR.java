package com.dankotyt.core.service.encryption.util;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

@Component
public class XOR {
    public static BufferedImage performXOR(BufferedImage image1, BufferedImage image2) {
        if (image1 == null || image2 == null) {
            throw new IllegalArgumentException("Images cannot be null");
        }
        if (image1.getWidth() != image2.getWidth() || image1.getHeight() != image2.getHeight()) {
            throw new IllegalArgumentException("Images must have the same dimensions");
        }
        int width = image1.getWidth();
        int height = image1.getHeight();

        // Используем тип с альфа-каналом
        BufferedImage resultImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb1 = image1.getRGB(x, y);
                int rgb2 = image2.getRGB(x, y);

                // Сохраняем альфа-канал из исходного изображения
                int alpha = (rgb1 >> 24) & 0xFF;

                // Применяем XOR только к RGB компонентам
                int xorRGB = (rgb1 & 0x00FFFFFF) ^ (rgb2 & 0x00FFFFFF);

                // Восстанавливаем альфа-канал
                xorRGB = (alpha << 24) | (xorRGB & 0x00FFFFFF);

                resultImage.setRGB(x, y, xorRGB);
            }
        }
        return resultImage;
    }
}
