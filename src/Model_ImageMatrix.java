import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.Graphics2D;
import java.util.Random;

/**
 * @author Boykov Iliya
 */
public class Model_ImageMatrix {
    private int[] pixels;
    private double[][] imageMatrix;

    /**
     * Constructor of the Model class with params
     * @param my_image Buffered image from input
     * @param my_height image height in pixels
     * @param my_width image width in pixels
     */
    Model_ImageMatrix(BufferedImage my_image, int my_height, int my_width) {
        DataBuffer dataBuffer = my_image.getRaster().getDataBuffer();
        if (dataBuffer instanceof DataBufferInt) {
            this.pixels = ((DataBufferInt) dataBuffer).getData();
        } else {
            // Преобразование изображения в тип BufferedImage.TYPE_INT_RGB
            BufferedImage convertedImage = new BufferedImage(my_image.getWidth(), my_image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = convertedImage.createGraphics();
            g2d.drawImage(my_image, 0, 0, null);
            g2d.dispose();
            this.pixels = ((DataBufferInt) convertedImage.getRaster().getDataBuffer()).getData();
        }
        this.imageMatrix = new double[my_height][my_width];
    }

    /**
     * pixels field setter
     * @param my_pixels image as a vector of pixels
     */
    public void setPixels(int[] my_pixels) {
        this.pixels = my_pixels;
    }

    /**
     * pixels field getter
     * @return pixels field with type int[]
     */
    public int[] getPixels() {
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
                imageMatrix[i][j] = pixels[i * my_width + j]; // Translate int to double
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
    public double[][] encryptImage(double[][] mandelbrotMatrix, int shiftBits) {
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

        // Применение сдвига бит
        encryptedMatrix = shiftPixels(encryptedMatrix, shiftBits);

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
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                pixels[i * width + j] = (int) matrix[i][j];
            }
        }

        return image;
    }

    /**
     * Сдвигает пиксели зашифрованного изображения на определенное количество бит.
     *
     * @param encryptedMatrix Зашифрованная матрица пикселей.
     * @param shiftBits Количество бит для сдвига.
     * @return Матрица пикселей со сдвигом.
     *
     * @author andrey
     */
    public double[][] shiftPixels(double[][] encryptedMatrix, int shiftBits) {
        int height = encryptedMatrix.length;
        int width = encryptedMatrix[0].length;

        double[][] shiftedMatrix = new double[height][width];

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int pixel = (int) encryptedMatrix[i][j];
                int shiftedPixel = (pixel << shiftBits) | (pixel >>> (32 - shiftBits)); // Циклический сдвиг влево
                shiftedMatrix[i][j] = shiftedPixel;
            }
        }

        return shiftedMatrix;
    }

    /**
     * Смешивает цвета двух изображений.
     *
     * @param fractalColor Цвет из фрактального изображения.
     * @param imageColor Цвет из исходного изображения.
     * @return Смешанный цвет.
     *
     * @author andrey
     */
    private int mixColors(int fractalColor, int imageColor) {
        int fractalRed = (fractalColor << 16) & 0xFF;
        int fractalGreen = (fractalColor << 8) & 0xFF;
        int fractalBlue = fractalColor & 0xFF;

        int imageRed = (imageColor >> 16) & 0xFF;
        int imageGreen = (imageColor >> 8) & 0xFF;
        int imageBlue = imageColor & 0xFF;

        int mixedRed = (fractalRed + imageRed) / 2;
        int mixedGreen = (fractalGreen + imageGreen) / 2;
        int mixedBlue = (fractalBlue + imageBlue) / 2;

        return (mixedRed << 16) & (mixedGreen << 8) & mixedBlue;
    }

    /**
     * Разбивает изображение на сегменты и перемешивает их случайным образом.
     *
     * @param image Изображение для разбиения.
     * @param segmentWidthSize Количество сегментов по ширине.
     * @param segmentHeightSize Количество сегментов по высоте.
     * @return Зашифрованное изображение.
     */
    public BufferedImage shuffleSegments(BufferedImage image, int segmentWidthSize, int segmentHeightSize) {
        int width = image.getWidth();
        int height = image.getHeight();
        int segmentWidth = width / segmentWidthSize;
        int segmentHeight = height / segmentHeightSize;

        // Проверка, что размеры изображения делятся на количество сегментов без остатка
        if (width % segmentWidthSize != 0 || height % segmentHeightSize != 0) {
            throw new IllegalArgumentException("Размеры изображения должны быть кратны количеству сегментов");
        }

        BufferedImage shuffledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = shuffledImage.createGraphics();

        Random random = new Random();
        int totalSegments = segmentWidthSize * segmentHeightSize;
        int[] segmentIndices = new int[totalSegments];
        for (int i = 0; i < totalSegments; i++) {
            segmentIndices[i] = i;
        }
        for (int i = totalSegments - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = segmentIndices[i];
            segmentIndices[i] = segmentIndices[j];
            segmentIndices[j] = temp;
        }

        for (int i = 0; i < totalSegments; i++) {
            int segmentIndex = segmentIndices[i];
            int segmentX = (segmentIndex % segmentWidthSize) * segmentWidth;
            int segmentY = (segmentIndex / segmentWidthSize) * segmentHeight;
            g2d.drawImage(image.getSubimage(segmentX, segmentY, segmentWidth, segmentHeight),
                    (i % segmentWidthSize) * segmentWidth, (i / segmentWidthSize) * segmentHeight, null);
        }

        g2d.dispose();
        return shuffledImage;
    }
}