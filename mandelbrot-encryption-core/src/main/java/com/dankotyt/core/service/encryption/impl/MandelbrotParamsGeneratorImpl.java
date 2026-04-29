package com.dankotyt.core.service.encryption.impl;

import com.dankotyt.core.dto.MandelbrotParams;
import com.dankotyt.core.service.encryption.MandelbrotParamsGenerator;
import java.security.SecureRandom;

/**
 * Реализация {@link MandelbrotParamsGenerator} с настраиваемыми диапазонами
 * для генерации случайных параметров фрактала Мандельброта.
 * Предоставляет конструктор по умолчанию с оптимальными значениями и
 * конструктор для полной кастомизации всех границ.
 *
 * @author dankotyt
 * @since 1.1.0
 */
public class MandelbrotParamsGeneratorImpl implements MandelbrotParamsGenerator {
    private final double zoomMin;
    private final double zoomMax;
    private final double offsetXMin;
    private final double offsetXMax;
    private final double offsetYNegMin;
    private final double offsetYNegMax;
    private final double offsetYPosMin;
    private final double offsetYPosMax;
    private final int iterBase;
    private final int iterStep;
    private final int iterMaxSteps;

    private static final double DEFAULT_ZOOM_MIN = 10_000;
    private static final double DEFAULT_ZOOM_MAX = 10_000 + 701 * 140;
    private static final double DEFAULT_OFFSET_X_MIN = -0.9998;
    private static final double DEFAULT_OFFSET_X_MAX = 0.45;
    private static final double DEFAULT_OFFSET_Y_NEG_MIN = -0.7;
    private static final double DEFAULT_OFFSET_Y_NEG_MAX = -0.1;
    private static final double DEFAULT_OFFSET_Y_POS_MIN = 0.1;
    private static final double DEFAULT_OFFSET_Y_POS_MAX = 0.7;
    private static final int DEFAULT_ITER_BASE = 250;
    private static final int DEFAULT_ITER_STEP = 10;
    private static final int DEFAULT_ITER_MAX_STEPS = 100;

    /**
     * Создаёт генератор с параметрами по умолчанию, обеспечивающими разнообразные фракталы.
     */
    public MandelbrotParamsGeneratorImpl() {
        this.zoomMin = DEFAULT_ZOOM_MIN;
        this.zoomMax = DEFAULT_ZOOM_MAX;
        this.offsetXMin = DEFAULT_OFFSET_X_MIN;
        this.offsetXMax = DEFAULT_OFFSET_X_MAX;
        this.offsetYNegMin = DEFAULT_OFFSET_Y_NEG_MIN;
        this.offsetYNegMax = DEFAULT_OFFSET_Y_NEG_MAX;
        this.offsetYPosMin = DEFAULT_OFFSET_Y_POS_MIN;
        this.offsetYPosMax = DEFAULT_OFFSET_Y_POS_MAX;
        this.iterBase = DEFAULT_ITER_BASE;
        this.iterStep = DEFAULT_ITER_STEP;
        this.iterMaxSteps = DEFAULT_ITER_MAX_STEPS;
    }

    /**
     * Создаёт генератор с заданными границами для всех параметров.
     *
     * @param zoomMin       минимальное значение увеличения
     * @param zoomMax       максимальное значение увеличения
     * @param offsetXMin    минимальное смещение по X
     * @param offsetXMax    максимальное смещение по X
     * @param offsetYNegMin минимальный Y в отрицательной области
     * @param offsetYNegMax максимальный Y в отрицательной области
     * @param offsetYPosMin минимальный Y в положительной области
     * @param offsetYPosMax максимальный Y в положительной области
     * @param iterBase      базовое количество итераций
     * @param iterStep      шаг увеличения итераций
     * @param iterMaxSteps  максимальное число шагов (итоговый максимум = iterBase + iterMaxSteps*iterStep)
     */
    public MandelbrotParamsGeneratorImpl(double zoomMin, double zoomMax,
                                         double offsetXMin, double offsetXMax,
                                         double offsetYNegMin, double offsetYNegMax,
                                         double offsetYPosMin, double offsetYPosMax,
                                         int iterBase, int iterStep, int iterMaxSteps) {
        this.zoomMin = zoomMin;
        this.zoomMax = zoomMax;
        this.offsetXMin = offsetXMin;
        this.offsetXMax = offsetXMax;
        this.offsetYNegMin = offsetYNegMin;
        this.offsetYNegMax = offsetYNegMax;
        this.offsetYPosMin = offsetYPosMin;
        this.offsetYPosMax = offsetYPosMax;
        this.iterBase = iterBase;
        this.iterStep = iterStep;
        this.iterMaxSteps = iterMaxSteps;
    }

    /**
     * Генерирует случайные параметры фрактала на основе предоставленного {@link SecureRandom}.
     *
     * @param prng криптографически стойкий генератор случайных чисел
     * @return новый экземпляр {@link MandelbrotParams}
     * @throws IllegalArgumentException если prng равен null
     */
    @Override
    public MandelbrotParams generate(SecureRandom prng) {
        if (prng == null) {
            throw new IllegalArgumentException("SecureRandom cannot be null");
        }
        double zoom = zoomMin + prng.nextDouble() * (zoomMax - zoomMin);
        double offsetX = offsetXMin + prng.nextDouble() * (offsetXMax - offsetXMin);
        boolean useNegativeY = prng.nextBoolean();
        double offsetYMin = useNegativeY ? offsetYNegMin : offsetYPosMin;
        double offsetYMax = useNegativeY ? offsetYNegMax : offsetYPosMax;
        double offsetY = offsetYMin + prng.nextDouble() * (offsetYMax - offsetYMin);
        int maxIter = iterBase + prng.nextInt(iterMaxSteps + 1) * iterStep;
        return new MandelbrotParams(zoom, offsetX, offsetY, maxIter);
    }
}