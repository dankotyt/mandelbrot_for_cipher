package com.cipher.core.encryption;

import java.awt.*;
import java.awt.image.BufferedImage;

import com.cipher.core.dto.*;
import com.cipher.core.dto.encryption.EncryptionArea;
import com.cipher.core.dto.encryption.EncryptionDataResult;
import com.cipher.core.dto.encryption.EncryptionParams;
import com.cipher.core.dto.encryption.EncryptionResult;
import com.cipher.core.dto.segmentation.SegmentationParams;
import com.cipher.core.dto.segmentation.SegmentationResult;
import com.cipher.core.service.network.CryptoKeyManager;
import com.cipher.core.utils.*;
import com.cipher.core.service.encryption.MandelbrotService;
import javafx.geometry.Rectangle2D;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageEncrypt {
    private final DialogDisplayer dialogDisplayer;
    private MandelbrotParams mandelbrotParams;
    private final CryptoKeyManager cryptoKeyManager;

    public void initMandelbrotParams(MandelbrotParams mandelbrotParams) {
        this.mandelbrotParams = mandelbrotParams;
    }

public void encryptWhole(BufferedImage originalImage,
                         MandelbrotService mandelbrotService,
                         ImageSegmentShuffler imageSegmentShuffler,
                         CryptographicService cryptographicService,
                         SceneManager sceneManager) throws Exception {

    SegmentationResult segmentationResult = imageSegmentShuffler.segmentAndShuffle(originalImage);
    BufferedImage segmentationUpdateImage = segmentationResult.shuffledImage();

    BufferedImage finalFractal = mandelbrotService
            .generateImage(segmentationUpdateImage.getWidth(), segmentationUpdateImage.getHeight());

    BufferedImage encryptedImage = XOR.performXOR(segmentationUpdateImage, finalFractal);

    EncryptionResult result = new EncryptionResult(
            encryptedImage,
            new EncryptionParams(
                    new EncryptionArea(
                            0, 0,
                            segmentationUpdateImage.getWidth(),
                            segmentationUpdateImage.getHeight()
                    ),
                    new SegmentationParams(
                            segmentationResult.segmentSize(),
                            segmentationResult.segmentMapping()
                    ),
                    mandelbrotService.getCurrentParams()
            )
    );
    EncryptionDataResult cipherDataResult = cryptographicService.encryptData(result);

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
                finalImage,
                new EncryptionParams(
                        new EncryptionArea(
                                startX, startY,
                                width,
                                height
                        ),
                        new SegmentationParams(
                                segmentationResult.segmentSize(),
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
}