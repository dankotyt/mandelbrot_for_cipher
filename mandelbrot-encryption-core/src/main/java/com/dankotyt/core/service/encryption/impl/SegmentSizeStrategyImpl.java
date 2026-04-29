package com.dankotyt.core.service.encryption.impl;

import com.dankotyt.core.service.encryption.SegmentSizeStrategy;

/**
 * Стандартная реализация {@link SegmentSizeStrategy}, определяющая размер квадратного сегмента
 * по порогам максимального измерения изображения. Все пороги и размеры могут быть переопределены
 * через конструктор с параметрами.
 *
 * @author dankotyt
 * @since 1.1.0
 */
public class SegmentSizeStrategyImpl implements SegmentSizeStrategy {
    private final int smallImageThreshold;
    private final int mediumImageThreshold;
    private final int smallSegmentSize;
    private final int mediumSegmentSize;
    private final int largeSegmentSize;

    private static final int DEFAULT_SMALL_THRESHOLD = 768;
    private static final int DEFAULT_MEDIUM_THRESHOLD = 1920;
    private static final int DEFAULT_SMALL_SEGMENT = 1;
    private static final int DEFAULT_MEDIUM_SEGMENT = 4;
    private static final int DEFAULT_LARGE_SEGMENT = 16;

    /**
     * Создаёт стратегию с порогами по умолчанию:
     * ≤768 → 1, ≤1920 → 4, иначе → 16.
     */
    public SegmentSizeStrategyImpl() {
        this.smallImageThreshold = DEFAULT_SMALL_THRESHOLD;
        this.mediumImageThreshold = DEFAULT_MEDIUM_THRESHOLD;
        this.smallSegmentSize = DEFAULT_SMALL_SEGMENT;
        this.mediumSegmentSize = DEFAULT_MEDIUM_SEGMENT;
        this.largeSegmentSize = DEFAULT_LARGE_SEGMENT;
    }

    /**
     * Создаёт стратегию с индивидуальными порогами и размерами сегментов.
     *
     * @param smallImageThreshold  максимальное измерение для маленького изображения (включительно)
     * @param mediumImageThreshold максимальное измерение для среднего изображения (включительно)
     * @param smallSegmentSize     размер сегмента для маленьких изображений
     * @param mediumSegmentSize    размер сегмента для средних изображений
     * @param largeSegmentSize     размер сегмента для больших изображений
     */
    public SegmentSizeStrategyImpl(int smallImageThreshold, int mediumImageThreshold,
                                   int smallSegmentSize, int mediumSegmentSize, int largeSegmentSize) {
        this.smallImageThreshold = smallImageThreshold;
        this.mediumImageThreshold = mediumImageThreshold;
        this.smallSegmentSize = smallSegmentSize;
        this.mediumSegmentSize = mediumSegmentSize;
        this.largeSegmentSize = largeSegmentSize;
    }

    /**
     * Определяет размер сегмента на основе максимального из двух измерений.
     *
     * @param imageWidth  ширина изображения
     * @param imageHeight высота изображения
     * @return размер одной стороны сегмента (пикселей)
     */
    @Override
    public int determineSegmentSize(int imageWidth, int imageHeight) {
        int maxDim = Math.max(imageWidth, imageHeight);
        if (maxDim <= smallImageThreshold) return smallSegmentSize;
        else if (maxDim <= mediumImageThreshold) return mediumSegmentSize;
        else return largeSegmentSize;
    }
}