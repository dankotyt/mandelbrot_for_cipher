package com.dankotyt.core.service.encryption;

import com.dankotyt.core.dto.EncryptedData;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Шифратор изображений с использованием фракталов Мандельброта и ECDH.
 * Предоставляет методы полного и частичного шифрования.
 *
 * @author dankotyt
 * @since 1.1.0
 */
public interface ImageEncryptor {

    /**
     * Инициализирует сессию шифрования на основе общего секрета.
     *
     * @param sharedSecret общий секрет, полученный по протоколу ECDH.
     * @throws InvalidKeyException      если соль не может быть использована как ключ HMAC.
     * @throws NoSuchAlgorithmException если HMAC-SHA256 недоступен.
     * @throws IllegalArgumentException если sharedSecret == null.
     */
    void prepareSession(byte[] sharedSecret) throws InvalidKeyException, NoSuchAlgorithmException;

    /**
     * Генерирует следующий фрактал Мандельброта для текущей сессии.
     *
     * @param width  ширина фрактала.
     * @param height высота фрактала.
     * @return сгенерированное изображение фрактала.
     */
    BufferedImage generateNextFractal(int width, int height);

    /**
     * Шифрует изображение целиком.
     *
     * @param originalImage исходное изображение.
     * @return зашифрованные данные с метаинформацией.
     */
    EncryptedData encryptWhole(BufferedImage originalImage);

    /**
     * Шифрует прямоугольную область изображения, заданную с помощью {@link Rectangle2D}.
     *
     * @param originalImage исходное изображение.
     * @param selectedArea  выделенная область (координаты преобразуются в целые).
     * @return зашифрованные данные с информацией об области.
     */
    EncryptedData encryptPart(BufferedImage originalImage, Rectangle2D selectedArea);

    /**
     * Шифрует прямоугольную область, заданную целыми координатами.
     *
     * @param originalImage исходное изображение.
     * @param sx            X-координата левого верхнего угла области.
     * @param sy            Y-координата левого верхнего угла области.
     * @param areaWidth     ширина области.
     * @param areaHeight    высота области.
     * @return зашифрованные данные с метаданными области и полного изображения.
     */
    EncryptedData encryptPart(BufferedImage originalImage, int sx, int sy, int areaWidth, int areaHeight);
}