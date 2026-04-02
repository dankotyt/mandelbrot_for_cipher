package com.cipher.core.service.encryption;

import com.cipher.core.dto.segmentation.SegmentationResult;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import static org.junit.jupiter.api.Assertions.*;

class ImageSegmentShufflerTest {

    private final ImageSegmentShuffler shuffler = new ImageSegmentShuffler();

    @Test
    void generateSegmentSize_withSmallImage_shouldReturn1() {
        assertEquals(1, shuffler.generateSegmentSize(768, 768));
        assertEquals(1, shuffler.generateSegmentSize(500, 600));
    }

    @Test
    void generateSegmentSize_withMediumImage_shouldReturn4() {
        assertEquals(4, shuffler.generateSegmentSize(1024, 768));
        assertEquals(4, shuffler.generateSegmentSize(1920, 1080));
    }

    @Test
    void generateSegmentSize_withLargeImage_shouldReturn16() {
        assertEquals(16, shuffler.generateSegmentSize(1921, 1080));
        assertEquals(16, shuffler.generateSegmentSize(4000, 3000));
    }

    @Test
    void padImageToSegmentSize_whenNotAligned_shouldPad() {
        BufferedImage img = new BufferedImage(5, 7, BufferedImage.TYPE_INT_RGB);
        BufferedImage padded = shuffler.padImageToSegmentSize(img, 4);
        assertEquals(8, padded.getWidth());
        assertEquals(8, padded.getHeight());
    }

    @Test
    void padImageToSegmentSize_whenAligned_shouldReturnSame() {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = shuffler.padImageToSegmentSize(img, 4);
        assertSame(img, result);
    }

    @Test
    void segmentAndShuffle_shouldBeReversible() throws Exception {
        SecureRandom prng = SecureRandom.getInstance("SHA1PRNG");
        prng.setSeed(12345L);
        BufferedImage original = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        // fill with unique colors
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                int rgb = (y * 10 + x) & 0x00FFFFFF;
                original.setRGB(x, y, rgb);
            }
        }
        SegmentationResult result = shuffler.segmentAndShuffle(original, prng);
        BufferedImage shuffled = result.shuffledImage();

        // unshuffle
        SecureRandom samePrng = SecureRandom.getInstance("SHA1PRNG");
        samePrng.setSeed(12345L);
        BufferedImage unshuffled = shuffler.unshuffle(shuffled, original.getWidth(), original.getHeight(), samePrng);

        // compare pixels
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                assertEquals(original.getRGB(x, y), unshuffled.getRGB(x, y));
            }
        }
    }

    @Test
    void unshuffle_withNonAlignedImage_shouldRestoreOriginal() throws Exception {
        SecureRandom prng = SecureRandom.getInstance("SHA1PRNG");
        prng.setSeed(12345L);
        BufferedImage original = new BufferedImage(11, 9, BufferedImage.TYPE_INT_RGB);
        // fill unique colors
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 11; x++) {
                int rgb = (y * 11 + x) & 0x00FFFFFF;
                original.setRGB(x, y, rgb);
            }
        }
        SegmentationResult result = shuffler.segmentAndShuffle(original, prng);
        BufferedImage shuffled = result.shuffledImage();

        SecureRandom samePrng = SecureRandom.getInstance("SHA1PRNG");
        samePrng.setSeed(12345L);
        BufferedImage unshuffled = shuffler.unshuffle(shuffled, original.getWidth(), original.getHeight(), samePrng);

        // compare pixels
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                assertEquals(original.getRGB(x, y), unshuffled.getRGB(x, y));
            }
        }
    }
}