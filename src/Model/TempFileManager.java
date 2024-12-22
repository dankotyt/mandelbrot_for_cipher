package Model;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

public class TempFileManager {

    private final String tempPath;

    public TempFileManager(String tempPath) {
        this.tempPath = tempPath;
        createTempFolder();
    }

    // Создание временной папки
    private void createTempFolder() {
        File tempDir = new File(tempPath);

        // Создаем папку temp, если она не существует
        if (!tempDir.exists()) {
            tempDir.mkdir();
        }

        // Добавляем хук для удаления папки при завершении программы
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            deleteFolder(tempDir);
        }));
    }

    // Метод для рекурсивного удаления папки
    private void deleteFolder(File folder) {
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteFolder(file); // Рекурсивно удаляем файлы в папке
                }
            }
        }
        folder.delete(); // Удаляем папку или файл
    }

    // Сохранение изображения в папку temp
    public void saveInputImageToTemp(File selectedFile) {
        String tempFilePath = tempPath + "input.png";
        File tempFile = new File(tempFilePath);

        try (InputStream inputStream = new FileInputStream(selectedFile);
             OutputStream outputStream = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            System.out.println("Изображение сохранено в папку temp: " + tempFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении изображения: " + e.getMessage());
        }
    }

    // Загрузка изображения из папки temp
    public ImageView loadInputImageFromTemp() {
        String tempFilePath = tempPath + "input.png";
        File tempFile = new File(tempFilePath);

        if (!tempFile.exists() || !tempFile.canRead()) {
            System.err.println("Файл изображения не найден: " + tempFilePath);
            return null;
        }

        Image image = new Image(tempFile.toURI().toString());
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(633);
        imageView.setFitHeight(436);
        imageView.setTranslateX(150);

        return imageView;
    }

    // Загрузка BufferedImage из папки temp
    public BufferedImage loadBufferedImageFromTemp(String filePath) {
        File tempFile = new File(tempPath + filePath);

        if (!tempFile.exists() || !tempFile.canRead()) {
            System.err.println("Файл изображения не найден: " + tempFile.getAbsolutePath());
            return null;
        }

        try {
            return ImageIO.read(tempFile);
        } catch (IOException e) {
            System.err.println("Ошибка при загрузке изображения: " + e.getMessage());
            return null;
        }
    }

    // Загрузка Image из папки temp
    public Image loadImageFromTemp(String filePath) {
        File tempFile = new File(tempPath + filePath);

        if (!tempFile.exists() || !tempFile.canRead()) {
            System.err.println("Файл изображения не найден: " + tempFile.getAbsolutePath());
            return null;
        }

        return new Image(tempFile.toURI().toString());
    }

    // Сохранение изображения Мандельброта в папку temp
    public void saveMandelbrotToTemp(BufferedImage image) {
        String tempFilePath = tempPath + "mandelbrot.png";
        File tempFile = new File(tempFilePath);

        try {
            ImageIO.write(image, "png", tempFile);
            System.out.println("Изображение сохранено в папку temp: " + tempFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении изображения: " + e.getMessage());
        }
    }
}