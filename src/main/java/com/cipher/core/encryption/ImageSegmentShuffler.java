package com.cipher.core.encryption;

import com.cipher.core.dto.segmentation.SegmentationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.security.SecureRandom;
import java.util.*;
import java.awt.*;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageSegmentShuffler {

    private SecureRandom segmentPrng;

    /**
     * Инициализирует генератор псевдослучайных чисел для перемешивания сегментов
     * с использованием предоставленного ключа.
     *
     * @param key ключ для инициализации PRNG
     * @throws Exception если возникает ошибка при создании экземпляра SecureRandom
     */
    public void initialize(byte[] key) throws Exception {
        this.segmentPrng = SecureRandom.getInstance("SHA1PRNG");
        this.segmentPrng.setSeed(key);
    }

    /**
     * Создает список сегментов изображения заданного размера
     * @param image исходное изображение
     * @param segmentSize ширина и высота сегмента (может быть 1, 2 или любое другое значение)
     * @return список прямоугольников, представляющих сегменты
     */
    private static List<Rectangle> createSegments(BufferedImage image, int segmentSize) {
        List<Rectangle> segments = new ArrayList<>();
        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height; y += segmentSize) {
            for (int x = 0; x < width; x += segmentSize) {
                int segW = Math.min(segmentSize, width - x);
                int segH = Math.min(segmentSize, height - y);
                segments.add(new Rectangle(x, y, segW, segH));
            }
        }

        return segments;
    }

    /**
     * Выполняет перемешивание сегментов изображения.
     * Процесс включает:
     * <ol>
     *   <li>Определение размера сегмента на основе размеров изображения</li>
     *   <li>Дополнение изображения до размеров, кратных размеру сегмента</li>
     *   <li>Разбиение на сегменты и их случайное перемешивание</li>
     * </ol>
     *
     * @param image исходное изображение для перемешивания
     * @return результат перемешивания, включающий перемешанное изображение,
     *         размер сегмента и карту соответствия
     */
    public SegmentationResult segmentAndShuffle(BufferedImage image) {
        int segmentSize = generateSegmentSize(image.getWidth(), image.getHeight());
        log.info("Segment size used: {}", segmentSize);
        BufferedImage paddedImage = padImageToSegmentSize(image, segmentSize);
        List<Rectangle> segments = createSegments(paddedImage, segmentSize);
        List<Integer> indices = getShuffledIndices(segments.size());

        BufferedImage result = new BufferedImage(paddedImage.getWidth(), paddedImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        for (int i = 0; i < segments.size(); i++) {
            Rectangle src = segments.get(indices.get(i));
            Rectangle dst = segments.get(i);
            BufferedImage segment = paddedImage.getSubimage(src.x, src.y, src.width, src.height);
            g.drawImage(segment, dst.x, dst.y, null);
        }
        g.dispose();

        Map<Integer, Integer> mapping = new HashMap<>();
        for (int i = 0; i < indices.size(); i++) mapping.put(i, indices.get(i));
        return new SegmentationResult(result, segmentSize, paddedImage.getWidth(), paddedImage.getHeight(), mapping);
    }

    /**
     * Восстанавливает оригинальный порядок сегментов в перемешанном изображении.
     * Использует детерминированное перемешивание на основе того же PRNG,
     * что и при шифровании, для получения обратного порядка сегментов.
     *
     * @param shuffledImage  перемешанное изображение
     * @param originalWidth  оригинальная ширина изображения (до дополнения)
     * @param originalHeight оригинальная высота изображения (до дополнения)
     * @return изображение с восстановленным порядком сегментов
     */
    public BufferedImage unshuffle(BufferedImage shuffledImage, int originalWidth, int originalHeight) {
        int segmentSize = generateSegmentSize(originalWidth, originalHeight);
        log.info("Segment size used: {}", segmentSize);
        List<Rectangle> segments = createSegments(shuffledImage, segmentSize);
        List<Integer> indices = getShuffledIndices(segments.size());

        Map<Integer, Integer> reverse = new HashMap<>();
        for (int i = 0; i < indices.size(); i++) reverse.put(indices.get(i), i);

        BufferedImage result = new BufferedImage(originalWidth, originalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        for (int i = 0; i < segments.size(); i++) {
            Rectangle src = segments.get(reverse.get(i));
            Rectangle dst = segments.get(i);
            BufferedImage segment = shuffledImage.getSubimage(src.x, src.y, src.width, src.height);
            g.drawImage(segment, dst.x, dst.y, null);
        }
        g.dispose();
        return result;
    }

    /**
     * Дополняет изображение прозрачными пикселями до размеров, кратных размеру сегмента.
     * Дополнение добавляется справа и снизу изображения.
     *
     * @param image       исходное изображение
     * @param segmentSize размер сегмента
     * @return дополненное изображение или исходное, если дополнение не требуется
     */
    public BufferedImage padImageToSegmentSize(BufferedImage image, int segmentSize) {
        int newWidth = (int) Math.ceil((double) image.getWidth() / segmentSize) * segmentSize;
        int newHeight = (int) Math.ceil((double) image.getHeight() / segmentSize) * segmentSize;

        if (image.getWidth() == newWidth && image.getHeight() == newHeight) {
            return image;
        }

        BufferedImage paddedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = paddedImage.createGraphics();

        g.drawImage(image, 0, 0, null);

        if (newWidth > image.getWidth()) {
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(image.getWidth(), 0, newWidth - image.getWidth(), newHeight);
        }
        if (newHeight > image.getHeight()) {
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, image.getHeight(), newWidth, newHeight - image.getHeight());
        }

        g.dispose();
        return paddedImage;
    }

    /**
     * Генерирует размер сегмента в зависимости от размера изображения
     */
    public int generateSegmentSize(int imageWidth, int imageHeight) {
        int maxDimension = Math.max(imageWidth, imageHeight);

        if (maxDimension <= 768) return 1;
        else if (maxDimension <= 1920) return 4;
        else return 16;
    }

    /**
     * Перемешивает список детерминированным образом
     */
    public <T> void shuffleList(List<T> list) {
        if (segmentPrng == null) throw new IllegalStateException("Segment PRNG not initialized");
        for (int i = list.size() - 1; i > 0; i--) {
            int j = segmentPrng.nextInt(i + 1);
            T temp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, temp);
        }
    }

    private List<Integer> getShuffledIndices(int size) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < size; i++) indices.add(i);
        shuffleList(indices);
        return indices;
    }
}