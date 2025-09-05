package com.cipher.client.utils;

import org.bitcoinj.crypto.MnemonicCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Утилитарный класс для работы с криптографическими ключами.
 * Предоставляет методы для генерации ключевых пар из seed-фразы
 * и создания идентификаторов пользователей.
 */
public class KeysUtils {
    private static final Logger logger = LoggerFactory.getLogger(KeysUtils.class);

    /**
     * Record, содержащий криптографические ключи и идентификатор пользователя.
     *
     * @param privateKey приватный ключ
     * @param publicKey публичный ключ
     * @param userId идентификатор пользователя
     */
    public record CryptoKeys(PrivateKey privateKey, PublicKey publicKey, String userId) {}

    /**
     * Создает криптографические ключи из seed-фразы.
     *
     * @param words список слов seed-фразы
     * @return объект CryptoKeys с ключами и идентификатором пользователя
     * @throws NoSuchAlgorithmException если алгоритм генерации ключей не найден
     * @throws GeneralSecurityException если произошла ошибка безопасности
     */
    public static CryptoKeys createKeysFromWords(List<String> words) throws NoSuchAlgorithmException, GeneralSecurityException {
        byte[] masterSeed = MnemonicCode.toSeed(words, ""); // 64 bytes
        Arrays.fill(masterSeed, 31, 63, (byte) 0);
        try {
            // Для EdDSA используем первые 32 байта из seed
            byte[] keyMaterial = Arrays.copyOfRange(masterSeed, 0, 32);
            KeyPair keyPair = generateKeyPairFromSeed(keyMaterial);
            PublicKey publicKey = keyPair.getPublic();
            String userId = createUserId(publicKey);

            logger.info("Generated PublicKey: {}", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
            logger.info("Generated UserId: {}", userId);

            return new CryptoKeys(keyPair.getPrivate(), publicKey, userId);
        } finally {
            Arrays.fill(masterSeed, (byte) 0); // Стираем мастер-сид из памяти
        }
    }

    /**
     * Генерирует пару ключей из seed материала.
     *
     * @param seed байтовый массив seed материала
     * @return пара криптографических ключей
     * @throws NoSuchAlgorithmException если алгоритм не найден
     */
    private static KeyPair generateKeyPairFromSeed(byte[] seed) throws NoSuchAlgorithmException {
        SecureRandom deterministicRandom = SecureRandom.getInstance("SHA1PRNG");
        deterministicRandom.setSeed(seed);

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EdDSA");
        keyPairGenerator.initialize(255, deterministicRandom);
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Создает идентификатор пользователя на основе публичного ключа.
     *
     * @param publicKey публичный ключ пользователя
     * @return идентификатор пользователя в виде base64 строки
     * @throws NoSuchAlgorithmException если алгоритм SHA-256 не найден
     */
    private static String createUserId(PublicKey publicKey) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKey.getEncoded());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Arrays.copyOfRange(hash, 0, 16));
    }

    /**
     * Конвертирует публичный ключ в base64 строку.
     *
     * @param publicKey публичный ключ для конвертации
     * @return base64 представление публичного ключа
     */
    public static String getPublicKeyBase64(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }
}
