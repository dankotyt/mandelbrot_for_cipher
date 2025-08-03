package com.cipher.core.encryption;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.awt.Graphics2D;

import com.cipher.core.utils.BinaryFile;
import com.cipher.core.utils.ImageUtils;
import com.cipher.core.utils.Mandelbrot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ImageDecrypt {
    private static final Logger logger = LoggerFactory.getLogger(ImageDecrypt.class);

    private static final String RESOURCES_PATH = "resources" + File.separator;

    private static String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private static String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    public static void decryptImage(String keyFilePath) {
        try {
            Object[] keyDecoderParams = BinaryFile.loadKeyDecoderFromBinaryFile(keyFilePath);

            int startMandelbrotWidth = (int) keyDecoderParams[0];
            int startMandelbrotHeight = (int) keyDecoderParams[1];
            double ZOOM = (double) keyDecoderParams[2];
            double offsetX = (double) keyDecoderParams[3];
            double offsetY = (double) keyDecoderParams[4];
            int MAX_ITER = (int) keyDecoderParams[5];
            int segmentWidthSize = (int) keyDecoderParams[6];
            int segmentHeightSize = (int) keyDecoderParams[7];
            Map<Integer, Integer> segmentMapping = (Map<Integer, Integer>) keyDecoderParams[8];
            int startX = (int) keyDecoderParams[9];
            int startY = (int) keyDecoderParams[10];
            int width = (int) keyDecoderParams[11];
            int height = (int) keyDecoderParams[12];

            // Загрузка зашифрованного изображения
            BufferedImage encryptedImage = ImageIO.read(new File(getTempPath() + "input.png"));

            // Выделяем зашифрованную часть
            BufferedImage encryptedSelectedArea = encryptedImage.getSubimage(startX, startY, width, height);

            // Создаем модель для аншафла и возвращаем сегменты на свои места
            ImageSegmentShuffler modelEncryptedSelectedArea = new ImageSegmentShuffler(encryptedSelectedArea);
            BufferedImage unshuffledSelectedImage = modelEncryptedSelectedArea.unshuffledSegments(encryptedSelectedArea, segmentMapping, segmentWidthSize, segmentHeightSize);

//            // Отображаем исходное изображение с зашифрованной областью
//            ImageViewer originalView = new ImageViewer("Original Image with Encrypted Area", unshuffledSelectedImage);
//            originalView.showImage();

            // Создаем изображение Мандельброта
            Mandelbrot mandelbrotImage = new Mandelbrot(startMandelbrotWidth, startMandelbrotHeight);
            BufferedImage mandelbrotBufferedImage = mandelbrotImage.generateImage(startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER);

            // Выделяем ту же часть Мандельброта, что и зашифрованная
            BufferedImage selectedMandelbrotImage = mandelbrotBufferedImage.getSubimage(startX, startY, width, height);

            // Преобразование типов изображений в BufferedImage.TYPE_INT_RGB
            selectedMandelbrotImage = ImageUtils.convertToType(selectedMandelbrotImage, BufferedImage.TYPE_INT_RGB);
            unshuffledSelectedImage = ImageUtils.convertToType(unshuffledSelectedImage, BufferedImage.TYPE_INT_RGB);

            // Проверка кодировки пикселей
            if (selectedMandelbrotImage.getType() != BufferedImage.TYPE_INT_RGB || unshuffledSelectedImage.getType() != BufferedImage.TYPE_INT_RGB) {
                throw new IllegalArgumentException("Изображения должны быть типа BufferedImage.TYPE_INT_RGB");
            }

            // XOR между выделенной частью и выделенным Мандельбротом
            BufferedImage xorResultImage = XOR.performXOR(selectedMandelbrotImage, unshuffledSelectedImage);

            // Сшиваем вырезанный участок с исходным зашифрованным изображением
            Graphics2D g2d = encryptedImage.createGraphics();
            g2d.drawImage(xorResultImage, startX, startY, null);
            g2d.dispose();

            // Сохраняем дешифрованное изображение
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