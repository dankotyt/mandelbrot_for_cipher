package com.cipher.core.encryption;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;

import com.cipher.core.dto.*;
import com.cipher.core.dto.encryption.EncryptedData;
import com.cipher.core.dto.segmentation.SegmentationResult;
import com.cipher.core.utils.*;
import com.cipher.core.service.encryption.MandelbrotService;
import javafx.geometry.Rectangle2D;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageEncrypt {

    private final MandelbrotService mandelbrotService;
    private final ImageSegmentShuffler imageSegmentShuffler;
    private final EncryptionDataSerializer serializer;
    private final SceneManager sceneManager;
    private final TempFileManager tempFileManager;
    private final ImageUtils imageUtils;

    public void encryptWhole(BufferedImage originalImage) throws Exception {
        byte[] salt = mandelbrotService.getSessionSalt();
        int attempts = mandelbrotService.getAttemptCount();
        MandelbrotParams params = mandelbrotService.getCurrentParams();

        BufferedImage fractal = mandelbrotService.generateImage(
                originalImage.getWidth(), originalImage.getHeight(),
                params.zoom(), params.offsetX(), params.offsetY(), params.maxIter()
        );

        BufferedImage xored = XOR.performXOR(originalImage, fractal);
        SegmentationResult segResult = imageSegmentShuffler.segmentAndShuffle(xored);
        BufferedImage finalImage = segResult.shuffledImage();

        byte[] imageBytes = imageUtils.imageToBytes(finalImage);

        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 4 + 4 + imageBytes.length);
        buffer.put(salt);
        buffer.putInt(attempts);
        buffer.putInt(finalImage.getWidth());
        buffer.putInt(finalImage.getHeight());
        buffer.put(imageBytes);

        File out = tempFileManager.saveBytesToFile(buffer.array(),
                "encrypted_whole_" + System.currentTimeMillis() + ".bin");
        sceneManager.showEncryptFinalPanel(finalImage, out);
    }

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

    /**
     * Сохраняет EncryptedData в файл
     */
//    private File saveToFile(EncryptedData data) throws IOException {
//        String fileName = "encrypted_" + System.currentTimeMillis() + ".bin";
//        File outputFile = new File(tempFileManager.getTempPath(), fileName);
//
//        byte[] serializedData = serializer.serialize(data);
//        Files.write(outputFile.toPath(), serializedData);
//
//        return outputFile;
//    }
}