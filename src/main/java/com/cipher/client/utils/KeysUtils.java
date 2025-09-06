package com.cipher.client.utils;

import com.cipher.common.utils.DeterministicRandomUtils;
import org.bitcoinj.crypto.MnemonicCode;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

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

    private static final ThreadLocal<KeyPairGenerator> KEY_PAIR_GENERATOR_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EdDSA", "BC");
            kpg.initialize(255);
            return kpg;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize KeyPairGenerator", e);
        }
    });

    static {
        Security.addProvider(new BouncyCastleProvider());
    }
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
    public static CryptoKeys createKeysFromWords(List<String> words) throws GeneralSecurityException {
        byte[] masterSeed = MnemonicCode.toSeed(words, "");

        try {
            byte[] privateKeySeed = Arrays.copyOfRange(masterSeed, 0, 32);

            KeyPair keyPair = generateKeyPairFromSeed(privateKeySeed);
            PublicKey publicKey = keyPair.getPublic();
            String userId = createUserId(publicKey);

            return new CryptoKeys(keyPair.getPrivate(), publicKey, userId);
        } finally {
            Arrays.fill(masterSeed, (byte) 0);
        }
    }

    /**
     * Генерирует пару Ed25519/EdDSA ключей из детерминистичного сида.
     * Использует алгоритм EdDSA с кривой Ed25519 через провайдер BouncyCastle.
     * Генерация является детерминистичной - одинаковый сид всегда создает одинаковую пару ключей.
     *
     * @param seed байтовый массив сида длиной 32 байта (256 бит), используемый для детерминистичной генерации ключей
     * @return KeyPair содержащий сгенерированные приватный и публичный ключи Ed25519
     * @throws GeneralSecurityException если произошла ошибка при генерации ключей:
     *         - NoSuchAlgorithmException если алгоритм EdDSA или SHA1PRNG не доступен
     *         - InvalidParameterException если параметры инициализации неверны
     *         - ProviderException если провайдер BouncyCastle не доступен или возникла внутренняя ошибка
     *
     * @implNote Для обеспечения детерминизма используется SHA1PRNG с установленным сидом.
     *           Длина ключа устанавливается в 255 бит (стандарт для Ed25519).
     *           Используется провайдер BouncyCastle ("BC") для поддержки EdDSA.
     *
     * @implSpec Важно: передаваемый сид должен быть криптографически безопасным и храниться в секрете.
     *           После использования сид должен быть очищен из памяти для предотвращения утечки.
     *
     * @see <a href="https://tools.ietf.org/html/rfc8032">RFC 8032 - EdDSA</a>
     * @see org.bouncycastle.jce.provider.BouncyCastleProvider
     */
    private static KeyPair generateKeyPairFromSeed(byte[] seed) throws GeneralSecurityException {
        try {
            KeyPairGenerator keyPairGenerator = KEY_PAIR_GENERATOR_THREAD_LOCAL;

            // Используем ДЕТЕРМИНИСТИЧНЫЙ random для ключевой пары
            SecureRandom deterministicRandom = DeterministicRandomUtils.createDeterministicRandom(seed);

            keyPairGenerator.initialize(255, deterministicRandom);
            return keyPairGenerator.generateKeyPair();

        } catch (Exception e) {
            throw new GeneralSecurityException("Failed to generate key pair from seed", e);
        }
    }

    /**
     * Создает уникальный идентификатор пользователя на основе публичного ключа Ed25519.
     * Идентификатор генерируется как первые 16 байт SHA-256 хеша от DER-кодированного публичного ключа,
     * представленные в виде Base64 URL-safe строки без padding.
     *
     * @param publicKey публичный ключ Ed25519 для которого генерируется идентификатор
     * @return String уникальный идентификатор пользователя в формате Base64 URL-safe без padding (16 байт → 22 символа)
     * @throws NoSuchAlgorithmException если алгоритм SHA-256 не доступен в текущем окружении
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
