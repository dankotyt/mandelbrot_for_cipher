package com.dankotyt.core.service.encryption;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author @dankotyt Danil Kotlyarov
 * Класс MandelbrotThread реализует интерфейс Runnable и используется для генерации части изображения множества Мандельброта
 * в отдельном потоке.
 * <p>
 * Модифицирован для использования гладкого (smooth) окрашивания с цветовым пространством HSB,
 * что даёт плавные градиенты, характерные для художественных фракталов.
 * </p>
 */
class MandelbrotThread implements Runnable {

    private final int startX;
    private final int startY;
    private final int width;
    private final int height;
    private final double ZOOM;
    private final int MAX_ITER;
    private final double offsetX;
    private final double offsetY;
    private final BufferedImage image;
    private static final Logger logger = Logger.getLogger(MandelbrotThread.class.getName());

    /**
     * Конструктор класса MandelbrotThread.
     *
     * @param startX Начальная координата X для генерации.
     * @param startY Начальная координата Y для генерации.
     * @param width Ширина области для генерации.
     * @param height Высота области для генерации.
     * @param ZOOM Уровень масштабирования.
     * @param MAX_ITER Максимальное количество итераций.
     * @param offsetX Смещение по оси X.
     * @param offsetY Смещение по оси Y.
     * @param image Изображение для записи результатов.
     */
    public MandelbrotThread(int startX, int startY, int width, int height, double ZOOM, int MAX_ITER, double offsetX, double offsetY, BufferedImage image) {
        this.startX = startX;
        this.startY = startY;
        this.width = width;
        this.height = height;
        this.ZOOM = ZOOM;
        this.MAX_ITER = MAX_ITER;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.image = image;
    }

    /**
     * Метод run выполняет генерацию части изображения множества Мандельброта.
     * Для каждой точки (x, y) вычисляется количество итераций, после чего
     * для внешних точек используется гладкое окрашивание (smooth coloring) на основе HSB,
     * а для внутренних – фиксированный тёмно-синий цвет (0x000040).
     */
    @Override
    public void run() {
        try {
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();

            int endX = Math.min(startX + width, imageWidth);
            int endY = Math.min(startY + height, imageHeight);

            int centerX = imageWidth / 2;
            int centerY = imageHeight / 2;

            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {
                    double zx = 0, zy = 0;
                    double cX = (x - centerX) / ZOOM + offsetX;
                    double cY = (y - centerY) / ZOOM + offsetY;
                    int iter = MAX_ITER;

                    while (zx * zx + zy * zy < 4 && iter > 0) {
                        double tmp = zx * zx - zy * zy + cX;
                        zy = 2.0 * zx * zy + cY;
                        zx = tmp;
                        iter--;
                    }

                    int color;
                    if (iter > 0) {
                        // Внешняя точка – гладкий цвет на основе HSB
                        color = getSmoothColor(iter, zx, zy, MAX_ITER);
                    } else {
                        // Внутренняя точка – тёмно-синий (соответствует MANDELBROT_COLOR)
                        color = 0x000040;
                    }

                    if (x >= 0 && x < imageWidth && y >= 0 && y < imageHeight) {
                        image.setRGB(x, y, color);
                    } else {
                        logger.warning(String.format("Попытка записи за границы: x=%d, y=%d, размер=%dx%d",
                                x, y, imageWidth, imageHeight));
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Ошибка в MandelbrotThread: " + e);
        }
    }

    /**
     * Вычисляет гладкий цвет для точки, лежащей вне множества Мандельброта.
     * Используется формула «smooth coloring» для получения непрерывного значения,
     * которое затем отображается на тон (Hue) в цветовом пространстве HSB.
     *
     * @param iter   номер итерации, на которой произошёл выход
     * @param zx     реальная часть z в момент выхода
     * @param zy     мнимая часть z в момент выхода
     * @param maxIter максимальное количество итераций
     * @return целочисленное представление цвета RGB
     */
    private int getSmoothColor(int iter, double zx, double zy, int maxIter) {
        // Модуль комплексного числа z
        double modZ = Math.sqrt(zx * zx + zy * zy);
        // Предотвращаем логарифмирование нуля или слишком малых чисел
        double logModZ = Math.log(Math.max(modZ, 1.0E-7));
        double logLogModZ = Math.log(Math.max(logModZ, 1.0E-7));
        // Гладкое значение (smooth value)
        double smooth = iter + 1.0 - logLogModZ / Math.log(2.0);

        // Преобразуем smooth в тон (hue) – экспериментируйте с коэффициентами для изменения палитры
        double hue = 0.95 + 2.0 * (smooth / maxIter);
        hue = hue - Math.floor(hue); // приводим к диапазону [0, 1)

        // Фиксированные насыщенность и яркость (можно менять по вкусу)
        float saturation = 0.8f;
        float brightness = 1.0f;

        return Color.HSBtoRGB((float) hue, saturation, brightness);
    }
}