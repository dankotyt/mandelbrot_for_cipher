package com.cipher.core.utils;

import com.cipher.core.dto.encryption.EncryptedData;
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
import java.nio.file.Files;

@Component
@RequiredArgsConstructor
public class TempFileManager {
    private static final Logger logger = LoggerFactory.getLogger(TempFileManager.class);

    private final DialogDisplayer displayer;
    private final SceneManager sceneManager;
    private final ImageUtils imageUtils;
    private final EncryptionDataSerializer serializer;

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
     * Сохраняет EncryptedData в файл .bin в temp папке
     */
    public File saveEncryptedData(EncryptedData data) throws IOException {
        createTempFolder();

        String fileName = "encrypted_" + System.currentTimeMillis() + ".bin";
        File outputFile = new File(getTempPath(), fileName);

        byte[] serializedData = serializer.serialize(data);
        Files.write(outputFile.toPath(), serializedData);

        logger.info("Зашифрованные данные сохранены в: {}", outputFile.getAbsolutePath());
        return outputFile;
    }

    /**
     * Загружает EncryptedData из файла .bin
     */
    public EncryptedData loadEncryptedData(File file) throws IOException {
        byte[] fileData = Files.readAllBytes(file.toPath());
        return serializer.deserialize(fileData);
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
    public File selectImageForEncrypt() {
        try {
            Stage primaryStage = sceneManager.getPrimaryStage();
            if (primaryStage == null) {
                logger.error("Primary stage is not set!");
                return null;
            }
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Выберите изображение для шифрования");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg")
            );
            return fileChooser.showOpenDialog(primaryStage);
        } catch (Exception e) {
            logger.error("Ошибка при выборе изображения: {}", e.getMessage(), e);
            displayer.showErrorDialog("Ошибка при выборе изображения: " + e.getMessage());
            return null;
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
     * Загружает изображение из файла в imageUtils
     */
    public void loadImageToMemory(File selectedFile) {
        try {
            BufferedImage image = ImageIO.read(selectedFile);
            if (image == null) {
                throw new IOException("Не удалось прочитать изображение");
            }
            imageUtils.setOriginalImage(image);

            // Сохраняем копию в temp
            saveBufferedImageToTemp(image, "input.png");

            logger.info("Изображение загружено в память: {}", selectedFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Ошибка при загрузке изображения: {}", e.getMessage());
            displayer.showErrorMessage("Ошибка при загрузке изображения: " + e.getMessage());
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
}