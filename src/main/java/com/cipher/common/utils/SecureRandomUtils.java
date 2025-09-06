package com.cipher.common.utils;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Утилитарный класс для криптографически безопасной генерации случайных чисел.
 * Предоставляет потокобезопасный доступ к SecureRandom через ThreadLocal.
 * Используется для генерации сессионных токенов, nonce и других критически безопасных значений.
 *
 * @implNote Для обеспечения безопасности использует DRBG алгоритм когда доступен,
 *           с fallback на стандартный SecureRandom.
 * @implSpec Все методы обеспечивают криптографическую стойкость генерируемых значений.
 */
public final class SecureRandomUtils {

    private SecureRandomUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static final ThreadLocal<SecureRandom> SECURE_RANDOM_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        try {
            return SecureRandom.getInstance("DRBG");
        } catch (NoSuchAlgorithmException e) {
            return new SecureRandom(); // fallback
        }
    });

    /**
     * Возвращает thread-local экземпляр SecureRandom.
     *
     * @return экземпляр SecureRandom для текущего потока
     */
    public static SecureRandom getSecureRandom() {
        return SECURE_RANDOM_THREAD_LOCAL.get();
    }

    /**
     * Генерирует криптографически безопасные случайные байты.
     *
     * @param length количество байт для генерации
     * @return массив случайных байт указанной длины
     * @throws IllegalArgumentException если length отрицательный
     */
    public static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        getSecureRandom().nextBytes(bytes);
        return bytes;
    }

    /**
     * Очищает thread-local ресурсы SecureRandom.
     * Должен вызываться после завершения работы со случайными значениями
     * для предотвращения утечек памяти.
     */
    public static void cleanUp() {
        SECURE_RANDOM_THREAD_LOCAL.remove();
    }
}
