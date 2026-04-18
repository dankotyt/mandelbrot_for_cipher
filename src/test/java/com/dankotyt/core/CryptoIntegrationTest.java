package com.dankotyt.core;

import com.dankotyt.core.dto.encryption.EncryptedData;
import com.dankotyt.core.service.encryption.*;
import com.dankotyt.core.service.encryption.impl.*;
import com.dankotyt.core.service.network.CryptoKeyManager;
import com.dankotyt.core.service.network.impl.ECDHCryptoKeyManagerImpl;
import com.dankotyt.core.utils.FileManager;
import com.dankotyt.core.utils.ImageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    private SegmentShuffler shuffler;
    private CryptoKeyManager aliceKeyManager;
    private CryptoKeyManager bobKeyManager;
    private InetAddress bobAddress;
    private ECDHService ecdhService;

    // Заглушки UI (минимальные, без моков)
    private static class NoOpSceneManager extends com.dankotyt.core.utils.SceneManager {
        NoOpSceneManager() { super(null, null); }
        @Override public void showEncryptFinalPanel(BufferedImage image, File file) {}
    }
    private static class NoOpDialogDisplayer extends com.dankotyt.core.utils.DialogDisplayer {
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
        shuffler = new ImageSegmentShufflerImpl();

        // Создаём ECDH сервис
        ecdhService = new ECDHServiceImpl();

        // Инициализируем менеджеры ключей с внедрённым сервисом
        aliceKeyManager = new ECDHCryptoKeyManagerImpl(ecdhService);
        bobKeyManager = new ECDHCryptoKeyManagerImpl(ecdhService);

        // Явно вызываем init() для запуска серверов инвалидации (заменяет @PostConstruct)
        ((ECDHCryptoKeyManagerImpl) aliceKeyManager).init();
        ((ECDHCryptoKeyManagerImpl) bobKeyManager).init();

        // Получаем ключевые пары
        var aliceKeys = aliceKeyManager.getCurrentKeys();
        var bobKeys = bobKeyManager.getCurrentKeys();

        // Выполняем обмен секретом через ECDHService
        ecdhService.computeSharedSecret(aliceKeys, ecdhService.serializePublicKey(bobKeys));
        ecdhService.computeSharedSecret(bobKeys, ecdhService.serializePublicKey(aliceKeys));

        // Регистрируем соединения в менеджерах
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

    // ==================== ИНТЕГРАЦИОННЫЕ ТЕСТЫ ====================

    @Test
    @DisplayName("ИТ-1: generateNextFractal возвращает фрактал и сохраняет его в поле")
    void testGenerateNextFractalReturnsAndSavesFractal() throws Exception {
        byte[] secret = aliceKeyManager.getMasterSeedFromDH(bobAddress);
        ImageEncryptorImpl encrypt = new ImageEncryptorImpl(mandelbrotService, shuffler, imageUtils);
        encrypt.prepareSession(secret);

        BufferedImage image = createTestImage(200, 200);
        BufferedImage generatedFractal = encrypt.generateNextFractal(image.getWidth(), image.getHeight());

        assertNotNull(generatedFractal, "generateNextFractal должен возвращать не-null фрактал");

        Field fractalField = ImageEncryptorImpl.class.getDeclaredField("fractal");
        fractalField.setAccessible(true);
        BufferedImage savedFractal = (BufferedImage) fractalField.get(encrypt);

        assertNotNull(savedFractal, "Фрактал должен сохраняться в поле класса");
        assertSame(generatedFractal, savedFractal, "Сохранённый фрактал должен быть тем же объектом");

        encrypt.encryptWhole(image);
        BufferedImage usedFractal = (BufferedImage) fractalField.get(encrypt);
        assertSame(savedFractal, usedFractal, "При шифровании должен использоваться тот же фрактал");
    }

    @Test
    @DisplayName("ИТ-2: encryptWhole успешно шифрует изображение (автогенерация фрактала)")
    void testEncryptWholeCompletesSuccessfully() throws Exception {
        byte[] secret = aliceKeyManager.getMasterSeedFromDH(bobAddress);
        ImageEncryptorImpl encrypt = new ImageEncryptorImpl(mandelbrotService, shuffler, imageUtils);
        encrypt.prepareSession(secret);

        BufferedImage image = createTestImage(200, 200);
        encrypt.generateNextFractal(image.getWidth(), image.getHeight());

        assertDoesNotThrow(() -> encrypt.encryptWhole(image),
                "encryptWhole должен завершаться без исключений");
    }

    @Test
    @DisplayName("ИТ-3: Сквозной сценарий шифрование → дешифрование")
    void testEndToEndEncryptDecrypt() throws Exception {
        byte[] aliceSecret = aliceKeyManager.getMasterSeedFromDH(bobAddress);

        ImageEncryptorImpl encrypt = new ImageEncryptorImpl(mandelbrotService, shuffler, imageUtils);
        encrypt.prepareSession(aliceSecret);

        BufferedImage original = createTestImage(300, 300);
        encrypt.generateNextFractal(original.getWidth(), original.getHeight());
        EncryptedData encryptedData = encrypt.encryptWhole(original);

        // Сохраняем файл через FileManager
        File encryptedFile = fileManager.saveEncryptedImage(
                encryptedData.sessionSalt(),
                encryptedData.attemptCount(),
                encryptedData.imageBytes(),
                encryptedData.originalWidth(),
                encryptedData.originalHeight(),
                encryptedData.startX(),
                encryptedData.startY(),
                encryptedData.areaWidth(),
                encryptedData.areaHeight()
        );

        ImageDecryptorImpl decrypt = new ImageDecryptorImpl(mandelbrotService, shuffler, imageUtils, bobKeyManager);
        BufferedImage decrypted = decrypt.decryptImage(encryptedFile);

        assertImagesEqual(original, decrypted);
    }

    @Test
    @DisplayName("ИТ-4: Частичное шифрование области и дешифрование")
    void testPartialEncryptDecrypt() throws Exception {
        byte[] secret = aliceKeyManager.getMasterSeedFromDH(bobAddress);
        ImageEncryptorImpl encrypt = new ImageEncryptorImpl(mandelbrotService, shuffler, imageUtils);
        encrypt.prepareSession(secret);

        BufferedImage original = createTestImage(400, 300);
        javafx.geometry.Rectangle2D area = new javafx.geometry.Rectangle2D(50, 60, 200, 150);

        encrypt.generateNextFractal(original.getWidth(), original.getHeight());
        EncryptedData encryptedData = encrypt.encryptPart(original, area);

        File encryptedFile = fileManager.saveEncryptedImage(
                encryptedData.sessionSalt(),
                encryptedData.attemptCount(),
                encryptedData.imageBytes(),
                encryptedData.originalWidth(),
                encryptedData.originalHeight(),
                encryptedData.startX(),
                encryptedData.startY(),
                encryptedData.areaWidth(),
                encryptedData.areaHeight()
        );

        ImageDecryptorImpl decrypt = new ImageDecryptorImpl(mandelbrotService, shuffler, imageUtils, bobKeyManager);
        BufferedImage decrypted = decrypt.decryptImage(encryptedFile);

        assertImagesEqual(original, decrypted);
    }

    @Test
    @DisplayName("ИТ-5: Разные размеры изображений (640x480, 1024x768, 1920x1080)")
    void testDifferentImageSizes() throws Exception {
        byte[] secret = aliceKeyManager.getMasterSeedFromDH(bobAddress);
        int[][] sizes = {{640, 480}, {1024, 768}, {1920, 1080}};

        for (int[] size : sizes) {
            ImageEncryptorImpl encrypt = new ImageEncryptorImpl(mandelbrotService, shuffler, imageUtils);
            encrypt.prepareSession(secret);

            BufferedImage original = createTestImage(size[0], size[1]);
            encrypt.generateNextFractal(original.getWidth(), original.getHeight());
            EncryptedData encryptedData = encrypt.encryptWhole(original);

            File encryptedFile = fileManager.saveEncryptedImage(
                    encryptedData.sessionSalt(),
                    encryptedData.attemptCount(),
                    encryptedData.imageBytes(),
                    encryptedData.originalWidth(),
                    encryptedData.originalHeight(),
                    encryptedData.startX(),
                    encryptedData.startY(),
                    encryptedData.areaWidth(),
                    encryptedData.areaHeight()
            );

            ImageDecryptorImpl decrypt = new ImageDecryptorImpl(mandelbrotService, shuffler, imageUtils, bobKeyManager);
            BufferedImage decrypted = decrypt.decryptImage(encryptedFile);

            assertImagesEqual(original, decrypted);

            // Очищаем temp
            Files.list(tempDir).filter(p -> p.toString().endsWith(".bin")).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    @DisplayName("ИТ-6: Дешифрование повреждённого файла выбрасывает исключение")
    void testDecryptCorruptedFile() throws Exception {
        // Создаём заведомо битый файл (недостаточной длины)
        byte[] corrupted = new byte[100];
        new SecureRandom().nextBytes(corrupted);
        File badFile = tempDir.resolve("corrupted.bin").toFile();
        Files.write(badFile.toPath(), corrupted);

        ImageDecryptorImpl decrypt = new ImageDecryptorImpl(mandelbrotService, shuffler, imageUtils, bobKeyManager);

        assertThrows(Exception.class, () -> decrypt.decryptImage(badFile),
                "Дешифрование битого файла должно выбрасывать исключение");
    }
}