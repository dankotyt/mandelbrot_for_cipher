import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MandelbrotModel {
    private int MAX_ITER = 150; // Максимальное количество итераций для генерации фрактала
    private double ZOOM = 800; // Начальный уровень масштабирования
    private double offsetX = 0; // Смещение по оси X
    private double offsetY = 0; // Смещение по оси Y
    private BufferedImage image; // Хранение изображения

    public BufferedImage generateImage(int width, int height) {
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
        return image;
    }

    private void randomPositionOnPlenty() {
        Random random = new Random();
        MAX_ITER = 500 + (random.nextInt(91) * 10);
        offsetX = -0.9998 + (random.nextDouble() * (0.9998 - -0.9998));
        offsetY = -0.9998 + (random.nextDouble() * (0.9998 - -0.9998));
        ZOOM = 50000 + (random.nextInt(44) * 1000);
    }

    public boolean checkImageDiversity(BufferedImage image) {
        // Логика проверки разнообразия пикселей
        return true; // Упрощенная реализация
    }

    public Model_ImageMatrix getImageMatrix(BufferedImage image, int height, int width) {
        Model_ImageMatrix modelImageMatrix = new Model_ImageMatrix(image, height, width);
        modelImageMatrix.translatePixelsToNumbers(height, width);
        return modelImageMatrix;
    }
}