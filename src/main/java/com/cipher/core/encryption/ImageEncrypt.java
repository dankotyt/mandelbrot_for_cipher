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
import com.cipher.core.service.MandelbrotService;
import com.cipher.core.utils.Pair;
import javafx.geometry.Rectangle2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
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

        // Проверка входных параметров
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

        // Проверка границ выбранной области
        if (startX < 0 || startY < 0 ||
                startX + width > originalImage.getWidth() ||
                startY + height > originalImage.getHeight()) {
            throw new RasterFormatException("Selected area is out of image bounds");
        }

        // Выделяем выбранную область
        BufferedImage selectedImage = originalImage.getSubimage(startX, startY, width, height);

        // Определяем размеры сегментов
        int segmentWidthSize = 4;
        int segmentHeightSize = 4;

        // Корректируем размеры сегментов, если необходимо
        while (width % segmentWidthSize != 0) {
            segmentWidthSize--;
        }
        while (height % segmentHeightSize != 0) {
            segmentHeightSize--;
        }

        // 1. Сначала сегментируем и перемешиваем исходное изображение
        ImageSegmentShuffler shuffler = new ImageSegmentShuffler(selectedImage);
        Pair<BufferedImage, Map<Integer, Integer>> shuffledResult =
                shuffler.shuffleSegments(selectedImage, segmentWidthSize, segmentHeightSize);
        BufferedImage shuffledImage = shuffledResult.getKey();
        Map<Integer, Integer> segmentMapping = shuffledResult.getValue();

        // Получаем новые размеры после сегментации
        int newWidth = shuffledImage.getWidth();
        int newHeight = shuffledImage.getHeight();

        // 2. Загружаем параметры из предварительно сгенерированного Мандельброта
        MandelbrotParams mandelbrotParams = BinaryFile.loadMandelbrotParamsFromBinaryFile(
                getTempPath() + "mandelbrot_params.bin");

        // 3. Генерируем Мандельброт с теми же параметрами, но под новые размеры
        MandelbrotService mandelbrotServiceGenerator = new MandelbrotService(newWidth, newHeight);
        BufferedImage mandelbrotImage = mandelbrotServiceGenerator.generateImage(
                newWidth, newHeight,
                mandelbrotParams.zoom(),
                mandelbrotParams.offsetX(),
                mandelbrotParams.offsetY(),
                mandelbrotParams.maxIter());

        // 4. Выполняем XOR между сегментированным изображением и Мандельбротом
        shuffledImage = ImageUtils.convertToType(shuffledImage, BufferedImage.TYPE_INT_RGB);
        mandelbrotImage = ImageUtils.convertToType(mandelbrotImage, BufferedImage.TYPE_INT_RGB);
        BufferedImage encryptedXORImage = XOR.performXOR(shuffledImage, mandelbrotImage);

        // 5. Вставляем зашифрованную область обратно в исходное изображение
        Graphics2D g2d = originalImage.createGraphics();
        g2d.drawImage(encryptedXORImage, startX, startY, null);
        g2d.dispose();

        // 6. Сохраняем параметры для дешифрации
        KeyDecoderParams keyDecoderParams = new KeyDecoderParams(
                mandelbrotParams.zoom(),
                mandelbrotParams.offsetX(),
                mandelbrotParams.offsetY(),
                mandelbrotParams.maxIter(),
                segmentWidthSize,
                segmentHeightSize,
                segmentMapping,
                startX,
                startY,
                newWidth,
                newHeight);

        BinaryFile.saveKeyDecoderToBinaryFile(getTempPath() + "key_decoder.bin", keyDecoderParams);

        return originalImage;
    }

    public void encryptWholeImage(BufferedImage image) throws IOException {
        MandelbrotParams previewParams = BinaryFile
                .loadMandelbrotParamsFromBinaryFile(getTempPath() + "mandelbrot_params.bin");

        // 2. Подготовка изображения и сегментация (как было)
        int width = image.getWidth();
        int height = image.getHeight();

        int segmentWidthSize = 4;
        int segmentHeightSize = 4;

        if (width % segmentWidthSize != 0) {
            width = (width / segmentWidthSize + 1) * segmentWidthSize;
        }
        if (height % segmentHeightSize != 0) {
            height = (height / segmentHeightSize + 1) * segmentHeightSize;
        }

        if (image.getWidth() != width || image.getHeight() != height) {
            image = resizeImage(image, width, height);
        }

        Pair<BufferedImage, Map<Integer, Integer>> shuffledResult =
                ImageSegmentShuffler.shuffleSegments(image, segmentWidthSize, segmentHeightSize);
        BufferedImage shuffledImage = shuffledResult.getKey();
        Map<Integer, Integer> segmentMapping = shuffledResult.getValue();

        int newWidth = shuffledImage.getWidth();
        int newHeight = shuffledImage.getHeight();

        // 3. Генерируем финальный Мандельброт с теми же параметрами, но новыми размерами
        MandelbrotService mandelbrotServiceGenerator = new MandelbrotService(newWidth, newHeight);
        BufferedImage mandelbrotImage = mandelbrotServiceGenerator.generateImage(
                newWidth, newHeight,
                previewParams.zoom(),
                previewParams.offsetX(),
                previewParams.offsetY(),
                previewParams.maxIter());

        // Сохраняем финальный ключ для дешифрации
        ImageIO.write(mandelbrotImage, "png", new File(getTempPath() + "mandelbrot_final.png"));

        // 4. Продолжаем шифрование как раньше
        shuffledImage = ImageUtils.convertToType(shuffledImage, BufferedImage.TYPE_INT_RGB);
        mandelbrotImage = ImageUtils.convertToType(mandelbrotImage, BufferedImage.TYPE_INT_RGB);

        encryptedWholeImage = XOR.performXOR(shuffledImage, mandelbrotImage);

        KeyDecoderParams keyDecoderParams = new KeyDecoderParams(
                previewParams.zoom(), previewParams.offsetX(), previewParams.offsetY(),
                previewParams.maxIter(), segmentWidthSize, segmentHeightSize, segmentMapping,
                0, 0, newWidth, newHeight);

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