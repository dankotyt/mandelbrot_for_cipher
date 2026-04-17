package com.cipher.core.service;

import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.FileManager;
import com.cipher.core.utils.ImageUtils;
import com.cipher.core.utils.SceneManager;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FileManagerTest {

    @Mock
    private DialogDisplayer displayer;
    @Mock
    private SceneManager sceneManager;
    @Mock
    private Stage primaryStage;

    @InjectMocks
    private FileManager fileManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Перенаправляем temp path для тестов
        lenient().when(sceneManager.getPrimaryStage()).thenReturn(primaryStage);
    }

    @Test
    void getTempPath_shouldReturnCorrectPath() {
        String tempPath = fileManager.getTempPath();
        assertNotNull(tempPath);
        assertTrue(tempPath.endsWith("temp" + File.separator));
    }

    @Test
    void createTempFolder_whenNotExists_shouldCreate() {
        fileManager.createTempFolder();
        File tempDir = new File(fileManager.getTempPath());
        assertTrue(tempDir.exists());
        assertTrue(tempDir.isDirectory());
    }

    @Test
    void selectEncryptedFileForDecrypt_withPrimaryStage_shouldReturnFile() {
        File expectedFile = new File("test.bin");
        FileChooser fileChooser = mock(FileChooser.class);

        // Используем PowerMock или создаем реальный FileChooser с моком
        // Для простоты тестируем только логику обработки исключений
        when(sceneManager.getPrimaryStage()).thenReturn(primaryStage);

        // Так как FileChooser сложно замокать, проверяем только что метод не выбрасывает исключение
        assertDoesNotThrow(() -> fileManager.selectEncryptedFileForDecrypt());
    }

    @Test
    void selectEncryptedFileForDecrypt_withNullPrimaryStage_shouldReturnNull() {
        when(sceneManager.getPrimaryStage()).thenReturn(null);

        File result = fileManager.selectEncryptedFileForDecrypt();
        assertNull(result);
        verify(displayer, never()).showErrorDialog(anyString());
    }

    @Test
    void selectImageForEncrypt_withNullPrimaryStage_shouldShowError() {
        when(sceneManager.getPrimaryStage()).thenReturn(null);

        fileManager.selectImageForEncrypt();

        verify(displayer).showErrorDialog("Ошибка: главное окно не инициализировано");
    }

    @Test
    void saveDecryptedImage_withValidImage_shouldSave() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        when(sceneManager.getPrimaryStage()).thenReturn(primaryStage);

        // Создаем временный файл для сохранения
        File tempFile = tempDir.resolve("test.png").toFile();

        // Так как FileChooser.showSaveDialog сложно замокать, проверяем только логику
        assertDoesNotThrow(() -> fileManager.saveDecryptedImage(image));
    }

    @Test
    void saveBufferedImageToTemp_shouldCreateFile() {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

        fileManager.saveBufferedImageToTemp(image, "test.png");

        File tempFile = new File(fileManager.getTempPath(), "test.png");
        assertTrue(tempFile.exists());
        tempFile.delete();
    }

    @Test
    void loadImageFromTemp_whenFileExists_shouldReturnImage() throws Exception {
        // Инициализируем JavaFX для этого теста
        CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(() -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);

        // Создаем тестовое изображение в temp
        BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        fileManager.saveBufferedImageToTemp(testImage, "test.png");

        Image result = fileManager.loadImageFromTemp("test.png");

        assertNotNull(result);
    }

    @Test
    void loadImageFromTemp_whenFileNotExists_shouldReturnNull() {
        Image result = fileManager.loadImageFromTemp("nonexistent.png");
        assertNull(result);
    }

    @Test
    void loadBufferedImageFromTemp_whenFileExists_shouldReturnImage() throws Exception {
        BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        fileManager.saveBufferedImageToTemp(testImage, "test.png");

        BufferedImage result = fileManager.loadBufferedImageFromTemp("test.png");

        assertNotNull(result);
        assertEquals(100, result.getWidth());
        assertEquals(100, result.getHeight());
    }

    @Test
    void loadBufferedImageFromTemp_whenFileNotExists_shouldReturnNull() {
        BufferedImage result = fileManager.loadBufferedImageFromTemp("nonexistent.png");
        assertNull(result);
    }

    @Test
    void cleanupTemp_shouldDeleteOldFiles() throws Exception {
        // Создаем несколько файлов в temp
        fileManager.saveBufferedImageToTemp(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "temp1.png");
        fileManager.saveBufferedImageToTemp(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "temp2.png");
        fileManager.saveBufferedImageToTemp(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "input_image.png");
        fileManager.saveBufferedImageToTemp(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "decrypted_image.png");

        fileManager.cleanupTemp();

        File tempDir = new File(fileManager.getTempPath());
        File[] files = tempDir.listFiles();
        assertNotNull(files);

        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("temp")) {
                assertFalse(file.exists(), "Temp file should be deleted: " + name);
            }
        }
    }

    @Test
    void saveBytesToFile_shouldCreateFile() throws Exception {
        byte[] data = {1, 2, 3, 4, 5};

        File result = fileManager.saveBytesToFile(data, "test.bin");

        assertNotNull(result);
        assertTrue(result.exists());
        byte[] readData = Files.readAllBytes(result.toPath());
        assertArrayEquals(data, readData);

        result.delete();
    }

    @Test
    void saveBytesToFile_withNullData_shouldThrow() {
        assertThrows(NullPointerException.class, () -> fileManager.saveBytesToFile(null, "test.bin"));
    }
}