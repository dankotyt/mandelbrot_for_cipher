package Model;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.Graphics2D;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;

public class ImageSegmentShuffler {
    private int[] pixels;

    public ImageSegmentShuffler(BufferedImage my_image) {
        DataBuffer dataBuffer = my_image.getRaster().getDataBuffer();
        if (dataBuffer instanceof DataBufferInt) {
            this.pixels = ((DataBufferInt) dataBuffer).getData();
        } else {
            // Преобразование изображения в тип BufferedImage.TYPE_INT_RGB
            BufferedImage convertedImage = new BufferedImage(my_image.getWidth(), my_image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = convertedImage.createGraphics();
            g2d.drawImage(my_image, 0, 0, null);
            g2d.dispose();
            this.pixels = ((DataBufferInt) convertedImage.getRaster().getDataBuffer()).getData();
        }
    }

    public static BufferedImage resizeImage(BufferedImage image, int width, int height) {
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(image, 0, 0, width, height, null);
        g2d.dispose();
        return resizedImage;
    }

    public Pair<BufferedImage, Map<Integer, Integer>> shuffleSegments(BufferedImage image, int segmentWidthSize, int segmentHeightSize) {
        int width = image.getWidth();
        int height = image.getHeight();
        int segmentWidth = width / segmentWidthSize;
        int segmentHeight = height / segmentHeightSize;

        // Проверка, что размеры изображения делятся на количество сегментов без остатка
        if (width % segmentWidthSize != 0 || height % segmentHeightSize != 0) {
            throw new IllegalArgumentException("Размеры изображения должны быть кратны количеству сегментов");
        }

        BufferedImage shuffledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = shuffledImage.createGraphics();

        int totalSegments = segmentWidthSize * segmentHeightSize;
        int[] segmentIndices = new int[totalSegments];
        for (int i = 0; i < totalSegments; i++) {
            segmentIndices[i] = i;
        }

        // Перемешивание индексов сегментов
        Random random = new Random();
        for (int i = totalSegments - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = segmentIndices[i];
            segmentIndices[i] = segmentIndices[j];
            segmentIndices[j] = temp;
        }

        Map<Integer, Integer> segmentMapping = new HashMap<>();
        for (int i = 0; i < totalSegments; i++) {
            int originalIndex = i; // Исходный индекс сегмента
            int shuffledIndex = segmentIndices[i]; // Перемешанный индекс сегмента
            int segmentX = (shuffledIndex % segmentWidthSize) * segmentWidth;
            int segmentY = (shuffledIndex / segmentWidthSize) * segmentHeight;
            g2d.drawImage(image.getSubimage(segmentX, segmentY, segmentWidth, segmentHeight),
                    (i % segmentWidthSize) * segmentWidth, (i / segmentWidthSize) * segmentHeight, null);
            segmentMapping.put(originalIndex, shuffledIndex); // Сохраняем исходный индекс как ключ и перемешанный индекс как значение
        }

        g2d.dispose();
        return new Pair<>(shuffledImage, segmentMapping);
    }

    public BufferedImage unshuffledSegments(BufferedImage shuffledImage, Map<Integer, Integer> segmentMapping, int segmentWidthSize, int segmentHeightSize) {
        int width = shuffledImage.getWidth();
        int height = shuffledImage.getHeight();
        int segmentWidth = width / segmentWidthSize;
        int segmentHeight = height / segmentHeightSize;

        BufferedImage unshuffledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = unshuffledImage.createGraphics();

        for (Map.Entry<Integer, Integer> entry : segmentMapping.entrySet()) {
            int originalIndex = entry.getValue(); // Исходный индекс сегмента
            int shuffledIndex = entry.getKey(); // Перемешанный индекс сегмента

            int segmentX = (shuffledIndex % segmentWidthSize) * segmentWidth;
            int segmentY = (shuffledIndex / segmentWidthSize) * segmentHeight;
            int originalX = (originalIndex % segmentWidthSize) * segmentWidth;
            int originalY = (originalIndex / segmentWidthSize) * segmentHeight;

            // Проверка координат и размеров сегментов
            if (segmentX < 0 || segmentY < 0 || originalX < 0 || originalY < 0 ||
                    segmentX + segmentWidth > width || segmentY + segmentHeight > height ||
                    originalX + segmentWidth > width || originalY + segmentHeight > height) {
                throw new IllegalArgumentException("Неправильные координаты или размеры сегментов");
            }

            g2d.drawImage(shuffledImage.getSubimage(segmentX, segmentY, segmentWidth, segmentHeight),
                    originalX, originalY, null);
        }

        g2d.dispose();
        return unshuffledImage;
    }
}