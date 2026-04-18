package com.cipher.core.utils;

import jakarta.annotation.PreDestroy;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;

@Component
@RequiredArgsConstructor
public class FileManager {
    private static final Logger logger = LoggerFactory.getLogger(FileManager.class);

    private final DialogDisplayer displayer;
    private final SceneManager sceneManager;
    private final ImageUtils imageUtils;

    private String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    public String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    public void createTempFolder() {
        String tempPath = getTempPath();
        File tempDir = new File(tempPath);

        if (!tempDir.exists()) {
            boolean created = tempDir.mkdirs();
            if (!created) {
                logger.error("Не удалось создать временную директорию: {}", tempPath);
                throw new RuntimeException("Не удалось создать временную директорию");
            }
        }
    }

    /**
     * Выбор файла .bin для дешифрования
     */
    public File selectEncryptedFileForDecrypt() {
        try {
            Stage primaryStage = sceneManager.getPrimaryStage();
            if (primaryStage == null) {
                logger.error("Primary stage is not set!");
                return null;
            }
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Выберите зашифрованный файл для расшифрования");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Зашифрованные файлы", "*.bin")
            );
            return fileChooser.showOpenDialog(primaryStage);
        } catch (Exception e) {
            logger.error("Ошибка при выборе файла: {}", e.getMessage(), e);
            displayer.showErrorDialog("Ошибка при выборе файла: " + e.getMessage());
            return null;
        }
    }

    /**
     * Выбор изображения для шифрования
     */
    public void selectImageForEncrypt() {
        try {
            Stage primaryStage = sceneManager.getPrimaryStage();
            if (primaryStage == null) {
                logger.error("Primary stage is not set!");
                displayer.showErrorDialog("Ошибка: главное окно не инициализировано");
            }
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Выберите изображение для шифрования");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg"));

            File selectedFile = fileChooser.showOpenDialog(primaryStage);

            if (selectedFile != null) {
                saveInputImageToMemory(selectedFile);
                logger.info("Файл выбран: {}", selectedFile.getAbsolutePath());
            } else {
                logger.info("Файл не выбран.");
            }
        } catch (Exception e) {
            logger.error("Ошибка при выборе файла для шифрования: {}", e.getMessage(), e);
            displayer.showErrorDialog("Ошибка при выборе файла: " + e.getMessage());
        }
    }

    private void saveInputImageToMemory(File selectedFile) {
        try {
            BufferedImage image = ImageIO.read(selectedFile);
            if (image == null) {
                throw new IOException("Не удалось прочитать изображение");
            }
            imageUtils.setOriginalImage(image);
        } catch (IOException e) {
            logger.error("Ошибка при загрузке изображения: {}", e.getMessage());
            displayer.showErrorMessage("Ошибка при загрузке изображения: " + e.getMessage());
        }
    }

    /**
     * Сохраняет расшифрованное изображение
     */
    public void saveDecryptedImage(BufferedImage image) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить расшифрованное изображение");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG изображение", "*.png")
        );
        fileChooser.setInitialFileName("decrypted_image.png");

        try {
            Stage primaryStage = sceneManager.getPrimaryStage();
            File fileToSave = fileChooser.showSaveDialog(primaryStage);

            if (fileToSave != null) {
                String filePath = fileToSave.getAbsolutePath();
                if (!filePath.toLowerCase().endsWith(".png")) {
                    filePath += ".png";
                }

                ImageIO.write(image, "png", new File(filePath));

                // Сохраняем копию в temp для предпросмотра
                ImageIO.write(image, "png", new File(getTempPath() + "decrypted_image.png"));

                displayer.showSuccessDialog("Изображение успешно сохранено в: " + filePath);
                logger.info("Изображение сохранено в: {}", filePath);
            }
        } catch (IOException e) {
            logger.error("Ошибка при сохранении изображения: {}", e.getMessage());
            displayer.showErrorDialog("Ошибка при сохранении изображения: " + e.getMessage());
        } catch (Exception e) {
            displayer.showErrorAlert("Ошибка сохранения", "Ошибка сохранения изображения: " + e.getMessage());
        }
    }

    /**
     * Сохраняет BufferedImage в temp папку
     */
    public void saveBufferedImageToTemp(BufferedImage image, String filename) {
        createTempFolder();
        File tempFile = new File(getTempPath(), filename);
        try {
            ImageIO.write(image, "png", tempFile);
            logger.info("Изображение сохранено в temp: {}", tempFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Ошибка при сохранении изображения в temp: {}", e.getMessage());
        }
    }

    /**
     * Загружает изображение из temp папки
     */
    public Image loadImageFromTemp(String filename) {
        File tempFile = new File(getTempPath(), filename);
        if (!tempFile.exists()) {
            logger.warn("Файл не найден в temp: {}", filename);
            return null;
        }
        return new Image(tempFile.toURI().toString());
    }

    /**
     * Загружает BufferedImage из temp папки
     */
    public BufferedImage loadBufferedImageFromTemp(String filename) {
        File tempFile = new File(getTempPath(), filename);
        if (!tempFile.exists()) {
            logger.warn("Файл не найден в temp: {}", filename);
            return null;
        }
        try {
            return ImageIO.read(tempFile);
        } catch (IOException e) {
            logger.error("Ошибка при загрузке BufferedImage из temp: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Очистка temp папки (кроме текущих файлов)
     */
    @PreDestroy
    public void cleanupTemp() {
        try {
            File tempDir = new File(getTempPath());
            if (tempDir.exists() && tempDir.isDirectory()) {
                File[] files = tempDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        // Не удаляем файлы, которые могут понадобиться в текущей сессии
                        if (!file.getName().startsWith("input") &&
                                !file.getName().startsWith("decrypted")) {
                            file.delete();
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при очистке temp папки: {}", e.getMessage());
        }
    }

    public File saveBytesToFile(byte[] data, String filename) throws IOException {
        createTempFolder(); // создаёт папку temp, если её нет
        File file = new File(getTempPath(), filename);
        Files.write(file.toPath(), data);
        logger.info("Saved encrypted file: {}", file.getAbsolutePath());
        return file;
    }

    /**
     * Сохраняет зашифрованное изображение с метаданными
     */
    public File saveEncryptedImage(byte[] sessionSalt, int attemptCount,
                                    byte[] imageBytes, int originalWidth, int originalHeight,
                                    int startX, int startY, int areaWidth, int areaHeight) throws IOException {

        // Формат: соль(16) + attempts(4) + координаты и размеры(6 int) + данные
        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 24 + imageBytes.length);
        buffer.put(sessionSalt);
        buffer.putInt(attemptCount);
        buffer.putInt(startX);
        buffer.putInt(startY);
        buffer.putInt(areaWidth);
        buffer.putInt(areaHeight);
        buffer.putInt(originalWidth);
        buffer.putInt(originalHeight);
        buffer.put(imageBytes);

        File outFile = saveBytesToFile(buffer.array(),
                "encrypted_" + System.currentTimeMillis() + ".bin");
        logger.info("Зашифрованный файл сохранён: {}, размер данных {} байт",
                outFile.getName(), imageBytes.length);
        return outFile;
    }
}