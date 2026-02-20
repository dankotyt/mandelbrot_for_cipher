package com.cipher.core.encryption;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.cipher.core.dto.*;
import com.cipher.core.dto.encryption.EncryptedData;
import com.cipher.core.dto.segmentation.SegmentationResult;
import com.cipher.core.service.encryption.SessionMandelbrotGenerator;
import com.cipher.core.utils.*;
import com.cipher.core.service.encryption.MandelbrotService;
import javafx.geometry.Rectangle2D;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.cipher.core.encryption.XOR.performXOR;
import static com.cipher.core.encryption.XOR.xorBytes;

@Component
@RequiredArgsConstructor
public class ImageEncrypt {

    private final MandelbrotService mandelbrotService;
    private final SessionMandelbrotGenerator sessionGenerator;
    private final ImageSegmentShuffler imageSegmentShuffler;
    private final EncryptionDataSerializer serializer;
    private final SceneManager sceneManager;
    private final TempFileManager tempFileManager;

public void encryptWhole(BufferedImage originalImage) throws Exception {
    if (!sessionGenerator.isInitialized()) {
        throw new IllegalStateException("Session generator not initialized. Establish connection first.");
    }

    SegmentationResult segmentationResult = imageSegmentShuffler.segmentAndShuffle(originalImage);
    BufferedImage segmentationUpdateImage = segmentationResult.shuffledImage();

    BufferedImage finalFractal = mandelbrotService
            .generateImage(segmentationUpdateImage.getWidth(), segmentationUpdateImage.getHeight());
    MandelbrotParams oneTimeParams = mandelbrotService.getCurrentParams();

    BufferedImage encryptedImage = performXOR(segmentationUpdateImage, finalFractal);

    byte[] sessionFractalBytes = sessionGenerator.getSessionFractalBytes();
    byte[] oneTimeParamsBytes = mandelbrotService.paramsToBytes(oneTimeParams);

    byte[] encryptedParams = xorBytes(oneTimeParamsBytes, sessionFractalBytes);

    byte[] encryptedImageBytes = serializer.imageToBytes(encryptedImage);

    EncryptedData data = EncryptedData.forWholeImage(
            encryptedImageBytes,
            encryptedParams,
            segmentationResult.segmentMapping(),
            segmentationResult.segmentSize(),
            encryptedImage.getWidth(),
            encryptedImage.getHeight()
    );

    // 6. Сохраняем в файл
    File outputFile = tempFileManager.saveEncryptedData(data);

    // 7. Показываем результат
    sceneManager.showEncryptFinalPanel(encryptedImage, outputFile);
}

    public void encryptPart(BufferedImage originalImage,
                            Rectangle2D selectedArea) throws Exception {
        if (!sessionGenerator.isInitialized()) {
            throw new IllegalStateException("Session generator not initialized. Establish connection first.");
        }

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
        MandelbrotParams oneTimeParams = mandelbrotService.getCurrentParams();

        // XOR только выделенной области
        BufferedImage encryptedPart = performXOR(segmentationResult.shuffledImage(), finalFractal);

        // Создаем копию всего исходного изображения
        BufferedImage finalImage = new BufferedImage(
                originalImage.getWidth(),
                originalImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        // Копируем исходное изображение
        Graphics2D g = finalImage.createGraphics();
        g.drawImage(originalImage, 0, 0, null);
        g.drawImage(encryptedPart, startX, startY, null);
        g.dispose();

        byte[] sessionFractalBytes = sessionGenerator.getSessionFractalBytes();
        byte[] oneTimeParamsBytes = mandelbrotService.paramsToBytes(oneTimeParams);
        byte[] encryptedParams = xorBytes(oneTimeParamsBytes, sessionFractalBytes);

        byte[] fullImageBytes = serializer.imageToBytes(finalImage);

        EncryptedData data = EncryptedData.forPartialImage(
                fullImageBytes,
                encryptedParams,
                segmentationResult.segmentMapping(),
                segmentationResult.segmentSize(),
                startX, startY,
                width, height,
                originalImage.getWidth(),
                originalImage.getHeight()
        );

        // 8. Сохраняем в файл
        File outputFile = tempFileManager.saveEncryptedData(data);

        // 9. Показываем результат
        sceneManager.showEncryptFinalPanel(finalImage, outputFile);
    }

    /**
     * Сохраняет EncryptedData в файл
     */
    private File saveToFile(EncryptedData data) throws IOException {
        String fileName = "encrypted_" + System.currentTimeMillis() + ".bin";
        File outputFile = new File(tempFileManager.getTempPath(), fileName);

        byte[] serializedData = serializer.serialize(data);
        Files.write(outputFile.toPath(), serializedData);

        return outputFile;
    }
}