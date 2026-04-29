package com.dankotyt.core.service.encryption;

import com.dankotyt.core.dto.MandelbrotParams;
import com.dankotyt.core.dto.EncryptedData;
import com.dankotyt.core.dto.SegmentationResult;
import com.dankotyt.core.service.encryption.impl.ImageEncryptorImpl;
import com.dankotyt.core.utils.ImageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageEncryptorImplTest {

    @Mock private MandelbrotService mandelbrotService;
    @Mock private SegmentShuffler segmentShuffler;
    @Mock private ImageUtils imageUtils;

    @InjectMocks
    private ImageEncryptorImpl imageEncryptor;

    private byte[] sharedSecret;
    private BufferedImage testImage;
    private BufferedImage fractalImage;
    private BufferedImage shuffledImage;
    private MandelbrotParams testParams;
    private byte[] testImageBytes;

    @BeforeEach
    void setUp() throws Exception {
        sharedSecret = new byte[32];
        testImage = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        fractalImage = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        shuffledImage = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        testImageBytes = new byte[100 * 80 * 3];
        testParams = new MandelbrotParams(10000, -0.5, 0.0, 250);

        lenient().when(mandelbrotService.generateParams(any(SecureRandom.class)))
                .thenReturn(testParams);
        lenient().when(mandelbrotService.generateImage(anyInt(), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(fractalImage);
        lenient().when(segmentShuffler.segmentAndShuffle(any(BufferedImage.class), any(SecureRandom.class)))
                .thenReturn(new SegmentationResult(shuffledImage, 1, 100, 80, null));
        lenient().when(imageUtils.imageToBytes(any(BufferedImage.class)))
                .thenReturn(testImageBytes);
    }

    @Test
    void prepareSession_shouldNotThrowException() {
        assertDoesNotThrow(() -> imageEncryptor.prepareSession(sharedSecret));
    }

    @Test
    void generateNextFractal_shouldGenerateImage() throws Exception {
        imageEncryptor.prepareSession(sharedSecret);
        BufferedImage result = imageEncryptor.generateNextFractal(100, 80);
        assertNotNull(result);
        verify(mandelbrotService).generateParams(any(SecureRandom.class));
        verify(mandelbrotService).generateImage(eq(100), eq(80), anyDouble(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void encryptWhole_shouldReturnEncryptedData() throws Exception {
        imageEncryptor.prepareSession(sharedSecret);
        imageEncryptor.generateNextFractal(100, 80);
        EncryptedData data = imageEncryptor.encryptWhole(testImage);

        assertNotNull(data);
        assertEquals(100, data.originalWidth());
        assertEquals(80, data.originalHeight());
        assertEquals(0, data.startX());
        assertEquals(0, data.startY());
        assertNotNull(data.imageBytes());
        assertArrayEquals(testImageBytes, data.imageBytes());
        assertNotNull(data.sessionSalt());

        verify(imageUtils).imageToBytes(any());
        verify(segmentShuffler).segmentAndShuffle(any(), any());
    }

    @Test
    void encryptWhole_withoutFractal_shouldGenerateAutomatically() throws Exception {
        imageEncryptor.prepareSession(sharedSecret);
        // Не вызываем generateNextFractal – должен сгенерироваться сам
        EncryptedData data = imageEncryptor.encryptWhole(testImage);

        assertNotNull(data);
        verify(mandelbrotService, atLeastOnce()).generateImage(eq(100), eq(80), anyDouble(), anyDouble(), anyDouble(), anyInt());
        verify(segmentShuffler).segmentAndShuffle(any(), any());
        verify(imageUtils).imageToBytes(any());
    }

    @Test
    void prepareSession_withNullSharedSecret_shouldThrow() {
        assertThrows(Exception.class, () -> imageEncryptor.prepareSession(null));
    }

    @Test
    void encryptWhole_withNullImage_shouldThrow() throws Exception {
        imageEncryptor.prepareSession(sharedSecret);
        assertThrows(Exception.class, () -> imageEncryptor.encryptWhole(null));
    }
}