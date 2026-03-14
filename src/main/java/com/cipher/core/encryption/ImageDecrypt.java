package com.cipher.core.encryption;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.dto.encryption.EncryptedData;
import com.cipher.core.service.network.CryptoKeyManager;
import com.cipher.core.utils.EncryptionDataSerializer;
import com.cipher.core.service.encryption.MandelbrotService;
import com.cipher.core.utils.ImageUtils;
import com.cipher.core.utils.TempFileManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.cipher.core.encryption.XOR.performXOR;
import static com.cipher.core.encryption.XOR.xorBytes;

@Component
@RequiredArgsConstructor
public class ImageDecrypt {
    private final MandelbrotService mandelbrotService;
    private final ImageSegmentShuffler imageSegmentShuffler;
    private final ImageUtils imageUtils;
    private final TempFileManager tempFileManager;
    private final CryptoKeyManager cryptoKeyManager;

    /**
     * Дешифрование из файла .bin
     */
    public BufferedImage decryptImage(File encryptedFile) throws Exception {
        byte[] fileData = Files.readAllBytes(encryptedFile.toPath());
        ByteBuffer buf = ByteBuffer.wrap(fileData);

        byte[] salt = new byte[16];
        buf.get(salt);
        int attempts = buf.getInt();
        int startX = buf.getInt();
        int startY = buf.getInt();
        int areaWidth = buf.getInt();
        int areaHeight = buf.getInt();
        int fullWidth = buf.getInt();
        int fullHeight = buf.getInt();

        byte[] imageBytes = new byte[buf.remaining()];
        buf.get(imageBytes);

        BufferedImage encryptedImage = imageUtils.bytesToImage(imageBytes, fullWidth, fullHeight);

        InetAddress peer = cryptoKeyManager.getConnectedPeer();
        byte[] sharedSecret = cryptoKeyManager.getMasterSeedFromDH(InetAddress.getByName(peer.getHostAddress()));

        byte[] prk = HKDF.extract(salt, sharedSecret);
        byte[] keyFractalParams = HKDF.expand(prk, "fractal-params".getBytes(StandardCharsets.UTF_8), 32);
        byte[] keySegmentation = HKDF.expand(prk, "segmentation".getBytes(StandardCharsets.UTF_8), 32);

        SecureRandom paramsPrng = SecureRandom.getInstance("SHA1PRNG");
        paramsPrng.setSeed(keyFractalParams);
        for (int i = 0; i < attempts; i++) {
            mandelbrotService.generateParams(paramsPrng); // прокрутка
        }
        MandelbrotParams params = mandelbrotService.generateParams(paramsPrng);

        imageSegmentShuffler.initialize(keySegmentation);

        // Извлекаем область, которая подвергалась шифрованию
        BufferedImage encryptedArea = encryptedImage.getSubimage(startX, startY, areaWidth, areaHeight);

        // Генерируем фрактал для размера области
        BufferedImage fractal = mandelbrotService.generateImage(
                areaWidth, areaHeight,
                params.zoom(), params.offsetX(), params.offsetY(), params.maxIter()
        );

        // Обратная сегментация области
        BufferedImage unshuffledArea = imageSegmentShuffler.unshuffle(encryptedArea, areaWidth, areaHeight);

        // XOR области с фракталом
        BufferedImage decryptedArea = XOR.performXOR(unshuffledArea, fractal);

        // Вставляем расшифрованную область обратно в полное изображение
        BufferedImage result = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(encryptedImage, 0, 0, null);
        g.drawImage(decryptedArea, startX, startY, null);
        g.dispose();

        tempFileManager.saveBufferedImageToTemp(result, "decrypted_image.png");
        return result;
    }

    /**
     * Дешифрование из EncryptedData
     */
//    private BufferedImage decryptImage(EncryptedData data) throws Exception {
//
//        // Расшифровываем параметры одноразового фрактала
//        byte[] oneTimeParamsBytes = xorBytes(
//                data.encryptedOneTimeParams(),
//                sessionFractalBytes
//        );
//        MandelbrotParams oneTimeParams = mandelbrotService.bytesToParams(oneTimeParamsBytes);
//
//        // Восстанавливаем изображение из байт
//        BufferedImage encryptedImage = serializer.bytesToImage(data.encryptedImage());
//
//        if (data.isPartial()) {
//            return decryptPartial(encryptedImage, data, oneTimeParams);
//        } else {
//            return decryptWhole(encryptedImage, data, oneTimeParams);
//        }
//    }

    /**
     * Дешифрование полного изображения
     */
    private BufferedImage decryptWhole(
            BufferedImage encryptedImage,
            EncryptedData data,
            MandelbrotParams oneTimeParams
    ) {

        // Генерируем одноразовый фрактал
        BufferedImage oneTimeFractal = mandelbrotService.generateImage(
                encryptedImage.getWidth(),
                encryptedImage.getHeight(),
                oneTimeParams.zoom(),
                oneTimeParams.offsetX(),
                oneTimeParams.offsetY(),
                oneTimeParams.maxIter()
        );

        // XOR для получения перемешанного изображения
        BufferedImage shuffledImage = performXOR(encryptedImage, oneTimeFractal);

        // Восстанавливаем оригинальный порядок сегментов
        return imageSegmentShuffler.unshuffledSegments(
                shuffledImage,
                data.segmentMapping(),
                data.segmentSize()
        );
    }

    /**
     * Дешифрование части изображения
     */
    private BufferedImage decryptPartial(
            BufferedImage encryptedImage,
            EncryptedData data,
            MandelbrotParams oneTimeParams
    ) {

        // 1. Извлекаем зашифрованную область
        BufferedImage encryptedArea = encryptedImage.getSubimage(
                data.areaStartX(),
                data.areaStartY(),
                data.areaWidth(),
                data.areaHeight()
        );

        // 2. Генерируем одноразовый фрактал для размера области
        BufferedImage oneTimeFractal = mandelbrotService.generateImage(
                data.areaWidth(),
                data.areaHeight(),
                oneTimeParams.zoom(),
                oneTimeParams.offsetX(),
                oneTimeParams.offsetY(),
                oneTimeParams.maxIter()
        );

        // 3. XOR области с одноразовым фракталом
        BufferedImage shuffledArea = performXOR(encryptedArea, oneTimeFractal);

        // 4. Восстанавливаем сегменты в области
        BufferedImage decryptedArea = imageSegmentShuffler.unshuffledSegments(
                shuffledArea,
                data.segmentMapping(),
                data.segmentSize()
        );

        // 5. Создаем ПОЛНОЕ изображение
        BufferedImage result = new BufferedImage(
                encryptedImage.getWidth(),
                encryptedImage.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        // 6. Копируем полное изображение и заменяем область
        Graphics2D g = result.createGraphics();
        g.drawImage(encryptedImage, 0, 0, null);
        g.drawImage(decryptedArea, data.areaStartX(), data.areaStartY(), null);
        g.dispose();

        return result;
    }
}