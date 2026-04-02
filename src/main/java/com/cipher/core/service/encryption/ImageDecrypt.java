package com.cipher.core.service.encryption;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.service.network.CryptoKeyManager;
import com.cipher.core.utils.ImageUtils;
import com.cipher.core.utils.FileManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageDecrypt {
    private final MandelbrotService mandelbrotService;
    private final ImageSegmentShuffler imageSegmentShuffler;
    private final ImageUtils imageUtils;
    private final FileManager fileManager;
    private final CryptoKeyManager cryptoKeyManager;

    /**
     * Дешифрует изображение из бинарного файла, созданного методом encryptWhole.
     * Процесс включает:
     * <ol>
     *   <li>Чтение соли, количества попыток, координат области и размеров</li>
     *   <li>Восстановление ключей через HKDF с использованием общего секрета</li>
     *   <li>Восстановление параметров фрактала путём прокрутки PRNG</li>
     *   <li>Генерацию фрактала и обратную сегментацию области</li>
     *   <li>XOR для восстановления оригинального изображения</li>
     * </ol>
     *
     * @param encryptedFile файл с зашифрованными данными
     * @return расшифрованное изображение
     * @throws Exception если возникает ошибка при чтении файла или дешифровании
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

        log.info("Decrypt: attempts={}", attempts);

        log.info("decryptImage: fullWidth={}, fullHeight={}, imageBytes.length={}, ожидалось {}",
                fullWidth, fullHeight, imageBytes.length, fullWidth * fullHeight * 3);
        if (imageBytes.length != fullWidth * fullHeight * 3) {
            throw new IllegalArgumentException(String.format(
                    "Несоответствие длины: получили %d, ожидали %d (ширина %d, высота %d)",
                    imageBytes.length, fullWidth * fullHeight * 3, fullWidth, fullHeight));
        }

        BufferedImage encryptedImage = imageUtils.bytesToImage(imageBytes, fullWidth, fullHeight);

        InetAddress peer = cryptoKeyManager.getConnectedPeer();
        byte[] sharedSecret = cryptoKeyManager.getMasterSeedFromDH(InetAddress.getByName(peer.getHostAddress()));

        byte[] prk = HKDF.extract(salt, sharedSecret);
        byte[] keyFractalParams = HKDF.expand(prk, "fractal-params".getBytes(StandardCharsets.UTF_8), 32);
        byte[] keySegmentation = HKDF.expand(prk, "segmentation".getBytes(StandardCharsets.UTF_8), 32);

        SecureRandom paramsPrng = SecureRandom.getInstance("SHA1PRNG");
        paramsPrng.setSeed(keyFractalParams);
        MandelbrotParams params = null;
        for (int i = 0; i < Math.max(1, attempts); i++) {
            params = mandelbrotService.generateParams(paramsPrng);
        }
        log.info("Decrypt: attempts={}, params: zoom={}, offsetX={}, offsetY={}, maxIter={}",
                attempts, params.zoom(), params.offsetX(), params.offsetY(), params.maxIter());

        SecureRandom segPrng = SecureRandom.getInstance("SHA1PRNG");
        segPrng.setSeed(keySegmentation);

        // Извлекаем область, которая подвергалась шифрованию
        BufferedImage encryptedArea = encryptedImage.getSubimage(startX, startY, areaWidth, areaHeight);

        // Генерируем фрактал для размера области
        BufferedImage fractal = mandelbrotService.generateImage(
                areaWidth, areaHeight,
                params.zoom(), params.offsetX(), params.offsetY(), params.maxIter()
        );

        // Обратная сегментация области
        BufferedImage unshuffledArea = imageSegmentShuffler.unshuffle(
                encryptedArea, areaWidth, areaHeight, segPrng
        );

        // XOR области с фракталом
        BufferedImage decryptedArea = XOR.performXOR(unshuffledArea, fractal);

        // Вставляем расшифрованную область обратно в полное изображение
        BufferedImage result = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(encryptedImage, 0, 0, null);
        g.drawImage(decryptedArea, startX, startY, null);
        g.dispose();

        fileManager.saveBufferedImageToTemp(result, "decrypted_image.png");
        return result;
    }
}