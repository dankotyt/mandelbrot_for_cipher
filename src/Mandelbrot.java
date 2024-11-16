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
    private int x; // Координата X для генерации
    private int width; // Ширина изображения
    private int height; // Высота изображения
    private double ZOOM; // Уровень масштабирования
    private int MAX_ITER; // Максимальное количество итераций
    private double offsetX; // Смещение по оси X
    private double offsetY; // Смещение по оси Y
    private BufferedImage image; // Изображение для записи результатов
    private int segmentWidthSize; // Ширина сегмента
    private int segmentHeightSize; // Высота сегмента
    private int[] segmentIndices; // Индексы сегментов изображения
    private int numberSave = 0;

    private static final String PROJECT_PATH = "C:/Users/Danil/ideaProjects/mandelbrot_for_cipher/";

    /**
     * Конструктор класса Mandelbrot.
     * Инициализирует компонент и добавляет обработчик событий мыши для повторной генерации изображения.
     */
    public Mandelbrot() {
        this.width = 1024; // Устанавливаем начальные значения ширины и высоты
        this.height = 720;
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
        ZOOM = 70000 + (random.nextInt(44) * 1000);
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
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            for (int x = 0; x < width; x++) {
                executor.submit(new MandelbrotThread(x, width, height, ZOOM, MAX_ITER, offsetX, offsetY, image));
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

        showImageInNewWindow(); // Отображаем изображение в новом окне

        while (true) {
            int option = JOptionPane.showConfirmDialog(this, "Хотите сохранить изображение?", "Сохранить изображение", JOptionPane.YES_NO_CANCEL_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                saveImageToResources(image);
                saveParametersToBinaryFile(PROJECT_PATH + "resources/mandelbrot_params.bin");
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

        return (uniqueColors > 500 && percentage < 0.25);
    }

    public void showImageInNewWindow() {
        JFrame frame = new JFrame("Generated Mandelbrot Set");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(width, height);
        frame.add(this);
        frame.setVisible(true);
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
     * Сохраняет значения ZOOM, offsetX, offsetY, MAX_ITER, segmentWidthSize, segmentHeightSize и segmentIndices в двоичный файл.
     *
     * @param filePath Путь к файлу для сохранения параметров.
     */
    private void saveParametersToBinaryFile(String filePath) {
        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
            dos.writeDouble(getZOOM());
            dos.writeDouble(getOffsetX());
            dos.writeDouble(getOffsetY());
            dos.writeInt(getMAX_ITER());
            dos.writeInt(getSegmentWidthSize());
            dos.writeInt(getSegmentHeightSize());
            dos.writeInt(getSegmentIndices().length);
            for (int index : getSegmentIndices()) {
                dos.writeInt(index);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Загружает значения ZOOM, offsetX, offsetY, MAX_ITER, segmentWidthSize, segmentHeightSize и segmentIndices из двоичного файла.
     *
     * @param filePath Путь к файлу для загрузки параметров.
     * @return Массив значений [ZOOM, offsetX, offsetY, MAX_ITER, segmentWidthSize, segmentHeightSize, segmentIndices].
     */
    public Object[] loadParametersFromBinaryFile(String filePath) {
        Object[] params = new Object[7];
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(Paths.get(filePath)))) {
            params[0] = dis.readDouble();
            params[1] = dis.readDouble();
            params[2] = dis.readDouble();
            params[3] = dis.readInt();
            params[4] = dis.readInt();
            params[5] = dis.readInt();
            int segmentCount = dis.readInt();
            int[] segmentIndices = new int[segmentCount];
            for (int i = 0; i < segmentCount; i++) {
                segmentIndices[i] = dis.readInt();
            }
            params[6] = segmentIndices;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return params;
    }

    // Геттеры для переменных
    public double getZOOM() {
        return ZOOM;
    }

    public BufferedImage getImage() {
        return image;
    }

    public int getMAX_ITER() {
        return MAX_ITER;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public int getSegmentWidthSize() {
        return segmentWidthSize;
    }

    public int getSegmentHeightSize() {
        return segmentHeightSize;
    }

    public int[] getSegmentIndices() {
        return segmentIndices;
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