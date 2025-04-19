package Model;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.Graphics2D;
import java.util.*;
import java.awt.*;
import java.util.List;

public class ImageSegmentShuffler {

    private int[] pixels;
    /**
     * Создает список сегментов изображения заданного размера
     * @param image исходное изображение
     * @param segmentWidth ширина сегмента (может быть 1, 2 или любое другое значение)
     * @param segmentHeight высота сегмента (может быть 1, 2 или любое другое значение)
     * @return список прямоугольников, представляющих сегменты
     */
    public static List<Rectangle> createSegments(BufferedImage image, int segmentWidth, int segmentHeight) {
        List<Rectangle> segments = new ArrayList<>();
        int width = image.getWidth();
        int height = image.getHeight();

        // Обрабатываем края изображения, где сегменты могут быть меньше заданного размера
        for (int y = 0; y < height; y += segmentHeight) {
            for (int x = 0; x < width; x += segmentWidth) {
                int segW = Math.min(segmentWidth, width - x);
                int segH = Math.min(segmentHeight, height - y);
                segments.add(new Rectangle(x, y, segW, segH));
            }
        }

        return segments;
    }

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

    /**
     * Перемешивает сегменты изображения
     * @param image исходное изображение
     * @param segmentWidth ширина сегмента
     * @param segmentHeight высота сегмента
     * @return перемешанное изображение и карта соответствия сегментов
     */
    public static Pair<BufferedImage, Map<Integer, Integer>> shuffleSegments(
            BufferedImage image, int segmentWidth, int segmentHeight) {

        List<Rectangle> segments = createSegments(image, segmentWidth, segmentHeight);
        BufferedImage result = new BufferedImage(
                image.getWidth(), image.getHeight(), image.getType());

        // Создаем список индексов и перемешиваем его
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            indices.add(i);
        }
        Collections.shuffle(indices);

        // Создаем карту соответствия оригинальных и новых позиций
        Map<Integer, Integer> segmentMapping = new HashMap<>();
        for (int i = 0; i < indices.size(); i++) {
            segmentMapping.put(i, indices.get(i));
        }

        // Копируем сегменты в новые позиции
        Graphics2D g = result.createGraphics();
        for (int i = 0; i < segments.size(); i++) {
            Rectangle srcRect = segments.get(indices.get(i));
            Rectangle destRect = segments.get(i);

            BufferedImage segment = image.getSubimage(
                    srcRect.x, srcRect.y, srcRect.width, srcRect.height);
            g.drawImage(segment, destRect.x, destRect.y, null);
        }
        g.dispose();

        return new Pair<>(result, segmentMapping);
    }

    /**
     * Восстанавливает оригинальный порядок сегментов
     * @param shuffledImage перемешанное изображение
     * @param segmentMapping карта соответствия сегментов
     * @param segmentWidth ширина сегмента
     * @param segmentHeight высота сегмента
     * @return изображение с восстановленным порядком сегментов
     */
    public static BufferedImage unshuffledSegments(
            BufferedImage shuffledImage, Map<Integer, Integer> segmentMapping,
            int segmentWidth, int segmentHeight) {

        List<Rectangle> segments = createSegments(shuffledImage, segmentWidth, segmentHeight);
        BufferedImage result = new BufferedImage(
                shuffledImage.getWidth(), shuffledImage.getHeight(), shuffledImage.getType());

        // Создаем обратную карту соответствия
        Map<Integer, Integer> reverseMapping = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : segmentMapping.entrySet()) {
            reverseMapping.put(entry.getValue(), entry.getKey());
        }

        // Восстанавливаем оригинальный порядок
        Graphics2D g = result.createGraphics();
        for (int i = 0; i < segments.size(); i++) {
            Rectangle srcRect = segments.get(reverseMapping.get(i));
            Rectangle destRect = segments.get(i);

            BufferedImage segment = shuffledImage.getSubimage(
                    srcRect.x, srcRect.y, srcRect.width, srcRect.height);
            g.drawImage(segment, destRect.x, destRect.y, null);
        }
        g.dispose();

        return result;
    }
    public static BufferedImage resizeImage(BufferedImage image, int width, int height) {
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(image, 0, 0, width, height, null);
        g2d.dispose();
        return resizedImage;
    }
}