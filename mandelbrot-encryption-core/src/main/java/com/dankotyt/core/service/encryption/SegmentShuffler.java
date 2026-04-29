package com.dankotyt.core.service.encryption;

import com.dankotyt.core.dto.SegmentationResult;

import java.awt.image.BufferedImage;
import java.security.SecureRandom;

/**
 * Перемешивает и восстанавливает сегменты изображения заданного размера.
 * Используется в процессе шифрования/дешифрования для дополнительной диффузии.
 *
 * @author dankotyt
 * @since 1.1.0
 */
public interface SegmentShuffler {

    /**
     * Разбивает изображение на квадратные сегменты и случайным образом переставляет их.
     *
     * @param image исходное изображение.
     * @param prng  детерминированный генератор для перемешивания.
     * @return результат перемешивания, включающий перемешанное изображение и размер сегмента.
     */
    SegmentationResult segmentAndShuffle(BufferedImage image, SecureRandom prng);

    /**
     * Восстанавливает оригинальное изображение из перемешанного, зная начальные размеры.
     *
     * @param shuffledImage  перемешанное изображение.
     * @param originalWidth  ширина оригинального изображения (до дополнения).
     * @param originalHeight высота оригинального изображения.
     * @param prng           тот же детерминированный генератор, что использовался при перемешивании.
     * @return восстановленное изображение.
     */
    BufferedImage unshuffle(BufferedImage shuffledImage, int originalWidth, int originalHeight, SecureRandom prng);
}