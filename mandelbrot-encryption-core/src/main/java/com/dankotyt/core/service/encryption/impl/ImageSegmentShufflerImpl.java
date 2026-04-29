package com.dankotyt.core.service.encryption.impl;

import com.dankotyt.core.dto.segmentation.SegmentationResult;
import com.dankotyt.core.service.encryption.SegmentShuffler;
import com.dankotyt.core.service.encryption.SegmentSizeStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageSegmentShufflerImpl implements SegmentShuffler {

    private final SegmentSizeStrategy sizeStrategy;

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
    @Override
    public SegmentationResult segmentAndShuffle(BufferedImage image, SecureRandom prng) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null");
        }
        if (prng == null) {
            throw new IllegalArgumentException("PRNG cannot be null");
        }
        int segmentSize = sizeStrategy.determineSegmentSize(image.getWidth(), image.getHeight());
        log.info("Segment size used: {}", segmentSize);
        BufferedImage paddedImage = padImageToSegmentSize(image, segmentSize);
        List<Rectangle> segments = createSegments(paddedImage, segmentSize);
        List<Integer> indices = getShuffledIndices(segments.size(), prng);

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
    @Override
    public BufferedImage unshuffle(BufferedImage shuffledImage, int originalWidth, int originalHeight, SecureRandom prng) {
        if (shuffledImage == null) {
            throw new IllegalArgumentException("Shuffled image cannot be null");
        }
        if (originalWidth <= 0 || originalHeight <= 0) {
            throw new IllegalArgumentException("Original dimensions must be positive");
        }
        if (prng == null) {
            throw new IllegalArgumentException("PRNG cannot be null");
        }
        int segmentSize = sizeStrategy.determineSegmentSize(originalWidth, originalHeight);
        log.info("Segment size used: {}", segmentSize);
        List<Rectangle> segments = createSegments(shuffledImage, segmentSize);
        List<Integer> indices = getShuffledIndices(segments.size(), prng);

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
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null");
        }
        if (segmentSize <= 0) {
            throw new IllegalArgumentException("Segment size must be positive");
        }
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
    private <T> void shuffleList(List<T> list, SecureRandom prng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = prng.nextInt(i + 1);
            T temp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, temp);
        }
    }

    private List<Integer> getShuffledIndices(int size, SecureRandom prng) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < size; i++) indices.add(i);
        shuffleList(indices, prng);
        return indices;
    }
}