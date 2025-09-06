package com.cipher.common.utils;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Утилитарный класс для детерминистичной генерации случайных чисел на основе seed.
 * Обеспечивает воспроизводимость результатов для одинаковых seed значений.
 * Используется для генерации криптографических ключей из seed-фраз.
 *
 * @implNote Использует алгоритм SHA1PRNG для детерминистичной генерации
 * @implSpec Важно: один и тот же seed всегда produces одинаковую последовательность,
 *           что является expected behavior для детерминистичной генерации ключей.
 */
public final class DeterministicRandomUtils {

    private DeterministicRandomUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Создает детерминистичный SecureRandom на основе предоставленного seed.
     * Для одного и того же seed всегда возвращает идентичную последовательность значений.
     *
     * @param seed байтовый массив используемый как seed для генерации
     * @return детерминистичный экземпляр SecureRandom
     * @throws IllegalStateException если алгоритм SHA1PRNG недоступен
     *
     * @see SecureRandom#getInstance(String)
     * @see SecureRandom#setSeed(byte[])
     */
    public static SecureRandom createDeterministicRandom(byte[] seed) {
        try {
            SecureRandom deterministicRandom = SecureRandom.getInstance("SHA1PRNG");
            deterministicRandom.setSeed(seed);
            return deterministicRandom;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA1PRNG not available", e);
        }
    }
}
