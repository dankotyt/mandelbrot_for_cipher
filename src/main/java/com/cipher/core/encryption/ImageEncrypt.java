package com.cipher.core.encryption;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;

import com.cipher.core.dto.*;
import com.cipher.core.dto.segmentation.SegmentationResult;
import com.cipher.core.utils.*;
import com.cipher.core.service.encryption.MandelbrotService;
import javafx.geometry.Rectangle2D;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageEncrypt {

    private final MandelbrotService mandelbrotService;
    private final ImageSegmentShuffler imageSegmentShuffler;
    private final SceneManager sceneManager;
    private final TempFileManager tempFileManager;
    private final ImageUtils imageUtils;

    /**
     * Выполняет полное шифрование изображения.
     * Процесс включает:
     * <ol>
     *   <li>Генерацию фрактала с текущими параметрами</li>
     *   <li>Применение XOR к оригинальному изображению и фракталу</li>
     *   <li>Сегментацию и перемешивание результата XOR</li>
     *   <li>Сохранение в бинарный файл с метаданными</li>
     * </ol>
     *
     * @param originalImage оригинальное изображение для шифрования
     * @throws Exception если возникает ошибка при генерации фрактала или записи файла
     */
    public void encryptWhole(BufferedImage originalImage) throws Exception {
        byte[] salt = mandelbrotService.getSessionSalt();
        int attempts = mandelbrotService.getAttemptCount();
        MandelbrotParams params = mandelbrotService.getCurrentParams();

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        BufferedImage fractal = mandelbrotService.generateImage(
                originalWidth, originalHeight,
                params.zoom(), params.offsetX(), params.offsetY(), params.maxIter()
        );

        log.info("Encrypt: attempts={}, params: zoom={}, offsetX={}, offsetY={}, maxIter={}",
                attempts, params.zoom(), params.offsetX(), params.offsetY(), params.maxIter());

        BufferedImage xored = XOR.performXOR(originalImage, fractal);
        SegmentationResult segResult = imageSegmentShuffler.segmentAndShuffle(xored);
        BufferedImage finalImage = segResult.shuffledImage();

        byte[] imageBytes = imageUtils.imageToBytes(finalImage);
        log.info("encryptWhole: finalImage размер {}x{}, байт = {}, ожидалось {}",
                finalImage.getWidth(), finalImage.getHeight(),
                imageBytes.length, finalImage.getWidth() * finalImage.getHeight() * 3);

        int fullWidth = finalImage.getWidth();
        int fullHeight = finalImage.getHeight();
        int startX = 0;
        int startY = 0;
        int areaWidth = fullWidth;
        int areaHeight = fullHeight;

        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 * 7 + imageBytes.length);
        buffer.put(salt);
        buffer.putInt(attempts);
        buffer.putInt(startX);
        buffer.putInt(startY);
        buffer.putInt(areaWidth);
        buffer.putInt(areaHeight);
        buffer.putInt(fullWidth);
        buffer.putInt(fullHeight);
        buffer.put(imageBytes);

        log.info("Encrypt: attempts={}", attempts);

        File out = tempFileManager.saveBytesToFile(buffer.array(),
                "encrypted_whole_" + System.currentTimeMillis() + ".bin");
        sceneManager.showEncryptFinalPanel(finalImage, out);
    }

    /**
     * Выполняет частичное шифрование выбранной области изображения.
     * Шифруется только указанная область, остальная часть изображения остаётся неизменной.
     *
     * @param originalImage оригинальное изображение
     * @param selectedArea  прямоугольная область для шифрования
     * @throws Exception если возникает ошибка при шифровании или записи файла
     */
    public void encryptPart(BufferedImage originalImage, Rectangle2D selectedArea) throws Exception {
        byte[] salt = mandelbrotService.getSessionSalt();
        int attempts = mandelbrotService.getAttemptCount();
        MandelbrotParams params = mandelbrotService.getCurrentParams();

        int sx = (int) selectedArea.getMinX();
        int sy = (int) selectedArea.getMinY();
        int areaWidth = (int) selectedArea.getWidth();
        int areaHeight = (int) selectedArea.getHeight();

        BufferedImage fractal = mandelbrotService.generateImage(
                areaWidth, areaHeight,
                params.zoom(), params.offsetX(), params.offsetY(), params.maxIter()
        );

        BufferedImage areaImage = originalImage.getSubimage(sx, sy, areaWidth, areaHeight);
        BufferedImage xoredArea = XOR.performXOR(areaImage, fractal);
        SegmentationResult segResult = imageSegmentShuffler.segmentAndShuffle(xoredArea);
        BufferedImage shuffledArea = segResult.shuffledImage();

        BufferedImage finalImage = new BufferedImage(
                originalImage.getWidth(), originalImage.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D g = finalImage.createGraphics();
        g.drawImage(originalImage, 0, 0, null);
        g.drawImage(shuffledArea, sx, sy, null);
        g.dispose();

        byte[] imageBytes = imageUtils.imageToBytes(finalImage);

        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 4*4 + 4 + 4 + imageBytes.length);
        buffer.put(salt);
        buffer.putInt(attempts);
        buffer.putInt(sx);
        buffer.putInt(sy);
        buffer.putInt(areaWidth);
        buffer.putInt(areaHeight);
        buffer.putInt(finalImage.getWidth());
        buffer.putInt(finalImage.getHeight());
        buffer.put(imageBytes);

        File out = tempFileManager.saveBytesToFile(buffer.array(),
                "encrypted_partial_" + System.currentTimeMillis() + ".bin");
        sceneManager.showEncryptFinalPanel(finalImage, out);
    }
}