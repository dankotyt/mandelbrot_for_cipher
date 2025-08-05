package com.cipher.core.encryption;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.awt.Graphics2D;

import com.cipher.core.dto.KeyDecoderParams;
import com.cipher.core.utils.BinaryFile;
import com.cipher.core.utils.ImageUtils;
import com.cipher.core.utils.Mandelbrot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageDecrypt {
    private static final Logger logger = LoggerFactory.getLogger(ImageDecrypt.class);

    private static String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private static String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    public static void decryptImage(String keyFilePath) {
        try {
            KeyDecoderParams keyDecoderParams = BinaryFile.loadKeyDecoderFromBinaryFile(keyFilePath);

            int startMandelbrotWidth = keyDecoderParams.startMandelbrotWidth();
            int startMandelbrotHeight = keyDecoderParams.startMandelbrotHeight();
            double zoom = keyDecoderParams.zoom();
            double offsetX = keyDecoderParams.offsetX();
            double offsetY = keyDecoderParams.offsetY();
            int maxIter = keyDecoderParams.maxIter();
            int segmentWidthSize = keyDecoderParams.segmentWidthSize();
            int segmentHeightSize = keyDecoderParams.segmentHeightSize();
            Map<Integer, Integer> segmentMapping = keyDecoderParams.segmentMapping();
            int startX = keyDecoderParams.startX();
            int startY = keyDecoderParams.startY();
            int width = keyDecoderParams.width();
            int height = keyDecoderParams.height();

            BufferedImage encryptedImage = ImageIO.read(new File(getTempPath() + "input.png"));

            BufferedImage encryptedSelectedArea = encryptedImage.getSubimage(startX, startY, width, height);

            BufferedImage unshuffledSelectedImage = ImageSegmentShuffler.unshuffledSegments(encryptedSelectedArea,
                    segmentMapping, segmentWidthSize, segmentHeightSize);

            Mandelbrot mandelbrotImage = new Mandelbrot(startMandelbrotWidth, startMandelbrotHeight);
            BufferedImage mandelbrotBufferedImage = mandelbrotImage.generateImage(startMandelbrotWidth,
                    startMandelbrotHeight, zoom, offsetX, offsetY, maxIter);

            BufferedImage selectedMandelbrotImage = mandelbrotBufferedImage.getSubimage(startX, startY, width, height);

            // Преобразование типов изображений в BufferedImage.TYPE_INT_RGB
            selectedMandelbrotImage = ImageUtils.convertToType(selectedMandelbrotImage, BufferedImage.TYPE_INT_RGB);
            unshuffledSelectedImage = ImageUtils.convertToType(unshuffledSelectedImage, BufferedImage.TYPE_INT_RGB);

            // Проверка кодировки пикселей
            if (selectedMandelbrotImage.getType() != BufferedImage.TYPE_INT_RGB ||
                    unshuffledSelectedImage.getType() != BufferedImage.TYPE_INT_RGB) {
                throw new IllegalArgumentException("Изображения должны быть типа BufferedImage.TYPE_INT_RGB");
            }

            BufferedImage xorResultImage = XOR.performXOR(selectedMandelbrotImage, unshuffledSelectedImage);

            Graphics2D g2d = encryptedImage.createGraphics();
            g2d.drawImage(xorResultImage, startX, startY, null);
            g2d.dispose();

            saveDecryptedImage(encryptedImage);

        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    protected static void saveDecryptedImage(BufferedImage decryptedImage) {
        try {
            ImageIO.write(decryptedImage, "png", new File(getTempPath() + "decrypted_image.png"));
            logger.info("Дешифрованное изображение сохранено как decrypted_image.png");
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}