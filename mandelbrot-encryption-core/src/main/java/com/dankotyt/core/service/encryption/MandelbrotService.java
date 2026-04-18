package com.dankotyt.core.service.encryption;

import com.dankotyt.core.dto.MandelbrotParams;
import com.dankotyt.core.threading.MandelbrotThread;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author @dankotyt Danil Kotlyarov
 * Класс Mandelbrot представляет собой графический компонент Swing, который генерирует изображение множества Мандельброта.
 * Он позволяет пользователю сохранять сгенерированные изображения на рабочий стол и использует многопоточность для ускорения
 * генерации изображения и проверки его разнообразия.
 */
@Component
@RequiredArgsConstructor
public class MandelbrotService extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(MandelbrotService.class);

    @Getter @Setter
    private int targetWidth;
    @Getter @Setter
    private int targetHeight;

    private BufferedImage image;

    /**
     * Переопределяет метод paintComponent для отрисовки сгенерированного изображения множества Мандельброта.
     *
     * @param g Графический контекст для рисования.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            g.drawImage(image, 0, 0, null);
        }
    }

    /**
     * Генерирует параметры множества Мандельброта.
     */
    public MandelbrotParams generateParams(SecureRandom prng) {
        if (prng == null) {
            throw new IllegalArgumentException("PRNG cannot be null");
        }
        double zoom = 10_000 + prng.nextInt(701) * 140;
        double offsetX = -0.9998 + prng.nextDouble() * (0.45 + 0.9998);
        double offsetY = prng.nextBoolean()
                ? -0.7 + prng.nextDouble() * 0.6
                : 0.1 + prng.nextDouble() * 0.6;
        int maxIter = 250 + prng.nextInt(101) * 10;
        return new MandelbrotParams(zoom, offsetX, offsetY, maxIter);
    }

    /**
     * Генерирует изображение множества Мандельброта с заданными параметрами.
     * <p>
     * Использует многопоточную обработку для ускорения генерации. Разделяет изображение
     * на вертикальные полосы по количеству доступных процессоров.
     *
     * @param width ширина генерируемого изображения
     * @param height высота генерируемого изображения
     * @param ZOOM коэффициент масштабирования
     * @param offsetX смещение по оси X
     * @param offsetY смещение по оси Y
     * @param MAX_ITER максимальное количество итераций для алгоритма
     * @return сгенерированное изображение
     */
    public BufferedImage generateImage(int width, int height,
                                       double ZOOM, double offsetX, double offsetY, int MAX_ITER) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }
        if (ZOOM <= 0) {
            throw new IllegalArgumentException("Zoom must be positive: " + ZOOM);
        }
        if (MAX_ITER <= 0) {
            throw new IllegalArgumentException("MAX_ITER must be positive: " + MAX_ITER);
        }

        BufferedImage resultImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        try (ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            int processors = Runtime.getRuntime().availableProcessors();
            int chunkWidth = width / processors;

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < processors; i++) {
                int startX = i * chunkWidth;
                int w = (i == processors - 1) ? width - startX : chunkWidth;

                futures.add(executor.submit(new MandelbrotThread(
                        startX, 0, w, height,
                        ZOOM, MAX_ITER, offsetX, offsetY, resultImage
                )));
            }

            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Генерация прервана", e);
            return null;
        } catch (ExecutionException e) {
            logger.error("Ошибка в потоке вычислений", e);
            throw new RuntimeException("Ошибка генерации фрактала", e);
        }

        this.image = resultImage;
        repaint();
        return resultImage;
    }

    /**
     * Проверяет, является ли сгенерированное изображение фрактала пригодным для использования в шифровании.
     * <p>
     * Критерии проверки:
     * <ul>
     *   <li>Доля пикселей внутренней области (цвет 0x000040) не должна превышать 25%.</li>
     *   <li>Распределение цветовых тонов должно быть достаточно широким и равномерным согласно
     *       {@link #checkHueDistribution(BufferedImage, int, double)}.</li>
     * </ul>
     * </p>
     *
     * @param fractal изображение фрактала Мандельброта для проверки
     * @return true, если фрактал проходит все критерии качества; false в противном случае
     */
    public boolean isFractalValid(BufferedImage fractal) {
        int[] pixels = ((DataBufferInt) fractal.getRaster().getDataBuffer()).getData();
        int total = pixels.length;

        // Проверка на слишком много внутренних точек (цвет 0x000040)
        int darkBlueCount = 0;
        for (int p : pixels) {
            if ((p & 0x00FFFFFF) == 0x000040) darkBlueCount++;
        }
        if ((double) darkBlueCount / total > 0.25) return false;

        // Проверка распределения тонов
        return checkHueDistribution(fractal, 20, 0.25);
    }

    /**
     * Анализирует распределение цветовых тонов (hue) в изображении фрактала, исключая пиксели внутренней области.
     * <p>
     * Метод преобразует каждый пиксель (кроме имеющих фиксированный цвет внутренней области 0x000040)
     * в цветовое пространство HSB и извлекает компонент тона (hue). Диапазон тона [0,1) делится на
     * {@code BINS} равных интервалов (в текущей реализации 36). Для каждого пикселя отмечается
     * соответствующий интервал. После обработки всех пикселей проверяются два условия:
     * <ul>
     *   <li>Количество интервалов, в которые попал хотя бы один пиксель, должно быть не меньше {@code minBins}.</li>
     *   <li>Доля пикселей в любом одном интервале не должна превышать {@code maxBinRatio}.</li>
     * </ul>
     * </p>
     * <p>
     * Пиксели внутренней области (цвет 0x000040) исключаются из анализа, так как они не несут информации
     * о цветовом разнообразии фрактала и могут искажать гистограмму.
     * </p>
     *
     * @param img         изображение фрактала для анализа
     * @param minBins     минимальное допустимое количество интервалов тона, которые должны быть заняты
     *                    (должно быть в диапазоне от 1 до {@code BINS})
     * @param maxBinRatio максимально допустимая доля пикселей, попадающих в один интервал тона
     *                    (значение от 0 до 1, например 0.25 означает не более 25%)
     * @return true, если распределение тонов удовлетворяет обоим условиям; false в противном случае
     */
    private boolean checkHueDistribution(BufferedImage img, int minBins, double maxBinRatio) {
        int[] pixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        final int BINS = 36;
        int[] binCounts = new int[BINS];
        int totalConsidered = 0;

        for (int p : pixels) {
            int rgb = p & 0x00FFFFFF;
            // Пропускаем внутренние точки (настраиваем под ваш код)
            if (rgb == 0x000040 || rgb == 0x000000) continue;

            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            float[] hsv = new float[3];
            Color.RGBtoHSB(r, g, b, hsv);
            int bin = (int) (hsv[0] * BINS);
            if (bin >= 0 && bin < BINS) {
                binCounts[bin]++;
                totalConsidered++;
            }
        }

        if (totalConsidered == 0) {
            return false; // нет внешних точек с цветом
        }

        // Подсчет занятых бинов
        int nonEmpty = 0;
        int maxCount = 0;
        for (int count : binCounts) {
            if (count > 0) nonEmpty++;
            if (count > maxCount) maxCount = count;
        }

        double maxRatio = (double) maxCount / totalConsidered;

        return nonEmpty >= minBins && maxRatio <= maxBinRatio;
    }
}