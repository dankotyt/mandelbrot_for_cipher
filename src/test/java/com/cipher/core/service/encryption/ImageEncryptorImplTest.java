package com.cipher.core.service.encryption;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.dto.segmentation.SegmentationResult;
import com.cipher.core.service.encryption.impl.ImageEncryptorImpl;
import com.cipher.core.service.encryption.impl.ImageSegmentShuffler;
import com.cipher.core.service.encryption.impl.MandelbrotService;
import com.cipher.core.utils.FileManager;
import com.cipher.core.utils.ImageUtils;
import com.cipher.core.utils.SceneManager;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.security.SecureRandom;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ImageEncryptorImplTest {

    @Mock
    private MandelbrotService mandelbrotService;
    @Mock
    private ImageSegmentShuffler imageSegmentShuffler;
    @Mock
    private SceneManager sceneManager;
    @Mock
    private FileManager tempFileManager;
    @Mock
    private ImageUtils imageUtils;

    @InjectMocks
    private ImageEncryptorImpl imageEncryptorImpl;

    private byte[] sharedSecret;
    private BufferedImage testImage;
    private BufferedImage fractalImage;
    private BufferedImage shuffledImage;
    private File outputFile;
    private MandelbrotParams testParams;
    private byte[] testImageBytes;

    @BeforeEach
    void setUp() throws Exception {
        sharedSecret = new byte[32];
        testImage = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        fractalImage = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        shuffledImage = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        outputFile = new File("test.bin");
        testImageBytes = new byte[100 * 80 * 3];

        testParams = new MandelbrotParams(10000, -0.5, 0.0, 250);

        // Настройка моков - используем lenient для стабов, которые используются не во всех тестах
        lenient().when(mandelbrotService.generateParams(any(SecureRandom.class)))
                .thenReturn(testParams);

        lenient().when(mandelbrotService.generateImage(anyInt(), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(fractalImage);

        lenient().when(imageSegmentShuffler.segmentAndShuffle(any(BufferedImage.class), any(SecureRandom.class)))
                .thenReturn(new SegmentationResult(shuffledImage, 1, 100, 80, null));

        lenient().when(tempFileManager.saveBytesToFile(any(byte[].class), anyString()))
                .thenReturn(outputFile);

        lenient().when(imageUtils.imageToBytes(any(BufferedImage.class)))
                .thenReturn(testImageBytes);
    }

    @Test
    void prepareSession_shouldNotThrowException() throws Exception {
        assertDoesNotThrow(() -> imageEncryptorImpl.prepareSession(sharedSecret));
    }

    @Test
    void generateNextFractal_shouldGenerateImage() throws Exception {
        imageEncryptorImpl.prepareSession(sharedSecret);
        BufferedImage result = imageEncryptorImpl.generateNextFractal(100, 80);
        assertNotNull(result);
        verify(mandelbrotService, times(1)).generateParams(any(SecureRandom.class));
        verify(mandelbrotService, times(1))
                .generateImage(eq(100), eq(80), anyDouble(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void encryptWhole_shouldEncryptAndSave() throws Exception {
        imageEncryptorImpl.prepareSession(sharedSecret);
        imageEncryptorImpl.generateNextFractal(100, 80);
        imageEncryptorImpl.encryptWhole(testImage);

        verify(mandelbrotService, atLeastOnce())
                .generateImage(eq(100), eq(80), anyDouble(), anyDouble(), anyDouble(), anyInt());
        verify(imageSegmentShuffler, times(1))
                .segmentAndShuffle(any(BufferedImage.class), any(SecureRandom.class));
        verify(imageUtils, times(1)).imageToBytes(any(BufferedImage.class));
        verify(tempFileManager, times(1)).saveBytesToFile(any(byte[].class), anyString());
        verify(sceneManager, times(1)).showEncryptFinalPanel(shuffledImage, outputFile);
    }

    @Test
    void encryptWhole_withoutFractal_shouldGenerate() throws Exception {
        imageEncryptorImpl.prepareSession(sharedSecret);
        // Не вызываем generateNextFractal, пусть fractal == null
        imageEncryptorImpl.encryptWhole(testImage);

        verify(mandelbrotService, times(1))
                .generateImage(eq(100), eq(80), anyDouble(), anyDouble(), anyDouble(), anyInt());
        verify(imageSegmentShuffler, times(1)).segmentAndShuffle(any(), any());
        verify(imageUtils, times(1)).imageToBytes(any(BufferedImage.class));
        verify(tempFileManager, times(1)).saveBytesToFile(any(), anyString());
    }

    @Test
    void encryptPart_shouldEncryptArea() throws Exception {
        imageEncryptorImpl.prepareSession(sharedSecret);
        Rectangle2D area = new Rectangle2D(10, 10, 50, 40);

        BufferedImage areaFractal = new BufferedImage(50, 40, BufferedImage.TYPE_INT_RGB);
        when(mandelbrotService.generateImage(eq(50), eq(40), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(areaFractal);

        BufferedImage areaShuffled = new BufferedImage(50, 40, BufferedImage.TYPE_INT_RGB);
        when(imageSegmentShuffler.segmentAndShuffle(any(BufferedImage.class), any(SecureRandom.class)))
                .thenReturn(new SegmentationResult(areaShuffled, 1, 50, 40, null));

        imageEncryptorImpl.encryptPart(testImage, area);

        verify(mandelbrotService, times(1))
                .generateImage(eq(50), eq(40), anyDouble(), anyDouble(), anyDouble(), anyInt());
        verify(imageSegmentShuffler, times(1))
                .segmentAndShuffle(any(BufferedImage.class), any(SecureRandom.class));
        verify(imageUtils, times(1)).imageToBytes(any(BufferedImage.class));
        verify(tempFileManager, times(1)).saveBytesToFile(any(byte[].class), anyString());
        verify(sceneManager, times(1)).showEncryptFinalPanel(any(BufferedImage.class), eq(outputFile));
    }

    @Test
    void encryptPart_withFractalSizeMismatch_shouldGenerateNew() throws Exception {
        imageEncryptorImpl.prepareSession(sharedSecret);
        imageEncryptorImpl.generateNextFractal(100, 80);
        Rectangle2D area = new Rectangle2D(0, 0, 30, 30);

        BufferedImage areaFractal = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        when(mandelbrotService.generateImage(eq(30), eq(30), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(areaFractal);

        BufferedImage areaShuffled = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        when(imageSegmentShuffler.segmentAndShuffle(any(BufferedImage.class), any(SecureRandom.class)))
                .thenReturn(new SegmentationResult(areaShuffled, 1, 30, 30, null));

        imageEncryptorImpl.encryptPart(testImage, area);

        verify(mandelbrotService, times(1))
                .generateImage(eq(30), eq(30), anyDouble(), anyDouble(), anyDouble(), anyInt());
        verify(imageUtils, times(1)).imageToBytes(any(BufferedImage.class));
        verify(tempFileManager, times(1)).saveBytesToFile(any(byte[].class), anyString());
    }

    @Test
    void prepareSession_withNullSharedSecret_shouldThrow() {
        assertThrows(Exception.class, () -> imageEncryptorImpl.prepareSession(null));
    }

    @Test
    void encryptWhole_withNullImage_shouldThrow() throws Exception {
        byte[] secret = new byte[32];
        imageEncryptorImpl.prepareSession(secret);
        assertThrows(Exception.class, () -> imageEncryptorImpl.encryptWhole(null));
    }
}