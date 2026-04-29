package com.dankotyt.core.service.encryption;

import com.dankotyt.core.dto.MandelbrotParams;

import java.security.SecureRandom;

/**
 * Генерирует случайные параметры для построения множества Мандельброта
 * с использованием криптографического {@link SecureRandom}.
 *
 * @author dankotyt
 * @since 1.1.0
 */
public interface MandelbrotParamsGenerator {

    /**
     * Генерирует новый набор параметров фрактала.
     *
     * @param prng генератор случайных чисел (не может быть null).
     * @return параметры фрактала.
     */
    MandelbrotParams generate(SecureRandom prng);
}
