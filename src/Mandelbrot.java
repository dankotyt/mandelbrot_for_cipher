import javax.swing.*;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Graphics;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * @author dankotyt
 *
 * Класс Mandelbrot представляет собой графический компонент Swing, который генерирует изображение множества Мандельброта
 * и позволяет пользователю сохранять сгенерированные изображения на рабочий стол. Класс использует многопоточность для
 * ускорения генерации изображения и проверки его разнообразия.
 */
public class Mandelbrot extends JPanel {
    private int MAX_ITER = 150; // Максимальное количество итераций для генерации фрактала
    private double ZOOM = 800; // Начальный уровень масштабирования
    private double offsetX = 0; // Смещение по оси X
    private double offsetY = 0; // Смещение по оси Y
    private BufferedImage image; // Хранение изображения
    private int numberSave = 0; // Номер сохраняемого изображения

    /**
     * Конструктор класса Mandelbrot.
     * Добавляет обработчик событий мыши для повторной генерации изображения.
     */
    public Mandelbrot() {
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

        // Рандомизация offsetX и offsetY от -0.9998 до 0.9998
        offsetX = -0.9998 + (random.nextDouble() * (0.9998 - -0.9998));
        offsetY = -0.9998 + (random.nextDouble() * (0.9998 - -0.9998));
        ZOOM = 50000 + (random.nextInt(44) * 1000); /*Чем больше начальный зум, тем дольше будет генерироваться картинка
                                                        однако информация зашифрована будет лучше из-за большого кол-ва
                                                        элементов фрактала */
        repaint();
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
            // Генерация изображения
            randomPositionOnPlenty();
            image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
            //используем пул потоков для выполнения генерации изображения
            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            for (int x = 0; x < getWidth(); x++) {
                executor.submit(new MandelbrotThread(x, getWidth(), getHeight(), ZOOM, MAX_ITER, offsetX, offsetY, image));
            }

            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Проверка на разнообразие пикселей
            validImage = checkImageDiversity(image);
            if (!validImage) {
                System.out.println("Попытка №" + attempt + ". Изображение не удовлетворяет условиям, повторная рандомизация...");
            }
        }
        repaint();

        // Запрашиваем пользователя о сохранении изображения
        int option = JOptionPane.showConfirmDialog(this, "Хотите сохранить изображение на рабочий стол?", "Сохранить изображение", JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
            saveImageToDesktop(image);
        } else {
            generateImage(); // Генерируем новое изображение, если пользователь отказался
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

        return (uniqueColors > 500 && percentage < 0.25);
    }

    /**
     * Сохраняет изображение на рабочий стол пользователя.
     *
     * @param image Изображение для сохранения.
     */
    private void saveImageToDesktop(BufferedImage image) {
        String desktopPath = "resources";
        numberSave++;
        File file = new File(desktopPath + File.separator + "mandelbrot" + numberSave +".png");

        try {
            ImageIO.write(image, "png", file);
            JOptionPane.showMessageDialog(this, "Изображение сохранено на рабочий стол: " + file.getAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Ошибка при сохранении изображения: " + e.getMessage());
        }
    }
}