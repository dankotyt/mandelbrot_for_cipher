package Model;

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
    private int numberSave = 0;

    private static final String PROJECT_PATH = "C:/Users/Danil/ideaProjects/mandelbrot_for_cipher/";

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

    public void createOrUseMandelbrot() {
        // Запрос пользователю о выборе области или всего изображения
        int option = JOptionPane.showOptionDialog(null, "Выберите действие:", "Создание картинки с множеством Мандельброта",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new String[]{"Создать картинку", "Выбрать уже существующую"}, "Выбрать уже существующую");

        if (option == JOptionPane.YES_OPTION) {
            // Пользователь выбрал "Создать картинку"
            generateImage();
        } else if (option == JOptionPane.NO_OPTION) {
            // Пользователь выбрал "Выбрать уже существующую"

        }
    }

    /**
     * Генерирует изображение множества Мандельброта и проверяет его разнообразие.
     * Если изображение удовлетворяет условиям разнообразия, оно отображается и предлагается пользователю сохранить его.
     * Если пользователь отказывается, генерируется новое изображение.
     */
    public void generateImage() {
        boolean validImage = false;
        int attempt = 0;

        while (!validImage) {
            attempt++;
            randomPositionOnPlenty();
            image = new BufferedImage(startMandelbrotWidth, startMandelbrotHeight, BufferedImage.TYPE_INT_RGB);
            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            for (int x = 0; x < startMandelbrotWidth; x++) {
                executor.submit(new MandelbrotThread(x, startMandelbrotWidth, startMandelbrotHeight, ZOOM, MAX_ITER, offsetX, offsetY, image));
            }

            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            validImage = checkImageDiversity(image);
            if (!validImage) {
                System.out.println("Попытка №" + attempt + ". Изображение не удовлетворяет условиям, повторная рандомизация...");
            }
        }
        repaint();

        while (true) {
            int option = JOptionPane.showConfirmDialog(this, "Хотите сохранить изображение?", "Сохранить изображение", JOptionPane.YES_NO_CANCEL_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                saveImageToResources(image);
                BinaryFile mandelbrotParams = new BinaryFile();
                mandelbrotParams.saveMandelbrotParamsToBinaryFile(PROJECT_PATH + "resources/mandelbrot_params.bin", startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER);
                break;
            } else if (option == JOptionPane.NO_OPTION) {
                generateImage(); // Пересоздание изображения
                break;
            } else if (option == JOptionPane.CANCEL_OPTION) {
                // Возвращаемся в меню
                break;
            }
        }
    }

    /**
     * Проверяет разнообразие пикселей в изображении.
     *
     * @param image Изображение для проверки.
     * @return true, если изображение удовлетворяет условиям разнообразия, иначе false.
     */
    private boolean checkImageDiversity(BufferedImage image) {
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
    private void saveImageToResources(BufferedImage image) {
        String savePath = PROJECT_PATH + "/resources";
        numberSave++;
        File file = new File(savePath + File.separator + "mandelbrot" + numberSave + ".png");

        try {
            ImageIO.write(image, "png", file);
            JOptionPane.showMessageDialog(this, "Изображение сохранено в папку resources: " + file.getAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Ошибка при сохранении изображения: " + e.getMessage());
        }
    }
}