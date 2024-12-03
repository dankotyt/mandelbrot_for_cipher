package Model;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.awt.Graphics2D;

import View.ImageView;

public class ImageDecrypt {
    private static final String RESOURCES_PATH = "resources" + File.separator;

    private static String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private static String getResourcesPath() {
        return getProjectRootPath() + RESOURCES_PATH;
    }

    public static void decryptImage() {
        try {
            // Загрузка параметров из бинарного файла key_decoder
            Object[] keyDecoderParams = BinaryFile.loadKeyDecoderFromBinaryFile(getResourcesPath() + "key_decoder.bin");

            // Извлечение параметров из key_decoder
            int startMandelbrotWidth = (int) keyDecoderParams[0];
            int startMandelbrotHeight = (int) keyDecoderParams[1];
            double ZOOM = (double) keyDecoderParams[2];
            double offsetX = (double) keyDecoderParams[3];
            double offsetY = (double) keyDecoderParams[4];
            int MAX_ITER = (int) keyDecoderParams[5];
            int segmentWidthSize = (int) keyDecoderParams[6];
            int segmentHeightSize = (int) keyDecoderParams[7];
            Map<Integer, Integer> segmentMapping = (Map<Integer, Integer>) keyDecoderParams[8];
            int startX = (int) keyDecoderParams[9];
            int startY = (int) keyDecoderParams[10];
            int width = (int) keyDecoderParams[11];
            int height = (int) keyDecoderParams[12];

            // Загрузка зашифрованного изображения
            BufferedImage encryptedImage = ImageIO.read(new File(getResourcesPath() + "encrypted_image.bmp"));

            // Выделяем зашифрованную часть
            BufferedImage encryptedSelectedArea = encryptedImage.getSubimage(startX, startY, width, height);

            // Создаем модель для аншафла и возвращаем сегменты на свои места
            ImageSegmentShuffler modelEncryptedSelectedArea = new ImageSegmentShuffler(encryptedSelectedArea);
            BufferedImage unshuffledSelectedImage = modelEncryptedSelectedArea.unshuffledSegments(encryptedSelectedArea, segmentMapping, segmentWidthSize, segmentHeightSize);

            // Загрузка существующего изображения Мандельброта
            BufferedImage mandelbrotImage = ImageIO.read(new File(getResourcesPath() + "mandelbrot.png"));

            // Выделяем ту же часть Мандельброта, что и зашифрованная
            BufferedImage selectedMandelbrotImage = mandelbrotImage.getSubimage(startX, startY, width, height);

            // Преобразование типов изображений в BufferedImage.TYPE_INT_RGB
            selectedMandelbrotImage = ImageUtils.convertToType(selectedMandelbrotImage, BufferedImage.TYPE_INT_RGB);
            unshuffledSelectedImage = ImageUtils.convertToType(unshuffledSelectedImage, BufferedImage.TYPE_INT_RGB);

            // Проверка кодировки пикселей
            if (selectedMandelbrotImage.getType() != BufferedImage.TYPE_INT_RGB || unshuffledSelectedImage.getType() != BufferedImage.TYPE_INT_RGB) {
                throw new IllegalArgumentException("Изображения должны быть типа BufferedImage.TYPE_INT_RGB");
            }

            // XOR между выделенной частью и выделенным Мандельбротом
            BufferedImage xorResultImage = XOR.performXOR(selectedMandelbrotImage, unshuffledSelectedImage);

            // Сшиваем вырезанный участок с исходным зашифрованным изображением
            Graphics2D g2d = encryptedImage.createGraphics();
            g2d.drawImage(xorResultImage, startX, startY, null);
            g2d.dispose();

            // Сохраняем дешифрованное изображение
            saveDecryptedImage(encryptedImage);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected static void saveDecryptedImage(BufferedImage decryptedImage) {
        try {
            ImageIO.write(decryptedImage, "png", new File(getResourcesPath() + "decrypted_image.png"));
            System.out.println("Дешифрованное изображение сохранено как decrypted_image.png");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static BufferedImage loadDecryptedImage() {
        try {
            File decryptedImageFile = new File(getResourcesPath() + "decrypted_image.png");
            if (!decryptedImageFile.exists()) {
                System.err.println("Файл дешифрованного изображения не найден: " + decryptedImageFile.getAbsolutePath());
                return null;
            }
            return ImageIO.read(decryptedImageFile);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

//    private static BufferedImage generateMandelbrotImage(int startMandelbrotWidth, int startMandelbrotHeight, double ZOOM, double offsetX, double offsetY, int MAX_ITER) {
//        // Создаем экземпляр класса Mandelbrot
//        Mandelbrot mandelbrot = new Mandelbrot();
//
//        // Генерируем изображение с помощью метода generateImage, используя значения из сеттеров
//        BufferedImage mandelbrotImage = mandelbrot.generateImage(startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER);
//
//        return mandelbrotImage;
//    }
}