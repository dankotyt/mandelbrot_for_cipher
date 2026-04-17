package com.cipher.core;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.service.encryption.*;
import com.cipher.core.service.network.CryptoKeyManager;
import com.cipher.core.service.network.impl.ECDHCryptoKeyManagerImpl;
import com.cipher.core.utils.FileManager;
import com.cipher.core.utils.ImageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class CryptoIntegrationTest {

    @TempDir
    Path tempDir;

    private FileManager fileManager;
    private ImageUtils imageUtils;
    private MandelbrotService mandelbrotService;
    private ImageSegmentShuffler shuffler;
    private CryptoKeyManager aliceKeyManager;
    private CryptoKeyManager bobKeyManager;
    private InetAddress bobAddress;

    // Заглушки UI (минимальные, без моков)
    private static class NoOpSceneManager extends com.cipher.core.utils.SceneManager {
        NoOpSceneManager() { super(null, null); }
        @Override public void showEncryptFinalPanel(BufferedImage image, File file) {}
    }
    private static class NoOpDialogDisplayer extends com.cipher.core.utils.DialogDisplayer {
        @Override public void showErrorDialog(String msg) {}
        @Override public void showSuccessDialog(String msg) {}
        @Override public void showErrorAlert(String title, String msg) {}
    }

    @BeforeEach
    void setUp() throws Exception {
        bobAddress = InetAddress.getByName("192.168.1.2");
        InetAddress aliceAddress = InetAddress.getByName("192.168.1.1");

        fileManager = new FileManager(new NoOpDialogDisplayer(), new NoOpSceneManager(), new ImageUtils()) {
            @Override public String getTempPath() { return tempDir.toString() + File.separator; }
        };
        fileManager.createTempFolder();

        imageUtils = new ImageUtils();
        mandelbrotService = new MandelbrotService();
        shuffler = new ImageSegmentShuffler();

        aliceKeyManager = new ECDHCryptoKeyManagerImpl();
        bobKeyManager = new ECDHCryptoKeyManagerImpl();

        var aliceKeys = aliceKeyManager.getCurrentKeys();
        var bobKeys = bobKeyManager.getCurrentKeys();
        aliceKeys.computeSharedSecret(bobKeys.getPublicKeyBytes());
        bobKeys.computeSharedSecret(aliceKeys.getPublicKeyBytes());
        aliceKeyManager.addConnection(bobAddress, bobKeys);
        bobKeyManager.addConnection(aliceAddress, aliceKeys);
        aliceKeyManager.setConnectedPeer(bobAddress);
        bobKeyManager.setConnectedPeer(aliceAddress);
    }

    private BufferedImage createTestImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, ((x * 255 / w) << 16) | ((y * 255 / h) << 8) | ((x + y) % 256));
        return img;
    }

    private void assertImagesEqual(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y) & 0x00FFFFFF, actual.getRGB(x, y) & 0x00FFFFFF,
                        "Mismatch at (" + x + "," + y + ")");
            }
        }
    }

    // ==================== ИНТЕГРАЦИОННЫЕ ТЕСТЫ (адаптированы под текущий код) ====================

    @Test
    @DisplayName("ИТ-1: generateNextFractal возвращает фрактал и сохраняет его в поле")
    void testGenerateNextFractalReturnsAndSavesFractal() throws Exception {
        byte[] secret = aliceKeyManager.getMasterSeedFromDH(bobAddress);
        ImageEncrypt encrypt = new ImageEncrypt(mandelbrotService, shuffler, new NoOpSceneManager(), fileManager, imageUtils);
        encrypt.prepareSession(secret);

        BufferedImage image = createTestImage(200, 200);
        BufferedImage generatedFractal = encrypt.generateNextFractal(image.getWidth(), image.getHeight());

        assertNotNull(generatedFractal, "generateNextFractal должен возвращать не-null фрактал");

        Field fractalField = ImageEncrypt.class.getDeclaredField("fractal");
        fractalField.setAccessible(true);
        BufferedImage savedFractal = (BufferedImage) fractalField.get(encrypt);

        assertNotNull(savedFractal, "Фрактал должен сохраняться в поле класса");
        assertSame(generatedFractal, savedFractal, "Сохранённый фрактал должен быть тем же объектом");

        encrypt.encryptWhole(image);
        BufferedImage usedFractal = (BufferedImage) fractalField.get(encrypt);
        assertSame(savedFractal, usedFractal, "При шифровании должен использоваться тот же фрактал");
    }

    @Test
    @DisplayName("ИТ-2: encryptWhole успешно шифрует изображение (без проверки валидности фрактала)")
    void testEncryptWholeCompletesSuccessfully() throws Exception {
        byte[] secret = aliceKeyManager.getMasterSeedFromDH(bobAddress);
        ImageEncrypt encrypt = new ImageEncrypt(mandelbrotService, shuffler, new NoOpSceneManager(), fileManager, imageUtils);
        encrypt.prepareSession(secret);

        BufferedImage image = createTestImage(200, 200);
        assertDoesNotThrow(() -> encrypt.encryptWhole(image),
                "encryptWhole должен завершаться без исключений даже если фрактал невалиден");
    }

    @Test
    @DisplayName("ИТ-3: Сквозной сценарий шифрование → дешифрование (реальный ECDH)")
    void testEndToEndEncryptDecrypt() throws Exception {
        byte[] aliceSecret = aliceKeyManager.getMasterSeedFromDH(bobAddress);

        ImageEncrypt encrypt = new ImageEncrypt(mandelbrotService, shuffler, new NoOpSceneManager(), fileManager, imageUtils);
        encrypt.prepareSession(aliceSecret);

        BufferedImage original = createTestImage(300, 300);
        encrypt.encryptWhole(original);

        File encryptedFile = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".bin"))
                .findFirst()
                .orElseThrow().toFile();

        ImageDecrypt decrypt = new ImageDecrypt(mandelbrotService, shuffler, imageUtils, fileManager, bobKeyManager);
        BufferedImage decrypted = decrypt.decryptImage(encryptedFile);

        assertImagesEqual(original, decrypted);
    }

    @Test
    @DisplayName("ИТ-4: Частичное шифрование области и дешифрование")
    void testPartialEncryptDecrypt() throws Exception {
        byte[] secret = aliceKeyManager.getMasterSeedFromDH(bobAddress);
        ImageEncrypt encrypt = new ImageEncrypt(mandelbrotService, shuffler, new NoOpSceneManager(), fileManager, imageUtils);
        encrypt.prepareSession(secret);

        BufferedImage original = createTestImage(400, 300);
        javafx.geometry.Rectangle2D area = new javafx.geometry.Rectangle2D(50, 60, 200, 150);

        encrypt.encryptPart(original, area);

        File encryptedFile = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".bin"))
                .findFirst()
                .orElseThrow().toFile();

        ImageDecrypt decrypt = new ImageDecrypt(mandelbrotService, shuffler, imageUtils, fileManager, bobKeyManager);
        BufferedImage decrypted = decrypt.decryptImage(encryptedFile);

        assertImagesEqual(original, decrypted);
    }

    @Test
    @DisplayName("ИТ-5: Разные размеры изображений (640x480, 1024x768, 1920x1080)")
    void testDifferentImageSizes() throws Exception {
        byte[] secret = aliceKeyManager.getMasterSeedFromDH(bobAddress);
        int[][] sizes = {{640, 480}, {1024, 768}, {1920, 1080}};

        for (int[] size : sizes) {
            ImageEncrypt encrypt = new ImageEncrypt(mandelbrotService, shuffler, new NoOpSceneManager(), fileManager, imageUtils);
            encrypt.prepareSession(secret);

            BufferedImage original = createTestImage(size[0], size[1]);
            encrypt.encryptWhole(original);

            File encryptedFile = Files.list(tempDir)
                    .filter(p -> p.toString().endsWith(".bin"))
                    .findFirst()
                    .orElseThrow().toFile();

            ImageDecrypt decrypt = new ImageDecrypt(mandelbrotService, shuffler, imageUtils, fileManager, bobKeyManager);
            BufferedImage decrypted = decrypt.decryptImage(encryptedFile);

            assertImagesEqual(original, decrypted);

            // Очищаем temp для следующей итерации
            Files.list(tempDir).filter(p -> p.toString().endsWith(".bin")).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    @DisplayName("ИТ-6: Дешифрование повреждённого файла выбрасывает исключение")
    void testDecryptCorruptedFile() throws Exception {
        // Создаём заведомо битый файл (недостаточной длины)
        byte[] corrupted = new byte[100];
        new java.security.SecureRandom().nextBytes(corrupted);
        File badFile = tempDir.resolve("corrupted.bin").toFile();
        Files.write(badFile.toPath(), corrupted);

        ImageDecrypt decrypt = new ImageDecrypt(mandelbrotService, shuffler, imageUtils, fileManager, bobKeyManager);

        assertThrows(Exception.class, () -> decrypt.decryptImage(badFile),
                "Дешифрование битого файла должно выбрасывать исключение");
    }
}