package com.cipher.core.utils;

import com.cipher.core.dto.encryption.EncryptionDataResult;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;

import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TempFileManager {
    private static final Logger logger = LoggerFactory.getLogger(TempFileManager.class);

    private final DialogDisplayer displayer;
    private final SceneManager sceneManager;
    private final ImageUtils imageUtils;

    private String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    public String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    public void selectOriginalImageFile() {
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

    public File selectKeyFileForDecrypt() {
        try {
            Stage primaryStage = sceneManager.getPrimaryStage();
            if (primaryStage == null) {
                logger.error("Primary stage is not set!");
                return null;
            }
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Выберите файл-ключ для расшифрования");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Файлы ключей", "*.bin"));
            return fileChooser.showOpenDialog(primaryStage);
        } catch (Exception e) {
            logger.error("Ошибка при выборе ключа для дешифрования: {}", e.getMessage(), e);
            displayer.showErrorDialog("Ошибка при выборе файла: " + e.getMessage());
            return null;
        }
    }

    public void createTempFolder() {
        String tempPath = getTempPath();
        File tempDir = new File(tempPath);

        if (!tempDir.exists()) {
            boolean created = tempDir.mkdir();
            if (!created) {
                logger.error("Не удалось создать временную директорию: {}", tempPath);
                throw new RuntimeException("Не удалось создать временную директорию");
            }
        }
    }

    private boolean deleteFolder(File folder) {
        boolean success = true;

        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    success &= deleteFolder(file);
                }
            }
        }

        boolean deleted = folder.delete();
        if (!deleted) {
            logger.warn("Не удалось удалить: {}", folder.getAbsolutePath());
        }

        return success && deleted;
    }

