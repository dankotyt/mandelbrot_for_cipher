package com.dankotyt.core.service.encryption.impl;

import com.dankotyt.core.dto.MandelbrotParams;
import com.dankotyt.core.dto.EncryptedData;
import com.dankotyt.core.service.encryption.ImageEncryptor;
import com.dankotyt.core.service.encryption.MandelbrotService;
import com.dankotyt.core.service.encryption.SegmentShuffler;
import com.dankotyt.core.service.encryption.util.HKDF;
import com.dankotyt.core.service.encryption.util.XOR;
import com.dankotyt.core.utils.ImageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Реализация {@link ImageEncryptor}, выполняющая шифрование изображений с использованием
 * фрактала Мандельброта, операции XOR и сегментного перемешивания.
 *
 * @author dankotyt
 * @since 1.1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageEncryptorImpl implements ImageEncryptor {

    private final MandelbrotService mandelbrotService;
    private final SegmentShuffler segmentShuffler;
    private final ImageUtils imageUtils;

    private byte[] sessionSalt;
    private SecureRandom paramsPrng;
    private SecureRandom segmentationPrng;
    private int attemptCount;
    private BufferedImage fractal;

    /**
     * Подготавливает сессию шифрования на основе общего секрета, полученного по протоколу ECDH.
     * <p>
     * Генерирует случайную соль, извлекает псевдослучайный ключ (PRK) с помощью HKDF-extract
     * и вырабатывает два производных ключа: для параметров фрактала и для сегментации.
     * Создаёт два детерминированных {@link SecureRandom} на основе этих ключей.
     * Счётчик попыток сбрасывается в 0, текущий фрактал обнуляется.
     * </p>
     *
     * @param sharedSecret общий секрет, полученный путём вычисления скалярного произведения ECDH.
     *                     Не должен быть {@code null}. Длина может варьироваться в зависимости
     *                     от реализации кривой.
     * @throws NoSuchAlgorithmException если алгоритм HMAC-SHA256 недоступен в текущей JRE.
     * @throws InvalidKeyException      если соль не может быть использована в качестве ключа HMAC
     * @throws IllegalArgumentException если {@code sharedSecret == null}.
     */
    @Override
    public void prepareSession(byte[] sharedSecret) throws InvalidKeyException, NoSuchAlgorithmException {
        if (sharedSecret == null) {
            throw new IllegalArgumentException("Shared secret cannot be null");
        }
        byte[] salt = new byte[16];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(salt);
        this.sessionSalt = salt.clone();

        byte[] prk = HKDF.extract(salt, sharedSecret);
        byte[] keyFractalParams = HKDF.expand(prk, "fractal-params".getBytes(StandardCharsets.UTF_8), 32);
        byte[] keySegmentation = HKDF.expand(prk, "segmentation".getBytes(StandardCharsets.UTF_8), 32);

        this.paramsPrng = SecureRandom.getInstance("SHA1PRNG");
        this.paramsPrng.setSeed(keyFractalParams);

        this.segmentationPrng = SecureRandom.getInstance("SHA1PRNG");
        this.segmentationPrng.setSeed(keySegmentation);

        this.attemptCount = 0;
        this.fractal = null;

        log.info("Сессия подготовлена, сгенерирована новая соль");
    }

    /**
     * Генерирует следующий фрактал для текущей сессии.
     * Увеличивает счётчик попыток.
     */
    @Override
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
     */
    @Override
    public EncryptedData encryptWhole(BufferedImage originalImage) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        if (fractal == null || fractal.getWidth() != width || fractal.getHeight() != height) {
            log.warn("Фрактал отсутствует или не соответствует размеру, генерируем заново");
            generateNextFractal(width, height);
        }

        BufferedImage xored = XOR.performXOR(originalImage, fractal);
        BufferedImage finalImage = segmentShuffler.segmentAndShuffle(xored, segmentationPrng).shuffledImage();

        return new EncryptedData(sessionSalt, attemptCount, 0, 0,
                width, height, width, height,
                imageUtils.imageToBytes(finalImage));
    }

    /**
     * Выполняет частичное шифрование выбранной области изображения.
     * Шифруется только указанная область, остальная часть изображения остаётся неизменной.
     *
     * @param originalImage оригинальное изображение
     * @param selectedArea  прямоугольная область для шифрования
     */
    @Override
    public EncryptedData encryptPart(BufferedImage originalImage, Rectangle2D selectedArea) {
        int sx = (int) selectedArea.getMinX();
        int sy = (int) selectedArea.getMinY();
        int areaWidth = (int) selectedArea.getWidth();
        int areaHeight = (int) selectedArea.getHeight();
        return encryptPart(originalImage, sx, sy, areaWidth, areaHeight);
    }

    /**
     * Выполняет частичное шифрование заданной прямоугольной области изображения.
     * Координаты и размеры области передаются непосредственно как целые числа.
     *
     * @param originalImage оригинальное изображение
     * @param sx            X-координата левого верхнего угла области
     * @param sy            Y-координата левого верхнего угла области
     * @param areaWidth     ширина области
     * @param areaHeight    высота области
     * @return зашифрованные данные с метаданными области и полного изображения
     */
    @Override
    public EncryptedData encryptPart(BufferedImage originalImage, int sx, int sy, int areaWidth, int areaHeight) {
        int origWidth = originalImage.getWidth();
        int origHeight = originalImage.getHeight();

        if (fractal == null || fractal.getWidth() != areaWidth || fractal.getHeight() != areaHeight) {
            log.warn("Фрактал отсутствует или не соответствует размеру области, генерируем заново");
            generateNextFractal(areaWidth, areaHeight);
        }

        BufferedImage areaImage = originalImage.getSubimage(sx, sy, areaWidth, areaHeight);
        BufferedImage xoredArea = XOR.performXOR(areaImage, fractal);
        BufferedImage shuffledArea = segmentShuffler.segmentAndShuffle(xoredArea, segmentationPrng).shuffledImage();

        BufferedImage finalImage = new BufferedImage(origWidth, origHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = finalImage.createGraphics();
        g.drawImage(originalImage, 0, 0, null);
        g.drawImage(shuffledArea, sx, sy, null);
        g.dispose();

        return new EncryptedData(sessionSalt, attemptCount, sx, sy,
                areaWidth, areaHeight, origWidth, origHeight,
                imageUtils.imageToBytes(finalImage));
    }
}