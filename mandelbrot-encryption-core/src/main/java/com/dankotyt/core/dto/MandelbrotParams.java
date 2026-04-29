package com.dankotyt.core.dto;

/**
 * Параметры для генерации фрактала Мандельброта.
 *
 * @param zoom    коэффициент увеличения (положительное число)
 * @param offsetX смещение центра по оси X
 * @param offsetY смещение центра по оси Y
 * @param maxIter максимальное количество итераций
 *
 * @author dankotyt
 * @since 1.1.0
 */
public record MandelbrotParams(double zoom, double offsetX, double offsetY, int maxIter) { }
