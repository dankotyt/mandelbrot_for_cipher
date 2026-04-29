package com.dankotyt.core.service.encryption;

/**
 * Определяет размер стороны квадратного сегмента в зависимости от размеров изображения.
 *
 * @author dankotyt
 * @since 1.1.0
 */
public interface SegmentSizeStrategy {

    /**
     * Возвращает размер сегмента (стороны квадрата) для заданных размеров изображения.
     *
     * @param imageWidth  ширина изображения.
     * @param imageHeight высота изображения.
     * @return размер сегмента в пикселях.
     */
    int determineSegmentSize(int imageWidth, int imageHeight);
}