//    public void cleanupTemp() {
//        try {
//            String tempPath = getTempPath();
//            File tempDir = new File(tempPath);
//
//            if (tempDir.exists()) {
//                boolean deleted = deleteFolder(tempDir);
//                if (!deleted) {
//                    logger.warn("Не удалось полностью очистить temp директорию");
//                }
//
//                // Создаем заново
//                boolean created = tempDir.mkdirs();
//                if (!created) {
//                    logger.error("Не удалось создать temp директорию: {}", tempPath);
//                }
//            } else {
//                // Создаем если не существует
//                boolean created = tempDir.mkdirs();
//                if (!created) {
//                    logger.error("Не удалось создать temp директорию: {}", tempPath);
//                }
//            }
//        } catch (Exception e) {
//            logger.error("Ошибка при очистке temp директории: {}", e.getMessage(), e);
//        }
//    }

    public void saveInputImageToTemp(File selectedFile) {
        createTempFolder();
        String tempPath = getTempPath();
        String tempFilePath = tempPath + "input.png";
        File tempFile = new File(tempFilePath);

        try {
            try (InputStream inputStream = new FileInputStream(selectedFile);
                 OutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            logger.info("Изображение сохранено в папку temp: {}", tempFile.getAbsolutePath());

            if (tempFile.exists() && tempFile.canRead()) {
                logger.info("Файл существует: {}", tempFile.getAbsolutePath());
            } else {
                logger.error("Файл не существует или не доступен для чтения: {}", tempFile.getAbsolutePath());
                displayer.showErrorMessage("Файл не существует или не доступен для чтения: " + tempFile.getAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Ошибка при сохранении изображения: {}", e.getMessage());
            displayer.showErrorMessage("Ошибка при сохранении изображения: " + e.getMessage());
        }
    }

    public ImageView loadInputImageFromTemp() {
        try {
            String tempPath = getTempPath();
            String tempFilePath = tempPath + "input.png";
            File tempFile = new File(tempFilePath);

            if (!tempFile.exists() || !tempFile.canRead()) {
                logger.error("Файл изображения не найден: {}", tempFilePath);
                displayer.showErrorDialog("Файл изображения не найден: " + tempFilePath);
                return null;
            }

            return new ImageView(new Image(tempFile.toURI().toString()));
        } catch (Exception e) {
            return null;
        }
    }

    public BufferedImage loadBufferedImageFromTemp(String filePath) {
        String tempPath = getTempPath();
        String tempFilePath = tempPath + filePath;
        File tempFile = new File(tempFilePath);
        if (!tempFile.exists() || !tempFile.canRead()) {
            logger.error("Файл изображения не найден: {}", filePath);
            displayer.showErrorDialog("Файл изображения не найден: " + filePath);
            return null;
        }

        try {
            return ImageIO.read(tempFile);
        } catch (IOException e) {
            logger.error("Ошибка при загрузке изображения: {}", e.getMessage());
            displayer.showErrorDialog("Ошибка при загрузке изображения: " + e.getMessage());
            return null;
        }
    }

    public Image loadImageFromTemp(String filePath) {
        String tempPath = getTempPath();
        String tempFilePath = tempPath + filePath;
        File tempFile = new File(tempFilePath);
        if (!tempFile.exists() || !tempFile.canRead()) {
            logger.error("Файл изображения не найден: {}", filePath);
            displayer.showErrorMessage("Файл изображения не найден: " + filePath);
            return null;
        }

        try {
            return new Image(tempFile.toURI().toString());
        } catch (Exception e) {
            logger.error("Ошибка при загрузке изображения: {}", e.getMessage());
            displayer.showErrorMessage("Ошибка при загрузке изображения: " + e.getMessage());
            return null;
        }
    }

    public void saveMandelbrotToTemp(BufferedImage image) {
        String tempPath = getTempPath();
        String tempFilePath = tempPath + "mandelbrot.png";
        File tempFile = new File(tempFilePath);

        try {
            ImageIO.write(image, "png", tempFile);
            logger.info("Изображение сохранено в папку temp: {}", tempFile.getAbsolutePath());

            if (tempFile.exists() && tempFile.canRead()) {
                logger.info("Файл существует: {}", tempFile.getAbsolutePath());
            } else {
                logger.error("Файл не существует или не доступен для чтения: {}", tempFile.getAbsolutePath());
                displayer.showErrorMessage("Файл не существует или не доступен для чтения: " + tempFile.getAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Ошибка при сохранении изображения: {}", e.getMessage());
            displayer.showErrorMessage("Ошибка при сохранении изображения: " + e.getMessage());
        }
    }

    public void saveBufferedImageToTemp(BufferedImage image, String filename) {
        String tempPath = getTempPath();
        String tempFilePath = tempPath + filename;
        File tempFile = new File(tempFilePath);

        try {
            ImageIO.write(image, "png", tempFile);
            logger.info("Изображение сохранено в папку temp: {}", tempFile.getAbsolutePath());

            if (tempFile.exists() && tempFile.canRead()) {
                logger.info("Файл существует: {}", tempFile.getAbsolutePath());
            } else {
                logger.error("Файл не существует или не доступен для чтения: {}", tempFile.getAbsolutePath());
                displayer.showErrorMessage("Файл не существует или не доступен для чтения: " + tempFile.getAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Ошибка при сохранении изображения: {}", e.getMessage());
            displayer.showErrorMessage("Ошибка при сохранении изображения: " + e.getMessage());
        }
    }

    public Image loadImageResource(String resourcePath) {
        try {
            InputStream inputStream = getClass().getResourceAsStream(resourcePath);
            if (inputStream == null) {
                displayer.showErrorAlert("Ошибка ресурса", "Ресурс не найден: " + resourcePath);
                return null;
            }
            return new Image(inputStream);
        } catch (Exception e) {
            displayer.showErrorAlert("Ошибка загрузки", "Ошибка загрузки изображения: " + e.getMessage());
            return null;
        }
    }

    public void saveEncryptedData(BufferedImage encryptedImage, EncryptionDataResult encryptedParams) {
        try {
            // 1. Сохраняем изображение
            String imagePath = saveEncryptedImage(encryptedImage);

            // 2. Сохраняем параметры
            if (imagePath != null) {
                saveEncryptedParams(encryptedParams, imagePath);
            }

        } catch (Exception e) {
            displayer.showErrorAlert("Ошибка сохранения", "Ошибка при сохранении данных: " + e.getMessage());
        }
    }

    /**
     * Сохраняет зашифрованное изображение
     */
    public String saveEncryptedImage(BufferedImage encryptedImage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить зашифрованное изображение");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        fileChooser.setInitialFileName("encrypted_image.png");

        try {
            Stage primaryStage = sceneManager.getPrimaryStage();
            File fileToSave = fileChooser.showSaveDialog(primaryStage);

            if (fileToSave != null) {
                String imageFilePath = fileToSave.getAbsolutePath();

                if (!imageFilePath.toLowerCase().endsWith(".png")) {
                    imageFilePath += ".png";
                }

                ImageIO.write(encryptedImage, "png", new File(imageFilePath));
                logger.info("Изображение сохранено: {}", imageFilePath);

                return imageFilePath;
            }
        } catch (IOException e) {
            displayer.showErrorDialog("Ошибка при сохранении изображения: " + e.getMessage());
        } catch (Exception e) {
            displayer.showErrorAlert("Ошибка", "Ошибка сохранения изображения: " + e.getMessage());
        }

        return null;
    }

    /**
     * Сохраняет зашифрованные параметры
     */
    public void saveEncryptedParams(EncryptionDataResult encryptedParams, String imagePath) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить файл с параметрами шифрования");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Файл-ключ", "*.bin"));

        // Предлагаем имя на основе имени изображения
        String imageName = new File(imagePath).getName().replace(".png", "");
        fileChooser.setInitialFileName(imageName + "_key.bin");

        try {
            Stage primaryStage = sceneManager.getPrimaryStage();
            File fileToSave = fileChooser.showSaveDialog(primaryStage);

            if (fileToSave != null) {
                String paramsFilePath = fileToSave.getAbsolutePath();

                if (!paramsFilePath.toLowerCase().endsWith(".bin")) {
                    paramsFilePath += ".bin";
                }

                // Сохраняем бинарные данные
                saveBinaryParams(encryptedParams, paramsFilePath);

                logger.info("Файл с параметрами сохранен: {}", paramsFilePath);
                displayer.showSuccessDialog(
                        "Данные успешно сохранены:\n" +
                                "Изображение: " + imagePath + "\n" +
                                "Файл-ключ: " + paramsFilePath
                );
            }
        } catch (Exception e) {
            logger.error("Ошибка при сохранении файла с параметрами: {}", e.getMessage());
            displayer.showErrorDialog("Ошибка при сохранении файл-ключа: " + e.getMessage());
        }
    }

    /**
     * Сохраняет зашифрованные параметры в бинарный файл
     */
    private void saveBinaryParams(EncryptionDataResult encryptedParams, String filePath) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(filePath))) {
            // Записываем IV (12 bytes)
            dos.writeInt(encryptedParams.iv().length);
            dos.write(encryptedParams.iv());

            // Записываем Salt (16 bytes)
            dos.writeInt(encryptedParams.salt().length);
            dos.write(encryptedParams.salt());

            // Записываем зашифрованные данные
            dos.writeInt(encryptedParams.encryptedData().length);
            dos.write(encryptedParams.encryptedData());
        }
    }

    /**
     * Загружает зашифрованные параметры из файла
     */
    public EncryptionDataResult loadEncryptedParams(File paramsFile) throws IOException {
        try (DataInputStream dis = new DataInputStream(new FileInputStream(paramsFile))) {
            // Читаем IV
            int ivLength = dis.readInt();
            byte[] iv = new byte[ivLength];
            dis.readFully(iv);

            // Читаем Salt
            int saltLength = dis.readInt();
            byte[] salt = new byte[saltLength];
            dis.readFully(salt);

            // Читаем зашифрованные данные
            int dataLength = dis.readInt();
            byte[] encryptedData = new byte[dataLength];
            dis.readFully(encryptedData);

            return new EncryptionDataResult(encryptedData, iv, salt);
        }
    }

    public void saveDecryptedImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить расшифрованное изображение");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG изображение", "*.png"));

        try {
            Stage primaryStage = sceneManager.getPrimaryStage();
            File fileToSave = fileChooser.showSaveDialog(primaryStage);

            if (fileToSave != null) {
                String filePath = fileToSave.getAbsolutePath();

                if (!filePath.toLowerCase().endsWith(".png")) {
                    filePath += ".png";
                }

                try {
                    BufferedImage decryptedImage = ImageIO.read(new File(getTempPath() + "decrypted_image.png"));
                    ImageIO.write(decryptedImage, "png", new File(filePath));

                    // Дублируем изображение в temp
                    String resourcesPath = getTempPath() + "decrypted_image.png";
                    ImageIO.write(decryptedImage, "png", new File(resourcesPath));

                    displayer.showSuccessDialog("Изображение успешно сохранено в: " + filePath);
                    logger.info("Изображение сохранено в: {}", filePath);

                } catch (IOException e) {
                    logger.error("Ошибка при сохранении изображения: {}", e.getMessage());
                    displayer.showErrorDialog("Ошибка при сохранении изображения: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            displayer.showErrorAlert("Ошибка сохранения", "Ошибка сохранения изображения: " + e.getMessage());
        }
    }

    private void saveKeyDecoder(String imageFilePath) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить key_decoder.bin");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Файл-ключ", "*.bin"));

        try {
            Stage primaryStage = sceneManager.getPrimaryStage();
            File fileToSave = fileChooser.showSaveDialog(primaryStage);

            if (fileToSave != null) {
                String keyDecoderFilePath = fileToSave.getAbsolutePath();

                if (!keyDecoderFilePath.toLowerCase().endsWith(".bin")) {
                    keyDecoderFilePath += ".bin";
                }

                try {
                    String resourcesPath = getTempPath() + "key_decoder.bin";
                    Path sourcePath = Paths.get(resourcesPath);
                    Path destinationPath = Paths.get(keyDecoderFilePath);

                    Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);

                    logger.info("key_decoder.bin сохранен в: {}", keyDecoderFilePath);
                    displayer.showSuccessDialog("Изображение и файл-ключ успешно сохранены:\n" +
                            "Изображение: " + imageFilePath + "\n" +
                            "Файл-ключ: " + keyDecoderFilePath);

                } catch (Exception e) {
                    logger.error("Ошибка при сохранении key_decoder.bin: {}", e.getMessage());
                    displayer.showErrorDialog("Ошибка при сохранении файл-ключа: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            displayer.showErrorAlert("Ошибка сохранения", "Ошибка сохранения ключа: " + e.getMessage());

        }
    }
}