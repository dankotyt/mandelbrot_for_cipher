package com.cipher.core.utils;

import com.cipher.core.dto.MandelbrotParams;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;

@Getter
@Component
public class ImageUtils {

    @Setter
    private BufferedImage originalImage;
    private BufferedImage mandelbrotImage;
    private MandelbrotParams mandelbrotParams;

    public void setMandelbrotImage(BufferedImage image, MandelbrotParams params) {
        this.mandelbrotImage = image;
        this.mandelbrotParams = params;
    }

    public boolean hasOriginalImage() {
        return originalImage != null;
    }

    public boolean hasMandelbrotImage() {
        return mandelbrotImage != null;
    }

    public Image convertToFxImage(BufferedImage bufferedImage) {
        return SwingFXUtils.toFXImage(bufferedImage, null);
    }

    public static BufferedImage convertToARGB(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }

        BufferedImage argbImage = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = argbImage.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        return argbImage;
    }
    public static BufferedImage copyImage(BufferedImage source) {
        if (source == null) {
            throw new IllegalArgumentException("Source image cannot be null");
        }

        BufferedImage copy = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                source.getType()
        );

        Graphics2D g = copy.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();

        return copy;
    }
}
