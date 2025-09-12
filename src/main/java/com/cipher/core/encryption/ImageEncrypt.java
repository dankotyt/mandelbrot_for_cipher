package com.cipher.core.encryption;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.security.SecureRandom;
import java.util.Map;

import com.cipher.core.dto.KeyDecoderParams;
import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.utils.BinaryFile;
import com.cipher.core.utils.DeterministicRandomGenerator;
import com.cipher.core.utils.ImageUtils;
import com.cipher.core.service.MandelbrotService;
import com.cipher.core.utils.Pair;
import javafx.geometry.Rectangle2D;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageEncrypt {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MandelbrotService mandelbrotService;
    private final BinaryFile binaryFile;
    private final DeterministicRandomGenerator drbg;

    private static BufferedImage encryptedWholeImage;

    private static String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private static String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    public BufferedImage encryptSelectedArea(BufferedImage originalImage, Rectangle2D selectedArea)
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

        byte[] masterSeed = new byte[32];
        SECURE_RANDOM.nextBytes(masterSeed);
        drbg.initialize(masterSeed);

        int segmentSize = drbg.generateAdaptiveSegmentSize(selectedImage.getWidth(), selectedImage.getHeight());
        int segmentWidthSize = segmentSize;
        int segmentHeightSize = segmentSize;

        // Корректируем размеры сегментов, если необходимо
        while (width % segmentWidthSize != 0) {
            segmentWidthSize--;
        }
        while (height % segmentHeightSize != 0) {
            segmentHeightSize--;
        }

        Pair<BufferedImage, Map<Integer, Integer>> shuffledResult =
                ImageSegmentShuffler.shuffleSegments(selectedImage, segmentWidthSize, segmentHeightSize, drbg);
        BufferedImage shuffledImage = shuffledResult.getKey();
        Map<Integer, Integer> segmentMapping = shuffledResult.getValue();

        // Получаем новые размеры после сегментации
        int newWidth = shuffledImage.getWidth();
        int newHeight = shuffledImage.getHeight();

        // 2. Загружаем параметры из предварительно сгенерированного Мандельброта
        MandelbrotParams mandelbrotParams = binaryFile.loadMandelbrotParamsFromBinaryFile(
                getTempPath() + "mandelbrot_params.bin");

        // 3. Генерируем Мандельброт с теми же параметрами, но под новые размеры
        MandelbrotService mandelbrotServiceGenerator = mandelbrotService.createWithSize(newWidth, newHeight);
        BufferedImage mandelbrotImage = mandelbrotServiceGenerator.generateImage(
                newWidth, newHeight,
                mandelbrotParams.zoom(),
                mandelbrotParams.offsetX(),
                mandelbrotParams.offsetY(),
                mandelbrotParams.maxIter());

        // 4. Выполняем XOR между сегментированным изображением и Мандельбротом
        shuffledImage = ImageUtils.convertToARGB(shuffledImage);
        mandelbrotImage = ImageUtils.convertToARGB(mandelbrotImage);
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

        binaryFile.saveKeyDecoderToBinaryFile(getTempPath() + "key_decoder.bin", keyDecoderParams, masterSeed);

        return originalImage;
    }

    public void encryptWholeImage(BufferedImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();

        byte[] masterSeed = new byte[32];
        SECURE_RANDOM.nextBytes(masterSeed);
        drbg.initialize(masterSeed);

        MandelbrotParams previewParams = binaryFile
                .loadMandelbrotParamsFromBinaryFile(getTempPath() + "mandelbrot_params.bin");

        int segmentSize = drbg.generateAdaptiveSegmentSize(width, height);

        if (width % segmentSize != 0) {
            width = (width / segmentSize + 1) * segmentSize;
        }
        if (height % segmentSize != 0) {
            height = (height / segmentSize + 1) * segmentSize;
        }

        if (image.getWidth() != width || image.getHeight() != height) {
            image = resizeImage(image, width, height);
        }

        Pair<BufferedImage, Map<Integer, Integer>> shuffledResult =
                ImageSegmentShuffler.shuffleSegments(image, segmentSize, segmentSize, drbg);
        BufferedImage shuffledImage = shuffledResult.getKey();
        Map<Integer, Integer> segmentMapping = shuffledResult.getValue();

        int newWidth = shuffledImage.getWidth();
        int newHeight = shuffledImage.getHeight();

        // 3. Генерируем финальный Мандельброт с теми же параметрами, но новыми размерами
        MandelbrotService mandelbrotServiceGenerator = mandelbrotService.createWithSize(newWidth, newHeight);
        BufferedImage mandelbrotImage = mandelbrotServiceGenerator.generateImage(
                newWidth, newHeight,
                previewParams.zoom(),
                previewParams.offsetX(),
                previewParams.offsetY(),
                previewParams.maxIter());

        // Сохраняем финальный ключ для дешифрации
        ImageIO.write(mandelbrotImage, "png", new File(getTempPath() + "mandelbrot_final.png"));

        // 4. Продолжаем шифрование как раньше
        shuffledImage = ImageUtils.convertToARGB(shuffledImage);
        mandelbrotImage = ImageUtils.convertToARGB(mandelbrotImage);

        encryptedWholeImage = XOR.performXOR(shuffledImage, mandelbrotImage);

        KeyDecoderParams keyDecoderParams = new KeyDecoderParams(
                previewParams.zoom(), previewParams.offsetX(), previewParams.offsetY(),
                previewParams.maxIter(), segmentSize, segmentSize, segmentMapping,
                0, 0, newWidth, newHeight);

        binaryFile.saveKeyDecoderToBinaryFile(getTempPath() + "key_decoder.bin",
                keyDecoderParams, masterSeed);
    }

    public BufferedImage getEncryptedImage() {
        return encryptedWholeImage;
    }

    public static BufferedImage resizeImage(BufferedImage image, int width, int height) {
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(image, 0, 0, width, height, null);
        g2d.dispose();
        return resizedImage;
    }
}