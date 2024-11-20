import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.awt.Graphics2D;

public class ImageDecryptor {
    private static final String PROJECT_PATH = "C:/Users/Danil/ideaProjects/mandelbrot_for_cipher/";

    public static void main(String[] args) {
        try {
            // Загрузка параметров из бинарного файла key_decoder
            Object[] keyDecoderParams = loadKeyDecoderParametersFromBinaryFile(PROJECT_PATH + "resources/key_decoder.bin");

            // Извлечение параметров из key_decoder
            double ZOOM = (double) keyDecoderParams[0];
            double offsetX = (double) keyDecoderParams[1];
            double offsetY = (double) keyDecoderParams[2];
            int MAX_ITER = (int) keyDecoderParams[3];
            int segmentWidthSize = (int) keyDecoderParams[4];
            int segmentHeightSize = (int) keyDecoderParams[5];
            Map<Integer, Integer> segmentMapping = (Map<Integer, Integer>) keyDecoderParams[6];
            int startX = (int) keyDecoderParams[7];
            int startY = (int) keyDecoderParams[8];
            int width = (int) keyDecoderParams[9];
            int height = (int) keyDecoderParams[10];

            // Загрузка зашифрованного изображения
            BufferedImage encryptedImage = ImageIO.read(new File(PROJECT_PATH + "resources/encrypted_image.png"));

            // Копирование зашифрованного изображения
            BufferedImage encryptedImageCopy = new BufferedImage(encryptedImage.getWidth(), encryptedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = encryptedImageCopy.createGraphics();
            g2d.drawImage(encryptedImage, 0, 0, null);
            g2d.dispose();

            // Генерация изображения множества Мандельброта по параметрам из key_decoder
            BufferedImage mandelbrotImage = generateMandelbrotImage(encryptedImage.getWidth(), encryptedImage.getHeight(), ZOOM, offsetX, offsetY, MAX_ITER);

            // Вырезаем у копии зашифрованную область по значениям width, height, startX, startY из key_decoder
            BufferedImage selectedEncryptedImage = encryptedImageCopy.getSubimage(startX, startY, width, height);

            // Восстанавливаем исходный порядок сегментов
            Model_ImageMatrix model = new Model_ImageMatrix(selectedEncryptedImage, height, width);
            BufferedImage unshuffledImage = model.unshuffledSegments(selectedEncryptedImage, segmentMapping, segmentWidthSize, segmentHeightSize);

            // Выполнение операции XOR между копией восстановленного изображения и изображением Мандельброта
            BufferedImage xorImage = performXOR(unshuffledImage, mandelbrotImage.getSubimage(startX, startY, width, height));

            // Вырезаем результат, используя width, height, startX, startY
            BufferedImage selectedArea = xorImage.getSubimage(0, 0, width, height);

            // Сшиваем вырезанный участок с исходным зашифрованным изображением
            g2d = encryptedImage.createGraphics();
            g2d.drawImage(selectedArea, startX, startY, null);
            g2d.dispose();

            // Отображаем исходное изображение с дешифрованной областью
            View_ImageMatrix originalView = new View_ImageMatrix("Original Image with Decrypted Area", encryptedImage, null);
            originalView.showImage();

            // Сохраняем дешифрованное изображение
            saveDecryptedImage(encryptedImage);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Сохраняет дешифрованное изображение в файл.
     *
     * @param decryptedImage Дешифрованное изображение для сохранения.
     */
    private static void saveDecryptedImage(BufferedImage decryptedImage) {
        try {
            ImageIO.write(decryptedImage, "png", new File(PROJECT_PATH + "resources/decrypted_image.png"));
            System.out.println("Дешифрованное изображение сохранено как decrypted_image.png");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Загружает параметры из бинарного файла key_decoder.
     *
     * @param filePath Путь к файлу для загрузки параметров.
     * @return Массив объектов с параметрами.
     */
    private static Object[] loadKeyDecoderParametersFromBinaryFile(String filePath) {
        Object[] params = new Object[11];
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(Paths.get(filePath)))) {
            params[0] = dis.readDouble(); // ZOOM
            params[1] = dis.readDouble(); // offsetX
            params[2] = dis.readDouble(); // offsetY
            params[3] = dis.readInt(); // MAX_ITER
            params[4] = dis.readInt(); // segmentWidthSize
            params[5] = dis.readInt(); // segmentHeightSize
            int segmentCount = dis.readInt();
            Map<Integer, Integer> segmentMapping = new HashMap<>();
            for (int i = 0; i < segmentCount; i++) {
                int key = dis.readInt();
                int value = dis.readInt();
                segmentMapping.put(key, value);
            }
            params[6] = segmentMapping;
            params[7] = dis.readInt(); // startX
            params[8] = dis.readInt(); // startY
            params[9] = dis.readInt(); // width
            params[10] = dis.readInt(); // height
        } catch (IOException e) {
            e.printStackTrace();
        }
        return params;
    }

    /**
     * Генерирует изображение множества Мандельброта по заданным параметрам.
     *
     * @param width    Ширина изображения.
     * @param height   Высота изображения.
     * @param ZOOM     Зум.
     * @param offsetX  Смещение по X.
     * @param offsetY  Смещение по Y.
     * @param MAX_ITER Максимальное количество итераций.
     * @return Изображение множества Мандельброта.
     */
    private static BufferedImage generateMandelbrotImage(int width, int height, double ZOOM, double offsetX, double offsetY, int MAX_ITER) {
        BufferedImage mandelbrotImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double zx = 0, zy = 0;
                double cX = (x - width / 1.75) / ZOOM + offsetX;
                double cY = (y - height / 1.75) / ZOOM + offsetY;
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

    /**
     * Выполняет операцию XOR между двумя изображениями.
     *
     * @param image1 Первое изображение.
     * @param image2 Второе изображение.
     * @return Результат операции XOR.
     */
    private static BufferedImage performXOR(BufferedImage image1, BufferedImage image2) {
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