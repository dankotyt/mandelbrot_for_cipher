package com.cipher.core.service;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.dto.neww.*;
import com.cipher.core.encryption.CryptographicService;
import com.cipher.core.encryption.ImageSegmentShuffler;
import com.cipher.core.utils.DeterministicRandomGenerator;
import com.cipher.core.encryption.XOR;
import com.cipher.core.utils.ImageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ImageEncryptionService {
    private final DeterministicRandomGenerator drbg;
    private final ImageSegmentShuffler imageSegmentShuffler;
    private final MandelbrotService mandelbrotService;
    private final CryptographicService cryptoService;

    private byte[] masterSeed;
    private MandelbrotParams mandelbrotParams;
    private SegmentationResult segmentationResult;
    
    public void initMandelbrotParams(MandelbrotParams mandelbrotParams) {
        this.mandelbrotParams = mandelbrotParams;
    }

//    public SegmentationResult prepareEncryption(BufferedImage image) {
//
//        masterSeed = generateMasterSeed();
//        drbg.initialize(masterSeed);
//
//        BufferedImage targetCopy = ImageUtils.copyImage(image);
//        segmentationResult = imageSegmentShuffler.segmentAndShuffle(targetCopy);
//        return segmentationResult;
//    }

    public BufferedImage performEncryption(EncryptionParams params, BufferedImage originalImage) throws Exception {
        // 1. Сегментируем оригинал с теми же параметрами
        BufferedImage targetImage = extractTargetArea(originalImage, params.area());
        BufferedImage targetCopy = ImageUtils.copyImage(targetImage);

        /*todo если шифруем всю картинку - юзаем уже ранее зашафленное изображение;
               если часть - то шафлим только часть. Для этого НЕ НУЖНО передавать masterSeed,
               т.к. drbg уже там проинициализирован;
         */
        if (!params.area().isWhole()) {
            segmentationResult = imageSegmentShuffler.reshufflePartOfImage(targetCopy);
        }

        MandelbrotParams finalParams = mandelbrotParams.withSize(
                segmentationResult.paddedWidth(),
                segmentationResult.paddedHeight()
        );

        //BufferedImage finalFractal = mandelbrotService.generateImage();

        // 3. XOR шифрование
        return XOR.performXOR(
                ImageUtils.convertToARGB(segmentationResult.shuffledImage()),
                ImageUtils.convertToARGB(finalFractal)
        );
    }

    private BufferedImage extractTargetArea(BufferedImage image, EncryptionArea area) {
        if (area.isWhole()) {
            return image;
        }
        return image.getSubimage(area.startX(), area.startY(), area.width(), area.height());
    }

    //todo при онлайн-соединении masterSeed = K, который образуется по алгоритму Д.-Х.
    private byte[] generateMasterSeed() {
        masterSeed = new byte[32];
        new SecureRandom().nextBytes(masterSeed);
        return masterSeed;
    }

    public EncryptionDataResult encryptParams(byte[] serializedParams, byte[] masterSeed) throws Exception {
        return cryptoService.encryptData(serializedParams, masterSeed);
    }

    public byte[] serializeParams(EncryptionParams params) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        try {
            // Сериализуем все параметры
            dos.writeBoolean(params.area().isWhole());
            dos.writeInt(params.area().startX());
            dos.writeInt(params.area().startY());
            dos.writeInt(params.area().width());
            dos.writeInt(params.area().height());

            dos.writeInt(params.segmentation().segmentSize());
            dos.writeInt(params.segmentation().paddedWidth());
            dos.writeInt(params.segmentation().paddedHeight());

            // Segment mapping
            Map<Integer, Integer> mapping = params.segmentation().segmentMapping();
            dos.writeInt(mapping.size());
            for (Map.Entry<Integer, Integer> entry : mapping.entrySet()) {
                dos.writeInt(entry.getKey());
                dos.writeInt(entry.getValue());
            }

            // Mandelbrot params
            dos.writeInt(params.mandelbrot().startMandelbrotWidth());
            dos.writeInt(params.mandelbrot().startMandelbrotHeight());
            dos.writeDouble(params.mandelbrot().zoom());
            dos.writeDouble(params.mandelbrot().offsetX());
            dos.writeDouble(params.mandelbrot().offsetY());
            dos.writeInt(params.mandelbrot().maxIter());

        } catch (IOException e) {
            throw new RuntimeException("Serialization error", e);
        }

        return baos.toByteArray();
    }
}