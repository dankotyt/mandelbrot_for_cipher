package com.cipher.core.encryption;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.Map;

import com.cipher.core.utils.BinaryFile;
import com.cipher.core.utils.ImageUtils;
import com.cipher.core.utils.Pair;
import javafx.geometry.Rectangle2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageEncrypt {
    private static final Logger logger = LoggerFactory.getLogger(ImageEncrypt.class);
    private static BufferedImage encryptedWholeImage;

    private static String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private static String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

//    public static BufferedImage encryptSelectedArea(BufferedImage originalImage, BufferedImage selectedImage,
//                                                    int startX, int startY,
//                                                    int width, int height) {
//        int selectedWidth = selectedImage.getWidth();
//        int selectedHeight = selectedImage.getHeight();
//
//        // Проверка и корректировка размеров для сегментации
//        int segmentWidthSize = 4; // Например, 32 сегмента по ширине
//        int segmentHeightSize = 4; // Например, 16 сегментов по высоте
//
//        // Уменьшаем размеры сегментов, если они не делят размеры выделенной области без остатка
//        while (selectedWidth % segmentWidthSize != 0) {
//            segmentWidthSize--;
//        }
//        while (selectedHeight % segmentHeightSize != 0) {
//            segmentHeightSize--;
//        }
//
//        // Загружаем изображение множества Мандельброта
//        BufferedImage mandelbrotImage = null;
//        try {
//            mandelbrotImage = ImageIO.read(new File(getTempPath() + "mandelbrot.png"));
//        } catch (IOException e) {
//            logger.error(e.getMessage());
//        }
//
//        // Вырезаем соответствующую область из изображения Мандельброта
//        if (mandelbrotImage != null) {
//            BufferedImage mandelbrotSelectedArea = mandelbrotImage.getSubimage(
//                    startX, startY, width, height);
//            // Загружаем параметры из mandelbrot_params
//            Object[] mandelbrotParams = BinaryFile.loadMandelbrotParamsFromBinaryFile(getTempPath() + "mandelbrot_params.bin");
//            int startMandelbrotWidth = (int) mandelbrotParams[0];
//            int startMandelbrotHeight = (int) mandelbrotParams[1];
//            double ZOOM = (double) mandelbrotParams[2];
//            double offsetX = (double) mandelbrotParams[3];
//            double offsetY = (double) mandelbrotParams[4];
//            int MAX_ITER = (int) mandelbrotParams[5];
//
//            BufferedImage encryptedXORImage = XOR.performXOR(selectedImage, mandelbrotSelectedArea);
//
//            // Сегментируем и перемешиваем зашифрованную область
//            ImageSegmentShuffler mandelbrotModel = new ImageSegmentShuffler(encryptedXORImage);
//            Pair<BufferedImage, Map<Integer, Integer>> shuffledResult =
//                    mandelbrotModel.shuffleSegments(encryptedXORImage, segmentWidthSize, segmentHeightSize);
//            BufferedImage shuffledImage = shuffledResult.getKey();
//            Map<Integer, Integer> segmentMapping = shuffledResult.getValue();
//
//            // Заменяем выделенную область на зашифрованную в исходном изображении
//            Graphics2D g2d = originalImage.createGraphics();
//            g2d.drawImage(shuffledImage, startX, startY, null);
//            g2d.dispose();
//
//            BinaryFile.saveKeyDecoderToBinaryFile(getTempPath() + "key_decoder.bin",
//                    startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER,
//                    segmentWidthSize, segmentHeightSize, segmentMapping, startX, startY, width, height);
//        }
//        return originalImage;
//    }

    public static BufferedImage encryptSelectedArea(BufferedImage originalImage, Rectangle2D selectedArea)
            throws IllegalArgumentException, RasterFormatException {

        if (originalImage == null) {
            throw new IllegalArgumentException("Original image cannot be null");
        }
        if (selectedArea == null) {
            throw new IllegalArgumentException("Selected area cannot be null");
        }

        int startX = (int) selectedArea.getMinX();
        int startY = (int) selectedArea.getMinY();
        int width = (int) selectedArea.getWidth();
        int height = (int) selectedArea.getHeight();

        // Проверка границ
        if (startX < 0 || startY < 0 ||
                startX + width > originalImage.getWidth() ||
                startY + height > originalImage.getHeight()) {
            throw new RasterFormatException("Selected area is out of image bounds");
        }

        BufferedImage selectedImage = originalImage.getSubimage(startX, startY, width, height);

        int segmentWidthSize = 4;
        int segmentHeightSize = 4;

        // Проверка и корректировка размеров сегментов
        while (width % segmentWidthSize != 0) {
            segmentWidthSize--;
        }
        while (height % segmentHeightSize != 0) {
            segmentHeightSize--;
        }

        // Загрузка изображения Мандельброта
        BufferedImage mandelbrotImage = loadMandelbrotImage();
        if (mandelbrotImage == null) {
            throw new IllegalStateException("Mandelbrot image not found");
        }

        // Проверка границ для изображения Мандельброта
        if (startX + width > mandelbrotImage.getWidth() ||
                startY + height > mandelbrotImage.getHeight()) {
            throw new RasterFormatException("Selected area is out of Mandelbrot image bounds");
        }

        BufferedImage mandelbrotSelectedArea = mandelbrotImage.getSubimage(startX, startY, width, height);

        Object[] mandelbrotParams = BinaryFile.loadMandelbrotParamsFromBinaryFile(getTempPath() + "mandelbrot_params.bin");
        int startMandelbrotWidth = (int) mandelbrotParams[0];
        int startMandelbrotHeight = (int) mandelbrotParams[1];
        double ZOOM = (double) mandelbrotParams[2];
        double offsetX = (double) mandelbrotParams[3];
        double offsetY = (double) mandelbrotParams[4];
        int MAX_ITER = (int) mandelbrotParams[5];

        BufferedImage encryptedXORImage = XOR.performXOR(selectedImage, mandelbrotSelectedArea);

        ImageSegmentShuffler shuffler = new ImageSegmentShuffler(encryptedXORImage);
        Pair<BufferedImage, Map<Integer, Integer>> shuffledResult =
                shuffler.shuffleSegments(encryptedXORImage, segmentWidthSize, segmentHeightSize);
        BufferedImage shuffledImage = shuffledResult.getKey();
        Map<Integer, Integer> segmentMapping = shuffledResult.getValue();

        // Заменяем выделенную область на зашифрованную в исходном изображении
        Graphics2D g2d = originalImage.createGraphics();
        g2d.drawImage(shuffledImage, startX, startY, null);
        g2d.dispose();

        BinaryFile.saveKeyDecoderToBinaryFile(getTempPath() + "key_decoder.bin",
                startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER,
                segmentWidthSize, segmentHeightSize, segmentMapping, startX, startY, width, height);

        return originalImage;
    }

    public static void encryptWholeImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Проверка и корректировка размеров для сегментации
        int segmentWidthSize = 4; // Например, 32 сегмента по ширине
        int segmentHeightSize = 4; // Например, 16 сегментов по высоте

        if (width % segmentWidthSize != 0) {
            width = (width / segmentWidthSize + 1) * segmentWidthSize;
        }
        if (height % segmentHeightSize != 0) {
            height = (height / segmentHeightSize + 1) * segmentHeightSize;
        }

        // Изменение размера изображения, если необходимо
        if (image.getWidth() != width || image.getHeight() != height) {
            image = resizeImage(image, width, height);
        }

        // Загружаем изображение множества Мандельброта
        BufferedImage mandelbrotImage = null;
        try {
            mandelbrotImage = ImageIO.read(new File(getTempPath() + "mandelbrot.png"));
        } catch (IOException e) {
            logger.error(e.getMessage());
        }


        // Изменяем размер изображения множества Мандельброта, если оно не совпадает с исходным изображением
        if (mandelbrotImage.getWidth() != width || mandelbrotImage.getHeight() != height) {
            mandelbrotImage = ImageSegmentShuffler.resizeImage(mandelbrotImage, width, height);
        }

        if (image.getWidth() != mandelbrotImage.getWidth() || image.getHeight() != mandelbrotImage.getHeight()) {
            throw new IllegalArgumentException("Размеры изображений должны совпадать");
        }

        // Преобразование типов изображений в BufferedImage.TYPE_INT_RGB
        image = ImageUtils.convertToType(image, BufferedImage.TYPE_INT_RGB);
        mandelbrotImage = ImageUtils.convertToType(mandelbrotImage, BufferedImage.TYPE_INT_RGB);

        // Проверка кодировки пикселей
        if (image.getType() != BufferedImage.TYPE_INT_RGB || mandelbrotImage.getType() != BufferedImage.TYPE_INT_RGB) {
            throw new IllegalArgumentException("Изображения должны быть типа BufferedImage.TYPE_INT_RGB");
        }

        BufferedImage encryptedXORImage = XOR.performXOR(image, mandelbrotImage);

        // Сегментируем и перемешиваем зашифрованное изображение
        ImageSegmentShuffler mandelbrotModel = new ImageSegmentShuffler(encryptedXORImage);
        Pair<BufferedImage, Map<Integer, Integer>> shuffledResult = mandelbrotModel.shuffleSegments(encryptedXORImage, segmentWidthSize, segmentHeightSize);
        BufferedImage shuffledImage = shuffledResult.getKey();
        Map<Integer, Integer> segmentMapping = shuffledResult.getValue();

        encryptedWholeImage = shuffledImage;

        // Загружаем параметры из mandelbrot_params
        Object[] mandelbrotParams = BinaryFile.loadMandelbrotParamsFromBinaryFile(getTempPath() + "mandelbrot_params.bin");
        int startMandelbrotWidth = (int) mandelbrotParams[0];
        int startMandelbrotHeight = (int) mandelbrotParams[1];
        double ZOOM = (double) mandelbrotParams[2];
        double offsetX = (double) mandelbrotParams[3];
        double offsetY = (double) mandelbrotParams[4];
        int MAX_ITER = (int) mandelbrotParams[5];

        // Сохраняем зашифрованное изображение и параметры
        BinaryFile.saveKeyDecoderToBinaryFile(getTempPath() + "key_decoder.bin", startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER, segmentWidthSize, segmentHeightSize, segmentMapping, 0, 0, width, height);
    }

    public BufferedImage getEncryptedImage() {
        return encryptedWholeImage;
    }

    private static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        graphics2D.dispose();
        return resizedImage;
    }

    public static BufferedImage loadImage(String filePath) {
        try {
            File imageFile = new File(filePath);
            if (!imageFile.exists()) {
                throw new FileNotFoundException("Не удалось найти изображение по пути: " + filePath);
            }
            return ImageIO.read(imageFile);
        } catch (Exception e) {
            logger.error("Ошибка при загрузке изображения: {}", e.getMessage());
            return null;
        }
    }

    private static BufferedImage loadMandelbrotImage() {
        try {
            return ImageIO.read(new File(getTempPath() + "mandelbrot.png"));
        } catch (IOException e) {
            logger.error("Ошибка при загрузке изображения-ключа: {}", e.getMessage());
            return null;
        }
    }
}