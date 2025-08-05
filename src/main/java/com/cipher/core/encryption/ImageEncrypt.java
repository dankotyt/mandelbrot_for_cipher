package com.cipher.core.encryption;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.Map;

import com.cipher.core.dto.KeyDecoderParams;
import com.cipher.core.dto.MandelbrotParams;
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

    public static BufferedImage encryptSelectedArea(BufferedImage originalImage, Rectangle2D selectedArea)
            throws IllegalArgumentException, RasterFormatException, IOException {

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
        MandelbrotParams mandelbrotParams = BinaryFile.loadMandelbrotParamsFromBinaryFile(getTempPath() + "mandelbrot_params.bin");
        KeyDecoderParams keyDecoderParams = new KeyDecoderParams(mandelbrotParams.startMandelbrotWidth(),
                mandelbrotParams.startMandelbrotHeight(), mandelbrotParams.zoom(),
                mandelbrotParams.offsetX(), mandelbrotParams.offsetY(), mandelbrotParams.maxIter(),
                segmentWidthSize, segmentHeightSize, segmentMapping, startX, startY, width, height);

        // Сохраняем зашифрованное изображение и параметры
        BinaryFile.saveKeyDecoderToBinaryFile(getTempPath() + "key_decoder.bin",
                keyDecoderParams);

        return originalImage;
    }

    public void encryptWholeImage(BufferedImage image) throws IOException {
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
            mandelbrotImage = resizeImage(mandelbrotImage, width, height);
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
        Pair<BufferedImage, Map<Integer, Integer>> shuffledResult =
                ImageSegmentShuffler.shuffleSegments(encryptedXORImage, segmentWidthSize, segmentHeightSize);
        BufferedImage shuffledImage = shuffledResult.getKey();
        Map<Integer, Integer> segmentMapping = shuffledResult.getValue();

        encryptedWholeImage = shuffledImage;

        // Загружаем параметры из mandelbrot_params
        MandelbrotParams mandelbrotParams =
                BinaryFile.loadMandelbrotParamsFromBinaryFile(getTempPath() + "mandelbrot_params.bin");

        KeyDecoderParams keyDecoderParams = new KeyDecoderParams(
                mandelbrotParams.startMandelbrotWidth(), mandelbrotParams.startMandelbrotHeight(),
                mandelbrotParams.zoom(), mandelbrotParams.offsetX(), mandelbrotParams.offsetY(),
                mandelbrotParams.maxIter(), segmentWidthSize, segmentHeightSize, segmentMapping,
                0, 0, width, height);

        BinaryFile.saveKeyDecoderToBinaryFile(getTempPath() + "key_decoder.bin", keyDecoderParams);
    }

    public BufferedImage getEncryptedImage() {
        return encryptedWholeImage;
    }

    public static BufferedImage resizeImage(BufferedImage image, int width, int height) {
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(image, 0, 0, width, height, null);
        g2d.dispose();
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