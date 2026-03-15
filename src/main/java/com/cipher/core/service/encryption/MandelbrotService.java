package com.cipher.core.service.encryption;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.encryption.HKDF;
import com.cipher.core.encryption.ImageSegmentShuffler;
import com.cipher.core.threading.MandelbrotThread;

import javax.swing.*;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
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
        double zoom = 1000 + (prng.nextInt(1000) * 100);
        double offsetX = -0.9998 + prng.nextDouble() * (0.45 - (-0.9998));
        double offsetY;
        if (prng.nextBoolean()) {
            offsetY = -0.7 + prng.nextDouble() * 0.6; // интервал [-0.7, -0.1]
        } else {
            offsetY = 0.1 + prng.nextDouble() * 0.6;  // интервал [0.1, 0.7]
        }
        int maxIter = 300;
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
     * Проверяет, удовлетворяет ли изображение критериям разнообразия.
     * <p>
     * Критерии могут включать:
     * <ul>
     *   <li>Достаточное количество цветов</li>
     *   <li>Баланс между тёмными и светлыми областями</li>
     *   <li>Отсутствие слишком больших монотонных областей</li>
     * </ul>
     *
     * @param image изображение для проверки
     * @return true если изображение удовлетворяет критериям, иначе false
     */
    public boolean checkImageDiversity(BufferedImage image) {
        if (isImageBlackPercentageAboveThreshold(image, 0.05)) { //todo попробовать меньше значение
            return false;
        }

        // Оптимизированная проверка уникальности цветов
        Set<Integer> uniqueColors = new HashSet<>();
        int[] pixelBuffer = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        for (int j : pixelBuffer) {
            int color = j & 0x00FFFFFF;
            uniqueColors.add(color);

            // Быстрый выход если уже много уникальных цветов
            if (uniqueColors.size() > 1000) {
                break;
            }
        }

        // Проверка доминирующего цвета
        return uniqueColors.size() >= 250;
    }

    /**
     * Проверяет, превышает ли процент чёрных пикселей в изображении заданный порог.
     *
     * @param image изображение для анализа
     * @param threshold пороговое значение (от 0.0 до 1.0)
     * @return true, если процент чёрных пикселей превышает порог, иначе false
     */
    private static boolean isImageBlackPercentageAboveThreshold(BufferedImage image, double threshold) {
        int width = image.getWidth();
        int height = image.getHeight();
        int totalPixels = width * height;
        int blackPixelCount = 0;
        int maxBlackPixels = (int) (totalPixels * threshold) + 1;

        int[] pixelBuffer = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        for (int pixel : pixelBuffer) {
            if (isBlackPixel(pixel)) {
                blackPixelCount++;
                if (blackPixelCount >= maxBlackPixels) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Определяет, является ли пиксель чёрным (RGB = 0,0,0).
     *
     * @param pixel значение пикселя в формате RGB
     * @return true если пиксель полностью чёрный, иначе false
     */
    private static boolean isBlackPixel(int pixel) {
        int red = (pixel >> 16) & 0xFF;
        int green = (pixel >> 8) & 0xFF;
        int blue = pixel & 0xFF;

        return red == 0 && green == 0 && blue == 0;
    }
}