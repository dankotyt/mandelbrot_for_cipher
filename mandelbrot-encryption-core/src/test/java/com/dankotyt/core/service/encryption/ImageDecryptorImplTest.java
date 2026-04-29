package com.dankotyt.core.service.encryption;

import com.dankotyt.core.dto.MandelbrotParams;
import com.dankotyt.core.service.encryption.impl.ImageDecryptorImpl;
import com.dankotyt.core.service.network.CryptoKeyManager;
import com.dankotyt.core.utils.ImageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageDecryptorImplTest {

    @Mock private MandelbrotService mandelbrotService;
    @Mock private SegmentShuffler segmentShuffler;
    @Mock private ImageUtils imageUtils;
    @Mock private CryptoKeyManager cryptoKeyManager;

    @InjectMocks
    private ImageDecryptorImpl imageDecryptor;

    private byte[] sharedSecret;
    private InetAddress peerAddress;
    private MandelbrotParams testParams;

    @BeforeEach
    void setUp() throws Exception {
        sharedSecret = new byte[32];
        peerAddress = InetAddress.getByName("127.0.0.1");
        testParams = new MandelbrotParams(10000, -0.5, 0.0, 250);

        lenient().when(cryptoKeyManager.getMasterSeedFromDH(peerAddress)).thenReturn(sharedSecret);
        lenient().when(mandelbrotService.generateParams(any(SecureRandom.class)))
                .thenReturn(testParams);
    }

    @Test
    void decryptImage_shouldDecryptSuccessfully() throws Exception {
        int fullWidth = 100, fullHeight = 80;
        int areaWidth = 50, areaHeight = 40;

        BufferedImage encryptedImage = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage fractalArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage unshuffledArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);

        when(imageUtils.bytesToImage(any(byte[].class), eq(fullWidth), eq(fullHeight)))
                .thenReturn(encryptedImage);
        when(mandelbrotService.generateImage(eq(areaWidth), eq(areaHeight), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(fractalArea);
        when(segmentShuffler.unshuffle(any(BufferedImage.class), eq(areaWidth), eq(areaHeight), any(SecureRandom.class)))
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

        BufferedImage result = imageDecryptor.decryptImage(file, peerAddress);
        assertNotNull(result);
        assertEquals(fullWidth, result.getWidth());
        assertEquals(fullHeight, result.getHeight());

        verify(mandelbrotService, times(attempts)).generateParams(any(SecureRandom.class));
        verify(mandelbrotService, times(1))
                .generateImage(eq(areaWidth), eq(areaHeight), anyDouble(), anyDouble(), anyDouble(), anyInt());
        verify(segmentShuffler, times(1))
                .unshuffle(any(BufferedImage.class), eq(areaWidth), eq(areaHeight), any(SecureRandom.class));

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

        assertThrows(IllegalArgumentException.class, () -> imageDecryptor.decryptImage(file, peerAddress));
        Files.deleteIfExists(tempFile);
    }

    @Test
    void decryptImage_withZeroAttempts_shouldGenerateOnce() throws Exception {
        int fullWidth = 10, fullHeight = 10;
        int areaWidth = 10, areaHeight = 10;

        BufferedImage encryptedImage = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage fractalArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage unshuffledArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);

        when(imageUtils.bytesToImage(any(byte[].class), eq(fullWidth), eq(fullHeight)))
                .thenReturn(encryptedImage);
        when(mandelbrotService.generateImage(eq(areaWidth), eq(areaHeight), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(fractalArea);
        when(segmentShuffler.unshuffle(any(BufferedImage.class), eq(areaWidth), eq(areaHeight), any(SecureRandom.class)))
                .thenReturn(unshuffledArea);

        int attempts = 0;
        byte[] imageBytes = new byte[fullWidth * fullHeight * 3];
        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 24 + imageBytes.length);
        buffer.put(new byte[16]);
        buffer.putInt(attempts);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(areaWidth);
        buffer.putInt(areaHeight);
        buffer.putInt(fullWidth);
        buffer.putInt(fullHeight);
        buffer.put(imageBytes);
        Path tempFile = Files.createTempFile("test", ".bin");
        Files.write(tempFile, buffer.array());
        File file = tempFile.toFile();

        imageDecryptor.decryptImage(file, peerAddress);
        verify(mandelbrotService, times(1)).generateParams(any(SecureRandom.class));
        Files.deleteIfExists(tempFile);
    }

    @Test
    void decryptImage_withDifferentAreaSize_shouldWork() throws Exception {
        int fullWidth = 100, fullHeight = 100;
        int areaWidth = 30, areaHeight = 30;

        BufferedImage encryptedImage = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage fractalArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage unshuffledArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);

        when(imageUtils.bytesToImage(any(byte[].class), eq(fullWidth), eq(fullHeight)))
                .thenReturn(encryptedImage);
        when(mandelbrotService.generateImage(eq(areaWidth), eq(areaHeight), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(fractalArea);
        when(segmentShuffler.unshuffle(any(BufferedImage.class), eq(areaWidth), eq(areaHeight), any(SecureRandom.class)))
                .thenReturn(unshuffledArea);

        int attempts = 1;
        int startX = 5, startY = 5;
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

        BufferedImage result = imageDecryptor.decryptImage(file, peerAddress);
        assertNotNull(result);
        Files.deleteIfExists(tempFile);
    }

    @Test
    void decryptImage_withLargeArea_shouldWork() throws Exception {
        int fullWidth = 200, fullHeight = 200;
        int areaWidth = 150, areaHeight = 150;

        BufferedImage encryptedImage = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage fractalArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage unshuffledArea = new BufferedImage(areaWidth, areaHeight, BufferedImage.TYPE_INT_RGB);

        when(imageUtils.bytesToImage(any(byte[].class), eq(fullWidth), eq(fullHeight)))
                .thenReturn(encryptedImage);
        when(mandelbrotService.generateImage(eq(areaWidth), eq(areaHeight), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(fractalArea);
        when(segmentShuffler.unshuffle(any(BufferedImage.class), eq(areaWidth), eq(areaHeight), any(SecureRandom.class)))
                .thenReturn(unshuffledArea);

        int attempts = 2;
        int startX = 25, startY = 25;
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

        BufferedImage result = imageDecryptor.decryptImage(file, peerAddress);
        assertNotNull(result);
        Files.deleteIfExists(tempFile);
    }

    @Test
    void decryptImage_withNullFile_shouldThrow() {
        assertThrows(NullPointerException.class, () -> imageDecryptor.decryptImage(null, null));
    }

    @Test
    void decryptImage_withEmptyFile_shouldThrow() throws Exception {
        Path emptyFile = Files.createTempFile("empty", ".bin");
        File file = emptyFile.toFile();
        assertThrows(Exception.class, () -> imageDecryptor.decryptImage(file, peerAddress));
        Files.deleteIfExists(emptyFile);
    }
}