package com.cipher.core.encryption;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.util.Set;

import com.cipher.core.dto.*;
import com.cipher.core.dto.neww.*;
import com.cipher.core.service.KeyExchangeService;
import com.cipher.core.utils.*;
import com.cipher.core.service.MandelbrotService;
import javafx.geometry.Rectangle2D;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageEncrypt {
    private final DialogDisplayer dialogDisplayer;
    private MandelbrotParams mandelbrotParams;
    private final KeyExchangeService keyExchangeService;

    public void initMandelbrotParams(MandelbrotParams mandelbrotParams) {
        this.mandelbrotParams = mandelbrotParams;
    }

//    public BufferedImage encryptSelectedArea(BufferedImage originalImage, EncryptionAreaDTO areaDTO)
//            throws Exception {
//
//        try {
//
//            // 2. Проверка параметров области
//            validateEncryptionArea(originalImage, areaDTO);
//
//            // 3. Выделяем выбранную область
//            BufferedImage selectedImage = originalImage.getSubimage(
//                    areaDTO.startX(), areaDTO.startY(),
//                    areaDTO.width(), areaDTO.height());
//
//            // 4. Сегментация выбранной области
//            SegmentationParams segmentationParams = processSegmentation(selectedImage, masterSeed, areaDTO);
//            sessionManager.setSegmentationParams(segmentationParams);
//
//            // 5. Генерация фрактала Мандельброта
//            MandelbrotParams mandelbrotParams = generateMandelbrot(masterSeed);
//            sessionManager.setMandelbrotParams(mandelbrotParams);
//
//            // 6. Финальное шифрование области
//            BufferedImage encryptedArea = performAreaEncryption(selectedImage, masterSeed, areaDTO);
//
//            // 7. Вставляем зашифрованную область обратно
//            Graphics2D g2d = originalImage.createGraphics();
//            g2d.drawImage(encryptedArea, areaDTO.startX(), areaDTO.startY(), null);
//            g2d.dispose();
//
//            // 8. Сохраняем параметры для дешифровки
//            saveDecryptionParameters(selectedArea);
//
//            return originalImage;
//
//        } finally {
//            sessionManager.clearSession();
//        }
//    }

//    public void encryptWholeImage(BufferedImage originalImage) throws Exception {
//        try {
//            // 1. Инициализация безопасной сессии
//            EncryptionSessionParams sessionDTO = sessionManager.initializeSession();
//            byte[] masterSeed = sessionManager.getMasterSeed();
//
//            // 2. Создаем DTO для всей области
//            EncryptionAreaDTO wholeAreaDTO = new EncryptionAreaDTO(
//                    0, 0, originalImage.getWidth(), originalImage.getHeight(), true);
//
//            // 3. Сегментация всего изображения
//            SegmentationParams segmentationParams = processSegmentation(originalImage, masterSeed, wholeAreaDTO);
//            sessionManager.setSegmentationParams(segmentationParams);
//
//            // 4. Генерация фрактала Мандельброта
//            MandelbrotParams mandelbrotParams = generateMandelbrot(masterSeed);
//            sessionManager.setMandelbrotParams(mandelbrotParams);
//
//            // 5. Финальное шифрование
//            performFinalEncryption(masterSeed);
//
//            // 6. Сохраняем параметры для дешифровки
//            saveDecryptionParameters(wholeAreaDTO);
//
//        } finally {
//            sessionManager.clearSession();
//        }
//    }

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

public void encryptWhole(BufferedImage originalImage,
                         MandelbrotService mandelbrotService,
                         ImageSegmentShuffler imageSegmentShuffler,
                         CryptographicService cryptographicService,
                         SceneManager sceneManager) throws Exception {

    SegmentationResult segmentationResult = imageSegmentShuffler.segmentAndShuffle(originalImage);
    BufferedImage segmentationUpdateImage = segmentationResult.shuffledImage();

    BufferedImage finalFractal = mandelbrotService
            .generateImage(segmentationUpdateImage.getWidth(), segmentationUpdateImage.getHeight());

    EncryptionResult result = new EncryptionResult(
            segmentationUpdateImage,
            finalFractal,
            new EncryptionParams(
                    new EncryptionArea(
                            0, 0,
                            segmentationUpdateImage.getWidth(),
                            segmentationUpdateImage.getHeight(),
                            true
                    ),
                    new SegmentationParams(
                            segmentationResult.segmentSize(),
                            segmentationResult.paddedWidth(),
                            segmentationResult.paddedHeight(),
                            segmentationResult.segmentMapping()
                    ),
                    mandelbrotService.getCurrentParams()
            )
    );

    // Передаем peerAddress в cryptographicService
    EncryptionDataResult cipherDataResult = cryptographicService.encryptData(result);
    BufferedImage encryptedImage = XOR.performXOR(segmentationUpdateImage, finalFractal);

    sceneManager.showEncryptFinalPanel(encryptedImage, cipherDataResult);
}

    public void encryptPart(BufferedImage originalImage,
                            MandelbrotService mandelbrotService,
                            ImageSegmentShuffler imageSegmentShuffler,
                            CryptographicService cryptographicService,
                            Rectangle2D selectedArea,
                            SceneManager sceneManager) throws Exception {

        int startX = (int) selectedArea.getMinX();
        int startY = (int) selectedArea.getMinY();
        int width = (int) selectedArea.getWidth();
        int height = (int) selectedArea.getHeight();

        // Проверка границ
        if (startX < 0 || startY < 0 ||
                startX + width > originalImage.getWidth() ||
                startY + height > originalImage.getHeight()) {
            throw new IllegalArgumentException("Выход за границы изображения!");
        }

        // Получаем выделенную область
        BufferedImage selectedImage = originalImage.getSubimage(startX, startY, width, height);

        // Обрабатываем только выделенную область
        SegmentationResult segmentationResult = imageSegmentShuffler.segmentAndShuffle(selectedImage);

        int shuffledWidth = segmentationResult.shuffledImage().getWidth();
        int shuffledHeight = segmentationResult.shuffledImage().getHeight();

        BufferedImage finalFractal = mandelbrotService.generateImage(shuffledWidth, shuffledHeight);

        // XOR только выделенной области
        BufferedImage encryptedPart = XOR.performXOR(segmentationResult.shuffledImage(), finalFractal);

        // Создаем копию всего исходного изображения
        BufferedImage finalImage = new BufferedImage(
                originalImage.getWidth(),
                originalImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        // Копируем исходное изображение
        Graphics2D g = finalImage.createGraphics();
        g.drawImage(originalImage, 0, 0, null);

        // Вставляем зашифрованную часть на нужное место
        g.drawImage(encryptedPart, startX, startY, null);
        g.dispose();

        // Создаем EncryptionResult с оригинальными размерами
        EncryptionResult result = new EncryptionResult(
                finalImage, // Теперь передаем всё изображение
                finalFractal,
                new EncryptionParams(
                        new EncryptionArea(
                                startX, startY,
                                width,
                                height,
                                false
                        ),
                        new SegmentationParams(
                                segmentationResult.segmentSize(),
                                segmentationResult.paddedWidth(),
                                segmentationResult.paddedHeight(),
                                segmentationResult.segmentMapping()
                        ),
                        mandelbrotService.getCurrentParams()
                )
        );

        // Передаем peerAddress в cryptographicService
        EncryptionDataResult cipherDataResult = cryptographicService.encryptData(result);

        // Передаем полное изображение с зашифрованной областью
        sceneManager.showEncryptFinalPanel(finalImage, cipherDataResult);
    }

    // Метод для проверки возможности шифрования
    public boolean canEncryptToPeer(InetAddress peerAddress) {
        return keyExchangeService.isConnectedTo(peerAddress);
    }

    // Метод для получения списка доступных пиров
    public Set<InetAddress> getAvailablePeers() {
        return keyExchangeService.getActiveConnections().keySet();
    }
}