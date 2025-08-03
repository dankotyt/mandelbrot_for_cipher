package com.cipher.core.threading;

import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author @dankotyt Danil Kotlyarov
 *
 * Класс com.cipher.core.threading.MandelbrotThread реализует интерфейс Runnable и используется для генерации части изображения множества Мандельброта
 * в отдельном потоке.
 */
public class MandelbrotThread implements Runnable {

    private int startX; // Начальная координата X для генерации
    private int startY; // Начальная координата Y для генерации
    private int width; // Ширина изображения
    private int height; // Высота изображения
    private double ZOOM; // Уровень масштабирования
    private int MAX_ITER; // Максимальное количество итераций
    private double offsetX; // Смещение по оси X
    private double offsetY; // Смещение по оси Y
    private BufferedImage image; // Изображение для записи результатов
    private static final Logger logger = Logger.getLogger(MandelbrotThread.class.getName());

    /**
     * Конструктор класса com.cipher.core.threading.MandelbrotThread.
     *
     * @param startX Начальная координата X для генерации.
     * @param startY Начальная координата Y для генерации.
     * @param width Ширина изображения.
     * @param height Высота изображения.
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
     * Для каждой точки (x, y) на изображении вычисляется количество итераций,
     * необходимых для определения, принадлежит ли точка множеству Мандельброта.
     * Результат записывается в соответствующую точку изображения.
     */
    @Override
    public void run() {
        try {
            for (int y = startY; y < startY + height; y++) {
                for (int x = startX; x < startX + width; x++) {
                    double zx = 0, zy = 0;
                    double cX = (x - image.getWidth() / 1.75) / ZOOM + offsetX;
                    double cY = (y - image.getHeight() / 1.75) / ZOOM + offsetY;
                    int i = MAX_ITER;
                    while (zx * zx + zy * zy < 4 && i > 0) {
                        double tmp = zx * zx - zy * zy + cX;
                        zy = 2.0 * zx * zy + cY;
                        zx = tmp;
                        i--;
                    }
                    int color = i | (i << 10) | (i << 14);
                    image.setRGB(x, y, i > 0 ? color : 0);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, e.toString());
        }
    }
}