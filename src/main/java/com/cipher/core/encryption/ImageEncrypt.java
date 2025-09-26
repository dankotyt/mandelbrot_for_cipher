package com.cipher.core.encryption;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.security.SecureRandom;
import java.util.Map;

import com.cipher.core.dto.*;
import com.cipher.core.dto.neww.SegmentationParams;
import com.cipher.core.utils.*;
import com.cipher.core.service.MandelbrotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageEncrypt {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MandelbrotService mandelbrotService;
    private final BinaryFile binaryFile;
    private final DeterministicRandomGenerator drbg;
    private final ImageSegmentShuffler imageSegmentShuffler;
    private final EncryptionSessionManager sessionManager;
    private final CryptographicService cryptoService;
    private final TempFileManager tempFileManager;

    private static BufferedImage encryptedWholeImage;

    private static String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private static String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

//    public BufferedImage encryptSelectedArea(BufferedImage originalImage, Rectangle2D selectedArea)
//            throws IllegalArgumentException, RasterFormatException, IOException {
//
//        // Проверка входных параметров
//        if (originalImage == null) {
//            throw new IllegalArgumentException("Original image cannot be null");
//        }
//        if (selectedArea == null) {
//            throw new IllegalArgumentException("Selected area cannot be null");
//        }
//
//        int startX = (int) selectedArea.getMinX();
//        int startY = (int) selectedArea.getMinY();
//        int width = (int) selectedArea.getWidth();
//        int height = (int) selectedArea.getHeight();
//
//        // Проверка границ выбранной области
//        if (startX < 0 || startY < 0 ||
//                startX + width > originalImage.getWidth() ||
//                startY + height > originalImage.getHeight()) {
//            throw new RasterFormatException("Selected area is out of image bounds");
//        }
//
//        // Выделяем выбранную область
//        BufferedImage selectedImage = originalImage.getSubimage(startX, startY, width, height);
//
//        byte[] masterSeed = new byte[32];
//        SECURE_RANDOM.nextBytes(masterSeed);
//        drbg.initialize(masterSeed);
//
//        int segmentSize = drbg.generateAdaptiveSegmentSize(selectedImage.getWidth(), selectedImage.getHeight());
//        int segmentWidthSize = segmentSize;
//        int segmentHeightSize = segmentSize;
//
//        // Корректируем размеры сегментов, если необходимо
//        while (width % segmentWidthSize != 0) {
//            segmentWidthSize--;
//        }
//        while (height % segmentHeightSize != 0) {
//            segmentHeightSize--;
//        }
//
//        int finalSegmentSize = Math.min(segmentWidthSize, segmentHeightSize);
//
//        Pair<BufferedImage, Map<Integer, Integer>> shuffledResult =
//                imageSegmentShuffler.shuffleSegments(selectedImage, finalSegmentSize, drbg);
//        BufferedImage shuffledImage = shuffledResult.getKey();
//        Map<Integer, Integer> segmentMapping = shuffledResult.getValue();
//
//        // Получаем новые размеры после сегментации
//        int newWidth = shuffledImage.getWidth();
//        int newHeight = shuffledImage.getHeight();
//
//        // 2. Загружаем параметры из предварительно сгенерированного Мандельброта
//        MandelbrotParams mandelbrotParams = binaryFile.loadMandelbrotParamsFromBinaryFile(
//                getTempPath() + "mandelbrot_params.bin");
//
//        // 3. Генерируем Мандельброт с теми же параметрами, но под новые размеры
//        MandelbrotService mandelbrotServiceGenerator = mandelbrotService.createWithSize(newWidth, newHeight);
//        BufferedImage mandelbrotImage = mandelbrotServiceGenerator.generateImage(
//                newWidth, newHeight,
//                mandelbrotParams.zoom(),
//                mandelbrotParams.offsetX(),
//                mandelbrotParams.offsetY(),
//                mandelbrotParams.maxIter());
//
//        // 4. Выполняем XOR между сегментированным изображением и Мандельбротом
//        shuffledImage = ImageUtils.convertToARGB(shuffledImage);
//        mandelbrotImage = ImageUtils.convertToARGB(mandelbrotImage);
//        BufferedImage encryptedXORImage = XOR.performXOR(shuffledImage, mandelbrotImage);
//
//        // 5. Вставляем зашифрованную область обратно в исходное изображение
//        Graphics2D g2d = originalImage.createGraphics();
//        g2d.drawImage(encryptedXORImage, startX, startY, null);
//        g2d.dispose();
//
//        // 6. Сохраняем параметры для дешифрации
//        KeyDecoderParams keyDecoderParams = new KeyDecoderParams(
//                new MandelbrotParams(
//                        newWidth,
//                        newHeight,
//                        mandelbrotParams.zoom(),
//                        mandelbrotParams.offsetX(),
//                        mandelbrotParams.offsetY(),
//                        mandelbrotParams.maxIter()),
//                new SegmentationParams(
//                        finalSegmentSize,
//                        newWidth,
//                        newHeight,
//                        segmentMapping),
//                startY,
//                newWidth,
//                newHeight);
//
//        binaryFile.saveKeyDecoderToBinaryFile(getTempPath() + "key_decoder.bin", keyDecoderParams, masterSeed);
//
//        return originalImage;
//    }

    public BufferedImage encryptSelectedArea(BufferedImage originalImage, EncryptionAreaDTO areaDTO)
            throws Exception {

        try {
            // 1. Инициализация безопасной сессии
            EncryptionSessionParams sessionDTO = sessionManager.initializeSession();
            byte[] masterSeed = sessionManager.getMasterSeed();

            // 2. Проверка параметров области
            validateEncryptionArea(originalImage, areaDTO);

            // 3. Выделяем выбранную область
            BufferedImage selectedImage = originalImage.getSubimage(
                    areaDTO.startX(), areaDTO.startY(),
                    areaDTO.width(), areaDTO.height());

            // 4. Сегментация выбранной области
            SegmentationParams segmentationParams = processSegmentation(selectedImage, masterSeed, areaDTO);
            sessionManager.setSegmentationParams(segmentationParams);

            // 5. Генерация фрактала Мандельброта
            MandelbrotParams mandelbrotParams = generateMandelbrot(masterSeed);
            sessionManager.setMandelbrotParams(mandelbrotParams);

            // 6. Финальное шифрование области
            BufferedImage encryptedArea = performAreaEncryption(selectedImage, masterSeed, areaDTO);

            // 7. Вставляем зашифрованную область обратно
            Graphics2D g2d = originalImage.createGraphics();
            g2d.drawImage(encryptedArea, areaDTO.startX(), areaDTO.startY(), null);
            g2d.dispose();

            // 8. Сохраняем параметры для дешифровки
            saveDecryptionParameters(selectedArea);

            return originalImage;

        } finally {
            sessionManager.clearSession();
        }
    }

    public void encryptWholeImage(BufferedImage originalImage) throws Exception {
        try {
            // 1. Инициализация безопасной сессии
            EncryptionSessionParams sessionDTO = sessionManager.initializeSession();
            byte[] masterSeed = sessionManager.getMasterSeed();

            // 2. Создаем DTO для всей области
            EncryptionAreaDTO wholeAreaDTO = new EncryptionAreaDTO(
                    0, 0, originalImage.getWidth(), originalImage.getHeight(), true);

            // 3. Сегментация всего изображения
            SegmentationParams segmentationParams = processSegmentation(originalImage, masterSeed, wholeAreaDTO);
            sessionManager.setSegmentationParams(segmentationParams);

            // 4. Генерация фрактала Мандельброта
            MandelbrotParams mandelbrotParams = generateMandelbrot(masterSeed);
            sessionManager.setMandelbrotParams(mandelbrotParams);

            // 5. Финальное шифрование
            performFinalEncryption(masterSeed);

            // 6. Сохраняем параметры для дешифровки
            saveDecryptionParameters(wholeAreaDTO);

        } finally {
            sessionManager.clearSession();
        }
    }

    private void validateEncryptionArea(BufferedImage image, EncryptionAreaDTO areaDTO) {
        if (areaDTO.startX() < 0 || areaDTO.startY() < 0 ||
                areaDTO.startX() + areaDTO.width() > image.getWidth() ||
                areaDTO.startY() + areaDTO.height() > image.getHeight()) {
            throw new IllegalArgumentException("Selected area is out of image bounds");
        }
    }

    private SegmentationParams processSegmentation(BufferedImage image, byte[] masterSeed,
                                                      EncryptionAreaDTO areaDTO) {

        // Для всей области используем всё изображение, для части - вырезаем
        BufferedImage targetImage = areaDTO.isWhole() ?
                image : image.getSubimage(
                areaDTO.startX(), areaDTO.startY(),
                areaDTO.width(), areaDTO.height());

        // Определяем размер сегмента
        DeterministicRandomGenerator tempDrbg = new DeterministicRandomGenerator();
        tempDrbg.initialize(masterSeed);
        int segmentSize = tempDrbg.generateAdaptiveSegmentSize(
                targetImage.getWidth(), targetImage.getHeight());

        // Изменяем размер изображения под сегменты
        BufferedImage paddedImage = imageSegmentShuffler.padImageToSegmentSize(targetImage, segmentSize);

        // Перемешиваем сегменты
        Pair<BufferedImage, Map<Integer, Integer>> shuffledResult =
                imageSegmentShuffler.shuffleSegments(paddedImage, segmentSize, masterSeed);

        // Сохраняем сегментированное изображение
        String imageName = areaDTO.isWhole() ? "segmented_whole.png" : "segmented_area.png";
        tempFileManager.saveBufferedImageToTemp(shuffledResult.getKey(), imageName);

        return new SegmentationParams(
                segmentSize,
                paddedImage.getWidth(),
                paddedImage.getHeight(),
                shuffledResult.getValue()
        );
    }

    private BufferedImage performAreaEncryption(BufferedImage originalArea, byte[] masterSeed,
                                                EncryptionAreaDTO areaDTO) throws Exception {

        // Загружаем сегментированное изображение области
        BufferedImage segmentedArea = tempFileManager.loadBufferedImageFromTemp("segmented_area.png");

        // Генерируем финальный фрактал для области
        BufferedImage finalMandelbrot = mandelbrotService.generateFinalFractal(
                segmentedArea.getWidth(), segmentedArea.getHeight());

        // Выполняем XOR

        return XOR.performXOR(
                ImageUtils.convertToARGB(segmentedArea),
                ImageUtils.convertToARGB(finalMandelbrot)
        );
    }

    /**
     * Шифрование всего изображения
     */
    private BufferedImage performFinalEncryption(byte[] masterSeed) throws Exception {
        BufferedImage segmentedImage = tempFileManager.loadBufferedImageFromTemp("segmented_whole.png");

        BufferedImage finalMandelbrot = mandelbrotService.generateFinalFractal(
                segmentedImage.getWidth(), segmentedImage.getHeight());

        return XOR.performXOR(
                ImageUtils.convertToARGB(segmentedImage),
                ImageUtils.convertToARGB(finalMandelbrot)
        );
    }

//    public void encryptWholeImage(BufferedImage segmentedImage, Map<Integer, Integer> segmentMapping,
//                                  int segmentSize, byte[] masterSeed) throws IOException {
//        int width = segmentedImage.getWidth();
//        int height = segmentedImage.getHeight();
//
//        drbg.initialize(masterSeed);
//
//        MandelbrotParams previewParams = binaryFile
//                .loadMandelbrotParamsFromBinaryFile(getTempPath() + "mandelbrot_params.bin");
//
//
////        if (width % segmentSize != 0) {
////            width = (width / segmentSize + 1) * segmentSize;
////        }
////        if (height % segmentSize != 0) {
////            height = (height / segmentSize + 1) * segmentSize;
////        }
////
////        if (segmentedImage.getWidth() != width || segmentedImage.getHeight() != height) {
////            segmentedImage = resizeImage(segmentedImage, width, height);
////        }
////
////        Pair<BufferedImage, Map<Integer, Integer>> shuffledResult =
////                ImageSegmentShuffler.shuffleSegments(segmentedImage, segmentSize, drbg);
////        BufferedImage shuffledImage = shuffledResult.getKey();
////        Map<Integer, Integer> segmentMapping = shuffledResult.getValue();
////
////        int newWidth = shuffledImage.getWidth();
////        int newHeight = shuffledImage.getHeight();
//
//        // 3. Генерируем финальный Мандельброт с теми же параметрами, но новыми размерами
//        MandelbrotService mandelbrotServiceGenerator = mandelbrotService.createWithSize(newWidth, newHeight);
//        BufferedImage mandelbrotImage = mandelbrotServiceGenerator.generateImage(
//                newWidth, newHeight,
//                previewParams.zoom(),
//                previewParams.offsetX(),
//                previewParams.offsetY(),
//                previewParams.maxIter());
//
//        // Сохраняем финальный ключ для дешифрации
//        ImageIO.write(mandelbrotImage, "png", new File(getTempPath() + "mandelbrot_final.png"));
//
//        // 4. Продолжаем шифрование как раньше
//        shuffledImage = ImageUtils.convertToARGB(shuffledImage);
//        mandelbrotImage = ImageUtils.convertToARGB(mandelbrotImage);
//
//        encryptedWholeImage = XOR.performXOR(shuffledImage, mandelbrotImage);
//
//        KeyDecoderParams keyDecoderParams = new KeyDecoderParams(
//                previewParams.zoom(), previewParams.offsetX(), previewParams.offsetY(),
//                previewParams.maxIter(), segmentSize, segmentMapping,
//                0, 0, newWidth, newHeight);
//
//        binaryFile.saveKeyDecoderToBinaryFile(getTempPath() + "key_decoder.bin",
//                keyDecoderParams, masterSeed);
//    }


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