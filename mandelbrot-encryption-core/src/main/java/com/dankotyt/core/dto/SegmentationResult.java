package com.dankotyt.core.dto;

import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Результат сегментного перемешивания изображения.
 *
 * @param shuffledImage  перемешанное изображение (с дополнением до размера сегмента)
 * @param segmentSize    размер одной стороны квадратного сегмента
 * @param paddedWidth    ширина дополненного изображения
 * @param paddedHeight   высота дополненного изображения
 * @param segmentMapping отображение исходного индекса сегмента на новый индекс (null при неизвестном)
 *
 * @author dankotyt
 * @since 1.1.0
 */
public record SegmentationResult(
        BufferedImage shuffledImage,
        int segmentSize,
        int paddedWidth,
        int paddedHeight,
        Map<Integer, Integer> segmentMapping
) {}
