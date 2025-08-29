package com.cipher.core.encryption;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

@Component
public class XOR {
    protected static BufferedImage performXOR(BufferedImage image1, BufferedImage image2) {
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
