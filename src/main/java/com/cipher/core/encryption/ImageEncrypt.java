package com.cipher.core.encryption;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import com.cipher.core.dto.*;
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

    private byte[] sessionSalt;
    private SecureRandom paramsPrng;
    private SecureRandom segmentationPrng;
    private int attemptCount;
    private BufferedImage fractal;

    /**
     * Подготавливает сессию шифрования на основе общего секрета.
     * Все криптографические материалы хранятся внутри ImageEncrypt.
     *
     * @param sharedSecret общий секрет от DH
     * @throws Exception если ошибка инициализации
     */
    public void prepareSession(byte[] sharedSecret) throws Exception {
        // 1. Генерируем соль криптостойким генератором
        byte[] salt = new byte[16];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(salt);
        this.sessionSalt = salt.clone();

        // 2. HKDF выработка ключей
        byte[] prk = HKDF.extract(salt, sharedSecret);
        byte[] keyFractalParams = HKDF.expand(prk, "fractal-params".getBytes(StandardCharsets.UTF_8), 32);
        byte[] keySegmentation = HKDF.expand(prk, "segmentation".getBytes(StandardCharsets.UTF_8), 32);

        // 3. Инициализируем PRNG для параметров фрактала
        this.paramsPrng = SecureRandom.getInstance("SHA1PRNG");
        this.paramsPrng.setSeed(keyFractalParams);

        // 4. Инициализируем PRNG для перемешивания
        this.segmentationPrng = SecureRandom.getInstance("SHA1PRNG");
        this.segmentationPrng.setSeed(keySegmentation);

        // 5. Сбрасываем счётчик попыток
        this.attemptCount = 0;
        this.fractal = null;

        log.info("Сессия подготовлена, сгенерирована новая соль");
    }

    /**
     * Генерирует следующий фрактал для текущей сессии.
     * Увеличивает счётчик попыток.
     */
    public BufferedImage generateNextFractal(int width, int height) {
        attemptCount++;
        MandelbrotParams params = mandelbrotService.generateParams(paramsPrng);
        log.debug("Генерация фрактала: попытка {}, params={}", attemptCount, params);

        fractal = mandelbrotService.generateImage(
                width, height,
                params.zoom(), params.offsetX(), params.offsetY(), params.maxIter()
        );

        return fractal;
    }

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
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        if (fractal == null || fractal.getWidth() != width || fractal.getHeight() != height) {
            log.warn("Фрактал отсутствует или не соответствует размеру, генерируем заново");
            generateNextFractal(width, height);
        }

        // Применяем XOR
        BufferedImage xored = XOR.performXOR(originalImage, fractal);

        // Перемешиваем сегменты
        BufferedImage shuffled = imageSegmentShuffler.segmentAndShuffle(xored, segmentationPrng).shuffledImage();

        // Сохраняем результат
        File outFile = saveEncryptedImage(shuffled, width, height, 0, 0, width, height);

        sceneManager.showEncryptFinalPanel(shuffled, outFile);
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
        int origWidth = originalImage.getWidth();
        int origHeight = originalImage.getHeight();

        int sx = (int) selectedArea.getMinX();
        int sy = (int) selectedArea.getMinY();
        int areaWidth = (int) selectedArea.getWidth();
        int areaHeight = (int) selectedArea.getHeight();

        if (fractal == null || fractal.getWidth() != areaWidth || fractal.getHeight() != areaHeight) {
            log.warn("Фрактал отсутствует или не соответствует размеру области, генерируем заново");
            generateNextFractal(areaWidth, areaHeight);
        }

        // Извлекаем и шифруем область
        BufferedImage areaImage = originalImage.getSubimage(sx, sy, areaWidth, areaHeight);
        BufferedImage xoredArea = XOR.performXOR(areaImage, fractal);
        BufferedImage shuffledArea = imageSegmentShuffler.segmentAndShuffle(xoredArea, segmentationPrng).shuffledImage();

        // Собираем итоговое изображение
        BufferedImage finalImage = new BufferedImage(origWidth, origHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = finalImage.createGraphics();
        g.drawImage(originalImage, 0, 0, null);
        g.drawImage(shuffledArea, sx, sy, null);
        g.dispose();

        // Сохраняем результат
        File outFile = saveEncryptedImage(finalImage, origWidth, origHeight, sx, sy, areaWidth, areaHeight);

        sceneManager.showEncryptFinalPanel(finalImage, outFile);
    }

    /**
     * Сохраняет зашифрованное изображение с метаданными
     */
    private File saveEncryptedImage(BufferedImage image, int originalWidth, int originalHeight,
                                    int startX, int startY, int areaWidth, int areaHeight) throws IOException {
        byte[] imageBytes = imageUtils.imageToBytes(image);

        // Формат: соль(16) + attempts(4) + координаты и размеры(6 int) + данные
        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 24 + imageBytes.length);
        buffer.put(sessionSalt);
        buffer.putInt(attemptCount);
        buffer.putInt(startX);
        buffer.putInt(startY);
        buffer.putInt(areaWidth);
        buffer.putInt(areaHeight);
        buffer.putInt(originalWidth);
        buffer.putInt(originalHeight);
        buffer.put(imageBytes);

        File outFile = tempFileManager.saveBytesToFile(buffer.array(),
                "encrypted_" + System.currentTimeMillis() + ".bin");
        log.info("Зашифрованный файл сохранён: {}, размер данных {} байт",
                outFile.getName(), imageBytes.length);
        return outFile;
    }
}