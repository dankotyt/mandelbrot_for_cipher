package Model;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.awt.Graphics2D;

import View.ImageView;

public class ImageDecrypt {
    private static final String PROJECT_PATH = "C:/Users/Danil/ideaProjects/mandelbrot_for_cipher/";

    protected void decryptImage() {
        try {
            // Загрузка параметров из бинарного файла key_decoder
            Object[] keyDecoderParams = BinaryFile.loadKeyDecoderFromBinaryFile(PROJECT_PATH + "resources/key_decoder.bin");

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
            BufferedImage encryptedImage = ImageIO.read(new File(PROJECT_PATH + "resources/encrypted_image.png"));

            //Выделяем зашифрованную часть
            BufferedImage encryptedSelectedArea = encryptedImage.getSubimage(startX, startY, width, height);

            //Создаем модель для аншафла и возвращаем сегменты на свои места
            ImageSegmentShuffler modelEncryptedSelectedArea = new ImageSegmentShuffler(encryptedSelectedArea);
            BufferedImage unshuffledSelectedImage = modelEncryptedSelectedArea.unshuffledSegments(encryptedSelectedArea, segmentMapping, segmentWidthSize, segmentHeightSize);

            //Генерация полноразмерного Мандельброта по key_decoder
            BufferedImage mandelbrotImage = generateMandelbrotImage(startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER);

            //Выделяем ту же часть Мандельброта, что и зашифрованная
            BufferedImage selectedMandelbrotImage = mandelbrotImage.getSubimage(startX, startY, width, height);

            //XOR между выделенной части и выделенным Мандельбротом
            BufferedImage resultImage = performXOR(unshuffledSelectedImage, selectedMandelbrotImage);

            // Сшиваем вырезанный участок с исходным зашифрованным изображением
            Graphics2D g2d = encryptedImage.createGraphics();
            g2d.drawImage(resultImage, startX, startY, null);
            g2d.dispose();

            // Отображаем исходное изображение с дешифрованной областью
            ImageView originalView = new ImageView("Original Image with Decrypted Area", encryptedImage);
            originalView.showImage();

            // Сохраняем дешифрованное изображение
            saveDecryptedImage(encryptedImage);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected static void saveDecryptedImage(BufferedImage decryptedImage) {
        try {
            ImageIO.write(decryptedImage, "png", new File(PROJECT_PATH + "resources/decrypted_image.png"));
            System.out.println("Дешифрованное изображение сохранено как decrypted_image.png");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static BufferedImage generateMandelbrotImage(int startMandelbrotWidth, int startMandelbrotHeight, double ZOOM, double offsetX, double offsetY, int MAX_ITER) {
        BufferedImage mandelbrotImage = new BufferedImage(startMandelbrotWidth, startMandelbrotHeight, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < startMandelbrotHeight; y++) {
            for (int x = 0; x < startMandelbrotWidth; x++) {
                double zx = 0, zy = 0;
                double cX = (x - startMandelbrotWidth / 1.75) / ZOOM + offsetX;
                double cY = (y - startMandelbrotHeight / 1.75) / ZOOM + offsetY;
                int i = MAX_ITER;
                while (zx * zx + zy * zy < 4 && i > 0) {
                    double tmp = zx * zx - zy * zy + cX;
                    zy = 2.0 * zx * zy + cY;
                    zx = tmp;
                    i--;
                }
                int color = i | (i << 10) | (i << 14);
                mandelbrotImage.setRGB(x, y, i > 0 ? color : 0);
            }
        }
        return mandelbrotImage;
    }

    protected static BufferedImage performXOR(BufferedImage image1, BufferedImage image2) {
        int width = image1.getWidth();
        int height = image1.getHeight();
        BufferedImage resultImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb1 = image1.getRGB(x, y);
                int rgb2 = image2.getRGB(x, y);
                int xorRGB = rgb1 ^ rgb2;
                resultImage.setRGB(x, y, xorRGB);
            }
        }
        return resultImage;
    }
}