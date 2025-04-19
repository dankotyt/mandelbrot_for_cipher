package Model;

import View.JavaFX;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Класс Model.Mandelbrot представляет собой графический компонент Swing, который генерирует изображение множества Мандельброта.
 * Он позволяет пользователю сохранять сгенерированные изображения на рабочий стол и использует многопоточность для ускорения
 * генерации изображения и проверки его разнообразия.
 */
public class Mandelbrot extends JPanel {

    private int startMandelbrotWidth; // Ширина изображения
    private int startMandelbrotHeight; // Высота изображения
    private double ZOOM; // Уровень масштабирования
    private int MAX_ITER; // Максимальное количество итераций
    private double offsetX; // Смещение по оси X
    private double offsetY; // Смещение по оси Y
    private BufferedImage image; // Изображение для записи результатов
    private ExecutorService executor;

    private static final String RESOURCES_PATH = "resources" + File.separator;

    private String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private String getResourcesPath() {
        return getProjectRootPath() + RESOURCES_PATH;
    }

    private String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    /**
     * Конструктор класса Model.Mandelbrot.
     * Инициализирует компонент и добавляет обработчик событий мыши для повторной генерации изображения.
     */
    public Mandelbrot() {
        this.startMandelbrotWidth = 1024; // Устанавливаем начальные значения ширины и высоты
        this.startMandelbrotHeight = 720;
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) || SwingUtilities.isRightMouseButton(e)) { //Повторная генерация
                    generateImage();
                }
            }
        });
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
            g.drawImage(image, 0, 0, null); // Рисуем сохраненное изображение
        }
    }

    /**
     * Генерирует случайные значения для параметров MAX_ITER, offsetX, offsetY и ZOOM.
     */
    public void randomPositionOnPlenty() {
        Random random = new Random();
        MAX_ITER = 500 + (random.nextInt(91) * 10); // 91 для диапазона от 0 до 90, чтобы получить 300, 310 и до 1200
        offsetX = -0.9998 + (random.nextDouble() * (0.9998 - -0.9998));
        offsetY = -0.9998 + (random.nextDouble() * (0.9998 - -0.9998));
        ZOOM = 100000 + (random.nextInt(44) * 1000);
        repaint();
    }

    /**
     * Генерирует изображение множества Мандельброта и проверяет его разнообразие.
     * Если изображение удовлетворяет условиям разнообразия, оно отображается и предлагается пользователю сохранить его.
     * Если пользователь отказывается, генерируется новое изображение.
     */
    public BufferedImage generateImage() {
        boolean validImage = false;
        int attempt = 0;

        while (!validImage && !Thread.currentThread().isInterrupted()) { // Проверяем флаг прерывания
            attempt++;
            randomPositionOnPlenty();
            image = new BufferedImage(startMandelbrotWidth, startMandelbrotHeight, BufferedImage.TYPE_INT_RGB);
            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            int chunkWidth = startMandelbrotWidth / Runtime.getRuntime().availableProcessors();
            int chunkHeight = startMandelbrotHeight;

            for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
                int startX = i * chunkWidth;
                int startY = 0;
                int width = (i == Runtime.getRuntime().availableProcessors() - 1) ? startMandelbrotWidth - startX : chunkWidth;
                int height = chunkHeight;

                executor.submit(new MandelbrotThread(startX, startY, width, height, ZOOM, MAX_ITER, offsetX, offsetY, image));
            }
            executor.shutdown();
            try {
                if (!executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
                    executor.shutdownNow(); // Принудительно завершаем выполнение, если тайм-аут истек
                }
            } catch (InterruptedException e) {
                executor.shutdownNow(); // Принудительно завершаем выполнение при прерывании
                Thread.currentThread().interrupt(); // Восстанавливаем флаг прерывания
                return null; // Возвращаем null, так как задача была отменена
            }

            validImage = checkImageDiversity(image);
            if (!validImage) {
                JavaFX.logToConsole("Попытка №" + attempt + ". Подождите, пожалуйста...");
            } else {
                JavaFX.logToConsole("Изображение успешно сгенерировано после " + attempt + " попыток.");
            }
        }
        repaint();
        BinaryFile binaryFile = new BinaryFile();
        binaryFile.saveMandelbrotParamsToBinaryFile(getTempPath() + "mandelbrot_params.bin", startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER);
        saveImageToTemp(image);
        return image;
    }

    public BufferedImage generateImage(int startMandelbrotWidth, int startMandelbrotHeight, double ZOOM, double offsetX, double offsetY, int MAX_ITER) {
        this.startMandelbrotWidth = startMandelbrotWidth;
        this.startMandelbrotHeight = startMandelbrotHeight;
        this.ZOOM = ZOOM;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.MAX_ITER = MAX_ITER;

        image = new BufferedImage(startMandelbrotWidth, startMandelbrotHeight, BufferedImage.TYPE_INT_RGB);
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        int chunkWidth = startMandelbrotWidth / Runtime.getRuntime().availableProcessors();
        int chunkHeight = startMandelbrotHeight;

        for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
            int startX = i * chunkWidth;
            int startY = 0;
            int width = (i == Runtime.getRuntime().availableProcessors() - 1) ? startMandelbrotWidth - startX : chunkWidth;
            int height = chunkHeight;

            executor.submit(new MandelbrotThread(startX, startY, width, height, ZOOM, MAX_ITER, offsetX, offsetY, image));
        }
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        repaint();
        return image;
    }

    public BufferedImage getImage() {
        return image;
    }

    public BufferedImage getImage(String imagePath) {
        return image;
    }

    /**
     * Проверяет разнообразие пикселей в изображении.
     *
     * @param image Изображение для проверки.
     * @return true, если изображение удовлетворяет условиям разнообразия, иначе false.
     */
    public boolean checkImageDiversity(BufferedImage image) {
        Map<Integer, Integer> colorCount = new HashMap<>();
        int totalPixels = image.getWidth() * image.getHeight();

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int color = image.getRGB(x, y);
                colorCount.put(color, colorCount.getOrDefault(color, 0) + 1);
            }
        }

        int uniqueColors = colorCount.size();
        int maxCount = colorCount.values().stream().max(Integer::compare).orElse(0);
        double percentage = (double) maxCount / totalPixels;

        if (isImageBlackPercentageAboveThreshold(image, 0.05)) {
            return false;
        }

        return (uniqueColors > 250 && percentage < 0.25);
    }

    public static boolean isImageBlackPercentageAboveThreshold(BufferedImage image, double threshold) {
        int width = image.getWidth();
        int height = image.getHeight();
        int blackPixelCount = 0;

        // Проход по всем пикселям изображения
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                if (isBlackPixel(pixel)) {
                    blackPixelCount++;
                }
            }
        }

        // Вычисление процента черных пикселей
        double percentageBlack = (double) blackPixelCount / (width * height);

        // Сравнение с порогом
        return percentageBlack > threshold;
    }

    public static boolean isBlackPixel(int pixel) {
        // Получение компонентов цвета (красный, зеленый, синий)
        int red = (pixel >> 16) & 0xFF;
        int green = (pixel >> 8) & 0xFF;
        int blue = pixel & 0xFF;

        // Проверка, является ли пиксель черным
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
            JOptionPane.showMessageDialog(this, "Ошибка при сохранении изображения: " + e.getMessage());
        }
    }

    /**
     * Метод для генерации изображения на основе параметров, загруженных из бинарного файла.
     *
     * @param imagePath Путь к бинарному файлу с параметрами.
     * @return Сгенерированное изображение.
     */
    public BufferedImage generateAfterGetParams(String imagePath) {
        // Загрузка параметров из бинарного файла
        Object[] mandelbrotParams = BinaryFile.loadKeyDecoderFromBinaryFile(imagePath);
        if (mandelbrotParams == null || mandelbrotParams.length < 6) {
            System.err.println("Ошибка: не удалось загрузить параметры из бинарного файла.");
            return null;
        }

        // Извлечение параметров
        int startMandelbrotWidth = (int) mandelbrotParams[0];
        int startMandelbrotHeight = (int) mandelbrotParams[1];
        double ZOOM = (double) mandelbrotParams[2];
        double offsetX = (double) mandelbrotParams[3];
        double offsetY = (double) mandelbrotParams[4];
        int MAX_ITER = (int) mandelbrotParams[5];

        // Проверка корректности параметров
        if (startMandelbrotWidth <= 0 || startMandelbrotHeight <= 0) {
            System.err.println("Ошибка: ширина или высота некорректны: width=" + startMandelbrotWidth + ", height=" + startMandelbrotHeight);
            return null;
        }

        // Генерация изображения с использованием параметров
        BufferedImage mandelbrotImage = generateImage(startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER);

        // Сохранение изображения в папку temp
        saveImageToTemp(mandelbrotImage);

        return mandelbrotImage;
    }
}