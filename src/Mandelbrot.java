import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Класс Mandelbrot представляет собой графический компонент Swing, который генерирует изображение множества Мандельброта.
 * Он позволяет пользователю сохранять сгенерированные изображения на рабочий стол и использует многопоточность для ускорения
 * генерации изображения и проверки его разнообразия.
 */
public class Mandelbrot extends JPanel {
    private int MAX_ITER = 150; // Максимальное количество итераций для генерации фрактала
    private double ZOOM = 800; // Начальный уровень масштабирования
    private double offsetX = 0; // Смещение по оси X
    private double offsetY = 0; // Смещение по оси Y
    private BufferedImage image; // Хранение изображения
    private int numberSave = 0; // Номер сохраняемого изображения

    private static final String PROJECT_PATH = "C:/Users/r10021998/ideaProjects/mandelbrot_for_cipher-master/";

    /**
     * Конструктор класса Mandelbrot.
     * Инициализирует компонент и добавляет обработчик событий мыши для повторной генерации изображения.
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
        offsetX = -0.9998 + (random.nextDouble() * (0.9998 - -0.9998));
        offsetY = -0.9998 + (random.nextDouble() * (0.9998 - -0.9998));
        ZOOM = 50000 + (random.nextInt(44) * 1000);
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
            randomPositionOnPlenty();
            image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
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

            validImage = checkImageDiversity(image);
            if (!validImage) {
                System.out.println("Попытка №" + attempt + ". Изображение не удовлетворяет условиям, повторная рандомизация...");
            }
        }
        repaint();

        while (true) {
            int option = JOptionPane.showConfirmDialog(this, "Хотите сохранить изображение?", "Сохранить изображение", JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                saveImageToResources(image);
                saveParametersToBinaryFile(PROJECT_PATH + "resources/mandelbrot_params.bin");
                break;
            } else if (option == JOptionPane.NO_OPTION) {
                generateImage(); // Пересоздание изображения
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

        return (uniqueColors > 500 && percentage < 0.25);
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

    /**
     * Сохраняет значения ZOOM, offsetX, offsetY и MAX_ITER в двоичный файл.
     *
     * @param filePath Путь к файлу для сохранения параметров.
     */
    private void saveParametersToBinaryFile(String filePath) {
        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
            dos.writeDouble(ZOOM);
            dos.writeDouble(offsetX);
            dos.writeDouble(offsetY);
            dos.writeInt(MAX_ITER);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Загружает значения ZOOM, offsetX, offsetY и MAX_ITER из двоичного файла.
     *
     * @param filePath Путь к файлу для загрузки параметров.
     * @return Массив значений [ZOOM, offsetX, offsetY, MAX_ITER].
     */
    public double[] loadParametersFromBinaryFile(String filePath) {
        double[] params = new double[4];
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(Paths.get(filePath)))) {
            params[0] = dis.readDouble();
            params[1] = dis.readDouble();
            params[2] = dis.readDouble();
            params[3] = dis.readInt();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return params;
    }

    /**
     * Основной метод для запуска приложения.
     *
     * @param args Аргументы командной строки.
     */
    public static void main(String[] args) {
        JFrame frame = new JFrame("Mandelbrot Set");
        Mandelbrot mandelbrot = new Mandelbrot();
        frame.add(mandelbrot);
        frame.setSize(1024, 720);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        mandelbrot.generateImage();
    }
}
