package com.cipher.core.service.encryption;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.encryption.HKDF;
import com.cipher.core.encryption.ImageSegmentShuffler;
import com.cipher.core.threading.MandelbrotThread;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

    private final ImageSegmentShuffler imageSegmentShuffler;

    private SecureRandom paramsPrng;
    @Getter
    private int attemptCount;
    @Getter
    private byte[] sessionSalt;
    private int targetWidth;
    private int targetHeight;
    private BufferedImage image;
    @Getter
    private MandelbrotParams currentParams;

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
     * Устанавливает целевой размер изображения для генерации фрактала.
     * Этот размер будет использоваться при вызове generateImage() без параметров.
     *
     * @param width  целевая ширина изображения
     * @param height целевая высота изображения
     */
    public void setTargetSize(int width, int height) {
        this.targetWidth = width;
        this.targetHeight = height;
    }

    /**
     * Подготавливает сессию для генерации фракталов на основе общего секрета.
     * Генерирует случайную соль, создаёт ключи для генерации параметров фрактала
     * и инициализирует генератор псевдослучайных чисел.
     *
     * @param sharedSecret общий секрет, полученный в результате DH обмена
     * @throws Exception если возникает ошибка при инициализации криптографических компонентов
     */
    public void prepareSession(byte[] sharedSecret) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        this.sessionSalt = salt;

        byte[] prk = HKDF.extract(salt, sharedSecret);
        byte[] keyFractalParams = HKDF.expand(prk, "fractal-params".getBytes(StandardCharsets.UTF_8), 32);
        byte[] keySegmentation = HKDF.expand(prk, "segmentation".getBytes(StandardCharsets.UTF_8), 32);

        this.paramsPrng = SecureRandom.getInstance("SHA1PRNG");
        this.paramsPrng.setSeed(keyFractalParams);

        imageSegmentShuffler.initialize(keySegmentation);

        this.attemptCount = 0;
    }

    /**
     * Генерирует параметры множества Мандельброта с использованием предоставленного
     * генератора псевдослучайных чисел.
     * <p>
     * Параметры включают:
     * <ul>
     *   <li>zoom - коэффициент масштабирования от 1000 до 101000</li>
     *   <li>offsetX - смещение по оси X в диапазоне [-0.9998, 0.45)</li>
     *   <li>offsetY - смещение по оси Y либо в [-0.7, -0.1], либо в [0.1, 0.7]</li>
     *   <li>maxIter - максимальное количество итераций (всегда 300)</li>
     * </ul>
     *
     * @param prng генератор псевдослучайных чисел для детерминированной генерации
     * @return объект MandelbrotParams со сгенерированными параметрами
     */
    public MandelbrotParams generateParams(SecureRandom prng) {
        double zoom = 10_000 + (prng.nextInt(701) * 140); // можно уменьшить - вырастет скорость
        double offsetX = -0.9998 + prng.nextDouble() * (0.45 + 0.9998);
        double offsetY;
        if (prng.nextBoolean()) {
            offsetY = -0.7 + prng.nextDouble() * 0.6; // интервал [-0.7, -0.1]
        } else {
            offsetY = 0.1 + prng.nextDouble() * 0.6;  // интервал [0.1, 0.7]
        }
        int maxIter = 250;
        return new MandelbrotParams(zoom, offsetX, offsetY, maxIter);
    }

    /**
     * Генерирует изображение множества Мандельброта с использованием текущих параметров.
     * Увеличивает счётчик попыток (attemptCount) и сохраняет сгенерированные параметры
     * как currentParams.
     *
     * @return сгенерированное изображение фрактала
     * @throws IllegalStateException если сессия не была подготовлена вызовом prepareSession()
     */
    public BufferedImage generateImage() {
        if (paramsPrng == null) {
            throw new IllegalStateException("Session not prepared. Call prepareSession first.");
        }
        MandelbrotParams params = generateParams(paramsPrng);
        this.currentParams = params;
        this.attemptCount++;
        return generateImage(targetWidth, targetHeight,
                params.zoom(), params.offsetX(), params.offsetY(), params.maxIter());
    }

    /**
     * Генерирует изображение множества Мандельброта с заданными параметрами.
     * <p>
     * Использует многопоточную обработку для ускорения генерации. Разделяет изображение
     * на вертикальные полосы по количеству доступных процессоров.
     *
     * @param startMandelbrotWidth ширина генерируемого изображения
     * @param startMandelbrotHeight высота генерируемого изображения
     * @param ZOOM коэффициент масштабирования
     * @param offsetX смещение по оси X
     * @param offsetY смещение по оси Y
     * @param MAX_ITER максимальное количество итераций для алгоритма
     * @return сгенерированное изображение
     */
    public BufferedImage generateImage(int startMandelbrotWidth, int startMandelbrotHeight,
                                       double ZOOM, double offsetX, double offsetY, int MAX_ITER) {

        image = new BufferedImage(startMandelbrotWidth, startMandelbrotHeight, BufferedImage.TYPE_INT_RGB);

        try (ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            int processors = Runtime.getRuntime().availableProcessors();
            int chunkWidth = startMandelbrotWidth / processors;

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < processors; i++) {
                int startX = i * chunkWidth;
                int width = (i == processors - 1) ? startMandelbrotWidth - startX : chunkWidth;

                futures.add(executor.submit(new MandelbrotThread(
                        startX, 0, width, startMandelbrotHeight,
                        ZOOM, MAX_ITER, offsetX, offsetY, image
                )));
            }

            // Ожидаем завершения всех задач
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

        repaint();
        return image;
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