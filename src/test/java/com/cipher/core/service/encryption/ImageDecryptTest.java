package com.cipher.core.service.encryption;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.service.network.CryptoKeyManager;
import com.cipher.core.utils.FileManager;
import com.cipher.core.utils.ImageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ImageDecryptTest {

    @Mock
    private MandelbrotService mandelbrotService;
    @Mock
    private ImageSegmentShuffler imageSegmentShuffler;
    @Mock
    private ImageUtils imageUtils;
    @Mock
    private FileManager tempFileManager;
    @Mock
    private CryptoKeyManager cryptoKeyManager;

    @InjectMocks
    private ImageDecrypt imageDecrypt;

    private byte[] sharedSecret;
    private InetAddress peerAddress;
    private MandelbrotParams testParams;

    @BeforeEach
    void setUp() throws Exception {
        sharedSecret = new byte[32];
        peerAddress = InetAddress.getByName("127.0.0.1");
        testParams = new MandelbrotParams(10000, -0.5, 0.0, 250);

        // Общие настройки для всех тестов
        lenient().when(cryptoKeyManager.getConnectedPeer()).thenReturn(peerAddress);
        lenient().when(cryptoKeyManager.getMasterSeedFromDH(peerAddress)).thenReturn(sharedSecret);
        lenient().when(mandelbrotService.generateParams(any(SecureRandom.class)))
                .thenReturn(testParams);
        lenient().doNothing().when(tempFileManager).saveBufferedImageToTemp(any(BufferedImage.class), anyString());
    }

    @Test
    void decryptImage_shouldDecryptSuccessfully() throws Exception {
        // Настройка специфичных для этого теста моков
        int fullWidth = 100, fullHeight = 80;
        int areaWidth = 50, areaHeight = 40;

        BufferedImage encryptedImage = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage fractalArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage unshuffledArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);

        when(imageUtils.bytesToImage(any(byte[].class), eq(fullWidth), eq(fullHeight)))
                .thenReturn(encryptedImage);
        when(mandelbrotService.generateImage(eq(areaWidth), eq(areaHeight), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(fractalArea);
        when(imageSegmentShuffler.unshuffle(any(BufferedImage.class), eq(areaWidth), eq(areaHeight), any(SecureRandom.class)))
                .thenReturn(unshuffledArea);

        int attempts = 3;
        int startX = 10, startY = 10;
        byte[] imageBytes = new byte[fullWidth * fullHeight * 3];
        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 24 + imageBytes.length);
        buffer.put(new byte[16]); // salt
        buffer.putInt(attempts);
        buffer.putInt(startX);
        buffer.putInt(startY);
        buffer.putInt(areaWidth);
        buffer.putInt(areaHeight);
        buffer.putInt(fullWidth);
        buffer.putInt(fullHeight);
        buffer.put(imageBytes);
        Path tempFile = Files.createTempFile("test", ".bin");
        Files.write(tempFile, buffer.array());
        File file = tempFile.toFile();

        BufferedImage result = imageDecrypt.decryptImage(file);
        assertNotNull(result);
        assertEquals(fullWidth, result.getWidth());
        assertEquals(fullHeight, result.getHeight());

        verify(mandelbrotService, times(attempts)).generateParams(any(SecureRandom.class));
        verify(mandelbrotService, times(1))
                .generateImage(eq(areaWidth), eq(areaHeight), anyDouble(), anyDouble(), anyDouble(), anyInt());
        verify(imageSegmentShuffler, times(1))
                .unshuffle(any(BufferedImage.class), eq(areaWidth), eq(areaHeight), any(SecureRandom.class));
        verify(tempFileManager, times(1))
                .saveBufferedImageToTemp(any(BufferedImage.class), eq("decrypted_image.png"));

        Files.deleteIfExists(tempFile);
    }

    @Test
    void decryptImage_withInvalidLength_shouldThrow() throws Exception {
        int fullWidth = 100, fullHeight = 80;

        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 24 + 1);
        buffer.put(new byte[16]);
        buffer.putInt(1);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(100);
        buffer.putInt(100);
        buffer.putInt(fullWidth);
        buffer.putInt(fullHeight);
        buffer.put((byte) 0);
        Path tempFile = Files.createTempFile("test", ".bin");
        Files.write(tempFile, buffer.array());
        File file = tempFile.toFile();

        assertThrows(IllegalArgumentException.class, () -> imageDecrypt.decryptImage(file));
        Files.deleteIfExists(tempFile);
    }

    @Test
    void decryptImage_withZeroAttempts_shouldGenerateOnce() throws Exception {
        // Настройка специфичных для этого теста моков
        int fullWidth = 10, fullHeight = 10;
        int areaWidth = 10, areaHeight = 10;

        BufferedImage encryptedImage = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage fractalArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage unshuffledArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);

        when(imageUtils.bytesToImage(any(byte[].class), eq(fullWidth), eq(fullHeight)))
                .thenReturn(encryptedImage);
        when(mandelbrotService.generateImage(eq(areaWidth), eq(areaHeight), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(fractalArea);
        when(imageSegmentShuffler.unshuffle(any(BufferedImage.class), eq(areaWidth), eq(areaHeight), any(SecureRandom.class)))
                .thenReturn(unshuffledArea);

        int attempts = 0;
        int startX = 0, startY = 0;
        byte[] imageBytes = new byte[fullWidth * fullHeight * 3];
        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 24 + imageBytes.length);
        buffer.put(new byte[16]);
        buffer.putInt(attempts);
        buffer.putInt(startX);
        buffer.putInt(startY);
        buffer.putInt(areaWidth);
        buffer.putInt(areaHeight);
        buffer.putInt(fullWidth);
        buffer.putInt(fullHeight);
        buffer.put(imageBytes);
        Path tempFile = Files.createTempFile("test", ".bin");
        Files.write(tempFile, buffer.array());
        File file = tempFile.toFile();

        imageDecrypt.decryptImage(file);
        verify(mandelbrotService, times(1)).generateParams(any(SecureRandom.class));
        Files.deleteIfExists(tempFile);
    }

    @Test
    void decryptImage_withDifferentAreaSize_shouldWork() throws Exception {
        // Настройка специфичных для этого теста моков
        int fullWidth = 100, fullHeight = 100;
        int areaWidth = 30, areaHeight = 30;
        int startX = 5, startY = 5;

        BufferedImage encryptedImage = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage fractalArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage unshuffledArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);

        when(imageUtils.bytesToImage(any(byte[].class), eq(fullWidth), eq(fullHeight)))
                .thenReturn(encryptedImage);
        when(mandelbrotService.generateImage(eq(areaWidth), eq(areaHeight), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(fractalArea);
        when(imageSegmentShuffler.unshuffle(any(BufferedImage.class), eq(areaWidth), eq(areaHeight), any(SecureRandom.class)))
                .thenReturn(unshuffledArea);

        int attempts = 1;
        byte[] imageBytes = new byte[fullWidth * fullHeight * 3];
        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 24 + imageBytes.length);
        buffer.put(new byte[16]);
        buffer.putInt(attempts);
        buffer.putInt(startX);
        buffer.putInt(startY);
        buffer.putInt(areaWidth);
        buffer.putInt(areaHeight);
        buffer.putInt(fullWidth);
        buffer.putInt(fullHeight);
        buffer.put(imageBytes);
        Path tempFile = Files.createTempFile("test", ".bin");
        Files.write(tempFile, buffer.array());
        File file = tempFile.toFile();

        BufferedImage result = imageDecrypt.decryptImage(file);
        assertNotNull(result);
        assertEquals(fullWidth, result.getWidth());
        assertEquals(fullHeight, result.getHeight());

        verify(mandelbrotService, times(1))
                .generateImage(eq(areaWidth), eq(areaHeight), anyDouble(), anyDouble(), anyDouble(), anyInt());
        verify(imageSegmentShuffler, times(1))
                .unshuffle(any(BufferedImage.class), eq(areaWidth), eq(areaHeight), any(SecureRandom.class));

        Files.deleteIfExists(tempFile);
    }

    @Test
    void decryptImage_withLargeArea_shouldWork() throws Exception {
        // Настройка специфичных для этого теста моков
        int fullWidth = 200, fullHeight = 200;
        int areaWidth = 150, areaHeight = 150;
        int startX = 25, startY = 25;

        BufferedImage encryptedImage = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage fractalArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage unshuffledArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);

        when(imageUtils.bytesToImage(any(byte[].class), eq(fullWidth), eq(fullHeight)))
                .thenReturn(encryptedImage);
        when(mandelbrotService.generateImage(eq(areaWidth), eq(areaHeight), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(fractalArea);
        when(imageSegmentShuffler.unshuffle(any(BufferedImage.class), eq(areaWidth), eq(areaHeight), any(SecureRandom.class)))
                .thenReturn(unshuffledArea);

        int attempts = 2;
        byte[] imageBytes = new byte[fullWidth * fullHeight * 3];
        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 24 + imageBytes.length);
        buffer.put(new byte[16]);
        buffer.putInt(attempts);
        buffer.putInt(startX);
        buffer.putInt(startY);
        buffer.putInt(areaWidth);
        buffer.putInt(areaHeight);
        buffer.putInt(fullWidth);
        buffer.putInt(fullHeight);
        buffer.put(imageBytes);
        Path tempFile = Files.createTempFile("test", ".bin");
        Files.write(tempFile, buffer.array());
        File file = tempFile.toFile();

        BufferedImage result = imageDecrypt.decryptImage(file);
        assertNotNull(result);
        assertEquals(fullWidth, result.getWidth());
        assertEquals(fullHeight, result.getHeight());

        Files.deleteIfExists(tempFile);
    }

    @Test
    void decryptImage_withNullFile_shouldThrow() {
        assertThrows(NullPointerException.class, () -> imageDecrypt.decryptImage(null));
    }

    @Test
    void decryptImage_withEmptyFile_shouldThrow() throws Exception {
        Path emptyFile = Files.createTempFile("empty", ".bin");
        File file = emptyFile.toFile();
        assertThrows(Exception.class, () -> imageDecrypt.decryptImage(file));
        Files.deleteIfExists(emptyFile);
    }
}