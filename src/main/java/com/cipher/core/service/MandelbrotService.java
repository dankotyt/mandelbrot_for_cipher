package com.cipher.core.service;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.threading.MandelbrotThread;
import com.cipher.core.utils.BinaryFile;
import com.cipher.core.utils.DeterministicRandomGenerator;
import com.cipher.view.javafx.JavaFXImpl;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

import javafx.application.Platform;
import javafx.scene.control.Alert;
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

    private final DeterministicRandomGenerator drbg;
    private final BinaryFile binaryFile;

    private int startMandelbrotWidth;
    private int startMandelbrotHeight;
    private double ZOOM;
    private int MAX_ITER;
    private double offsetX;
    private double offsetY;
    private BufferedImage image;

    private String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    public MandelbrotService createWithSize(int width, int height) {
        MandelbrotService service = new MandelbrotService(this.drbg, this.binaryFile);
        service.startMandelbrotWidth = width;
        service.startMandelbrotHeight = height;
        service.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) || SwingUtilities.isRightMouseButton(e)) {
                    service.generateImage();
                }
            }
        });
        return service;
    }

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
     * Генерирует случайные значения для параметров MAX_ITER, offsetX, offsetY и ZOOM.
     */
    public void randomPositionOnPlenty() {
        MAX_ITER = 500 + (drbg.nextInt(91) * 10); // 91 для диапазона от 0 до 90, чтобы получить 300, 310 и до 1200
        offsetX = -0.9998 + (drbg.nextDouble() * (0.9998 - -0.9998));
        offsetY = -0.9998 + (drbg.nextDouble() * (0.9998 - -0.9998));
        ZOOM = 100000 + (drbg.nextInt(44) * 1000);
        repaint();
    }

    /**
     * Генерирует изображение множества Мандельброта и проверяет его разнообразие.
     * <p>
     * Алгоритм:
     * <ol>
     *   <li>Генерирует изображение с использованием многопоточной обработки</li>
     *   <li>Проверяет качество изображения (достаточное разнообразие цветов)</li>
     *   <li>Повторяет процесс, если изображение не удовлетворяет критериям</li>
     *   <li>Сохраняет параметры генерации во временный файл</li>
     * </ol>
     *
     * @return сгенерированное изображение или null, если поток был прерван
     * @see #checkImageDiversity(BufferedImage)
     */
    public BufferedImage generateImage() {
        boolean validImage = false;
        int attempt = 0;
        MandelbrotParams currentParams;
        BufferedImage resultImage = null;

        while (!validImage && !Thread.currentThread().isInterrupted()) {
            attempt++;
            randomPositionOnPlenty(); // Обновляем параметры

            try (ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
                resultImage = new BufferedImage(startMandelbrotWidth, startMandelbrotHeight, BufferedImage.TYPE_INT_RGB);
                int processors = Runtime.getRuntime().availableProcessors();
                int chunkWidth = startMandelbrotWidth / processors;

                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < processors; i++) {
                    int startX = i * chunkWidth;
                    int width = (i == processors - 1) ? startMandelbrotWidth - startX : chunkWidth;

                    futures.add(executor.submit(new MandelbrotThread(
                            startX, 0, width, startMandelbrotHeight,
                            ZOOM, MAX_ITER, offsetX, offsetY, resultImage
                    )));
                }

                // Ожидаем завершения всех задач
                for (Future<?> future : futures) {
                    future.get();
                }

                currentParams = new MandelbrotParams(
                        startMandelbrotWidth,
                        startMandelbrotHeight,
                        ZOOM,
                        offsetX,
                        offsetY,
                        MAX_ITER
                );

                validImage = checkImageDiversity(resultImage);

                if (!validImage) {
                    JavaFXImpl.logToConsole("Попытка №" + attempt + ". Подождите, пожалуйста...");
                } else {
                    JavaFXImpl.logToConsole("Изображение успешно сгенерировано после " + attempt + " попыток.");
                    binaryFile.saveMandelbrotParamsToBinaryFile(
                            getTempPath() + "mandelbrot_params.bin",
                            currentParams
                    );
                    saveImageToTemp(resultImage);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Генерация прервана", e);
                return null;
            } catch (ExecutionException e) {
                logger.error("Ошибка в потоке вычислений (попытка " + attempt + ")", e);
            }
        }

        repaint();
        return resultImage;
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
        this.startMandelbrotWidth = startMandelbrotWidth;
        this.startMandelbrotHeight = startMandelbrotHeight;
        this.ZOOM = ZOOM;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.MAX_ITER = MAX_ITER;

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
                future.get(); // Дожидаемся завершения каждого потока
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
//    public boolean checkImageDiversity(BufferedImage image) {
//        Map<Integer, Integer> colorCount = new HashMap<>();
//        int totalPixels = image.getWidth() * image.getHeight();
//
//        for (int x = 0; x < image.getWidth(); x++) {
//            for (int y = 0; y < image.getHeight(); y++) {
//                int color = image.getRGB(x, y);
//                colorCount.put(color, colorCount.getOrDefault(color, 0) + 1);
//            }
//        }
//
//        int uniqueColors = colorCount.size();
//        int maxCount = colorCount.values().stream().max(Integer::compare).orElse(0);
//        double percentage = (double) maxCount / totalPixels;
//
//        if (isImageBlackPercentageAboveThreshold(image, 0.05)) {
//            return false;
//        }
//
//        return (uniqueColors > 250 && percentage < 0.25);
//    }
    private boolean checkImageDiversity(BufferedImage image) {
        // Быстрая проверка на черноту
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
//    public static boolean isImageBlackPercentageAboveThreshold(BufferedImage image, double threshold) {
//        int width = image.getWidth();
//        int height = image.getHeight();
//        int blackPixelCount = 0;
//
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x++) {
//                int pixel = image.getRGB(x, y);
//                if (isBlackPixel(pixel)) {
//                    blackPixelCount++;
//                }
//            }
//        }
//
//        double percentageBlack = (double) blackPixelCount / (width * height);
//
//        return percentageBlack > threshold;
//    }
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

    /**
     * Сохраняет изображение в папку resources в корне проекта.
     *
     * @param image Изображение для сохранения.
     */
    public void saveImageToTemp(BufferedImage image) {
        String savePath = getTempPath();
        File file = new File(savePath + "mandelbrot.png");

        try {
            ImageIO.write(image, "png", file);
        } catch (IOException e) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Ошибка сохранения");
                alert.setHeaderText(null);
                alert.setContentText("Ошибка при сохранении изображения: " + e.getMessage());
                alert.showAndWait();
            });
        }
    }

    /**
     * Метод для генерации изображения на основе параметров, загруженных из бинарного файла.
     *
     * @param imagePath Путь к бинарному файлу с параметрами.
     * @return Сгенерированное изображение.
     */
    public BufferedImage generateAfterGetParams(String imagePath) {
        try {
            // Загрузка параметров
            MandelbrotParams params = binaryFile.loadMandelbrotParamsFromBinaryFile(imagePath);

            // Проверка параметров
            if (params.startMandelbrotWidth() <= 0 || params.startMandelbrotHeight() <= 0) {
                logger.error("Некорректные размеры: width={}, height={}",
                        params.startMandelbrotWidth(),
                        params.startMandelbrotHeight());
                return null;
            }

            // Генерация изображения
            MandelbrotService mandelbrotService = createWithSize(
                    params.startMandelbrotWidth(), params.startMandelbrotHeight());

            BufferedImage generatedImage = mandelbrotService.generateImage(
                    params.startMandelbrotWidth(),
                    params.startMandelbrotHeight(),
                    params.zoom(),
                    params.offsetX(),
                    params.offsetY(),
                    params.maxIter()
            );

            // Сохранение изображения
            saveImageToTemp(generatedImage);
            return generatedImage;
        } catch (IOException e) {
            logger.error("Ошибка при загрузке/генерации из файла {}", imagePath, e);
            return null;
        }
    }
}