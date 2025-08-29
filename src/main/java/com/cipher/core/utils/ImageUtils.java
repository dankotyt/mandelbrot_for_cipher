package com.cipher.core.utils;

import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;

@Component
public class ImageUtils {
    public static BufferedImage convertToType(BufferedImage image, int type) {
        if (image.getType() == type) {
            return image;
        }

        BufferedImage convertedImage = new BufferedImage(image.getWidth(), image.getHeight(), type);
        Graphics2D g2d = convertedImage.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        return convertedImage;
    }
}
