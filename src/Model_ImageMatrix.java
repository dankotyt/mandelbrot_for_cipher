import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.Graphics2D;

/**
 * @author Boykov Iliya
 */
public class Model_ImageMatrix {
    private byte[] pixels;
    private double[][] imageMatrix;

    /**
     * Constructor of the Model class with params
     * @param my_image Buffered image from input
     * @param my_height image height in pixels
     * @param my_width image width in pixels
     */
    Model_ImageMatrix(BufferedImage my_image, int my_height, int my_width) {
        this.pixels = ((DataBufferByte) my_image.getRaster().getDataBuffer()).getData();
        this.imageMatrix = new double[my_height][my_width];
    }

    /**
     * pixels field setter
     * @param my_pixels image as a vector of pixels
     */
    public void setPixels(byte[] my_pixels) {
        this.pixels = my_pixels;
    }

    /**
     * pixels field getter
     * @return pixels field with type byte[]
     */
    public byte[] getPixels() {
        return this.pixels;
    }

    /**
     * imageMatrix field setter
     * @param my_imageMatrix matrix of numbers which are translation of pixels in image
     */
    public void setImageMatrix(double[][] my_imageMatrix) {
        this.imageMatrix = my_imageMatrix;
    }

    /**
     * imageMatrix field getter
     * @return imageMatrix field with type double[][] (2-dim matrix)
     */
    public double[][] getImageMatrix() {
        return this.imageMatrix;
    }

    /**
     * Method which translates image in pixels to matrix of numbers
     * @param my_height height of image in pixels
     * @param my_width width of image in pixels
     */
    public void translatePixelsToNumbers(int my_height, int my_width) {
        for (int i = 0; i < my_height; i++) {
            for (int j = 0; j < my_width; j++) {
                imageMatrix[i][j] = pixels[i * my_width + j] & 0xFF; // Translate byte to int
            }
        }
    }

    /**
     * Изменяет размер изображения до нужных размеров.
     *
     * @param image Изображение для изменения размера.
     * @param width Новая ширина.
     * @param height Новая высота.
     * @return Изображение с измененными размерами.
     *
     * @author andrey
     */
    public static BufferedImage resizeImage(BufferedImage image, int width, int height) {
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(image, 0, 0, width, height, null);
        g2d.dispose();
        return resizedImage;
    }

    /**
     * Шифрует изображение с использованием матрицы множества Мандельброта через операцию XOR.
     *
     * @param mandelbrotMatrix Матрица множества Мандельброта.
     * @return Зашифрованная матрица пикселей.
     * @author andrey
     */
    public double[][] encryptImage(double[][] mandelbrotMatrix) {
        int height = imageMatrix.length;
        int width = imageMatrix[0].length;
        int mandelbrotHeight = mandelbrotMatrix.length;
        int mandelbrotWidth = mandelbrotMatrix[0].length;

        // Проверка размеров матриц
        if (height != mandelbrotHeight || width != mandelbrotWidth) {
            throw new IllegalArgumentException("Размеры матриц не совпадают");
        }

        double[][] encryptedMatrix = new double[height][width];

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                encryptedMatrix[i][j] = (int) imageMatrix[i][j] ^ (int) mandelbrotMatrix[i][j];
            }
        }

        return encryptedMatrix;
    }

    /**
     * Дешифрует изображение с использованием матрицы множества Мандельброта через операцию XOR.
     *
     * @param encryptedMatrix Зашифрованная матрица пикселей.
     * @param mandelbrotMatrix Матрица множества Мандельброта.
     * @return Дешифрованная матрица пикселей.
     *
     * @author andrey
     */
    public double[][] decryptImage(double[][] encryptedMatrix, double[][] mandelbrotMatrix) {
        int height = encryptedMatrix.length;
        int width = encryptedMatrix[0].length;
        int mandelbrotHeight = mandelbrotMatrix.length;
        int mandelbrotWidth = mandelbrotMatrix[0].length;

        // Проверка размеров матриц
        if (height != mandelbrotHeight || width != mandelbrotWidth) {
            throw new IllegalArgumentException("Размеры матриц не совпадают");
        }

        double[][] decryptedMatrix = new double[height][width];

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                decryptedMatrix[i][j] = (int) encryptedMatrix[i][j] ^ (int) mandelbrotMatrix[i][j];
            }
        }

        return decryptedMatrix;
    }

    /**
     * Преобразует матрицу пикселей в изображение.
     *
     * @param matrix Матрица пикселей.
     * @param width Ширина изображения.
     * @param height Высота изображения.
     * @return Изображение.
     *
     * @author andrey
     */
    public BufferedImage matrixToImage(double[][] matrix, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                pixels[i * width + j] = (byte) matrix[i][j];
            }
        }

        return image;
    }
}