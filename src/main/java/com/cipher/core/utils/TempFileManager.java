package com.cipher.core.utils;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TempFileManager {
    private static final Logger logger = LoggerFactory.getLogger(TempFileManager.class);
    private final DialogDisplayer displayer = new DialogDisplayer();

    private String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    public TempFileManager() {
        createTempFolder();
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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            boolean deleted = deleteFolder(tempDir);
            if (!deleted) {
                logger.warn("Не удалось полностью удалить временную директорию: {}", tempPath);
            }
        }));
    }

    public boolean deleteFolder(File folder) {
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

    public void saveInputImageToTemp(File selectedFile) {
        String tempPath = getTempPath();

        createTempFolder();

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
        String tempPath = getTempPath();
        String tempFilePath = tempPath + "input.png";
        File tempFile = new File(tempFilePath);

        if (!tempFile.exists() || !tempFile.canRead()) {
            logger.error("Файл изображения не найден: {}", tempFilePath);
            displayer.showErrorDialog("Файл изображения не найден: " + tempFilePath);
            return null;
        }

        Image image = new Image(tempFile.toURI().toString());
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(640);
        imageView.setFitHeight(480);
        imageView.setTranslateX(150); // Выглядывание на 50px

        return imageView;
    }

    public BufferedImage loadBufferedImageFromTemp(String filePath) {
        File tempFile = new File(filePath);
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
        File tempFile = new File(filePath);
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

        createTempFolder();

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
}