import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.Graphics2D;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;

public class Model_ImageMatrix {
    private int[] pixels;
    private double[][] imageMatrix;

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

    public double[][] getImageMatrix() {
        return this.imageMatrix;
    }

    public void translatePixelsToNumbers(int my_height, int my_width) {
        // Проверка, что размеры массива pixels соответствуют размерам изображения
        if (pixels.length != my_height * my_width) {
            throw new IllegalArgumentException("Размер массива pixels не соответствует размерам изображения");
        }

        // Инициализация матрицы imageMatrix
        imageMatrix = new double[my_height][my_width];

        for (int i = 0; i < my_height; i++) {
            for (int j = 0; j < my_width; j++) {
                imageMatrix[i][j] = pixels[i * my_width + j]; // Translate int to double
            }
        }
    }

    public static BufferedImage resizeImage(BufferedImage image, int width, int height) {
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(image, 0, 0, width, height, null);
        g2d.dispose();
        return resizedImage;
    }

    public double[][] encryptImage(double[][] mandelbrotMatrix, int shiftBits) {
        int height = imageMatrix.length;
        int width = imageMatrix[0].length;
        int mandelbrotHeight = mandelbrotMatrix.length;
        int mandelbrotWidth = mandelbrotMatrix[0].length;

        // Проверка размеров матриц
        if (height != mandelbrotHeight || width != mandelbrotWidth) {
            mandelbrotMatrix = resizeMandelbrotMatrix(mandelbrotMatrix, height, width);
        }

        double[][] encryptedMatrix = new double[height][width];

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                encryptedMatrix[i][j] = (int) imageMatrix[i][j] ^ (int) mandelbrotMatrix[i][j];
            }
        }
        return encryptedMatrix;
    }

    public double[][] decryptImage(double[][] encryptedMatrix, double[][] mandelbrotMatrix) {
        int height = encryptedMatrix.length;
        int width = encryptedMatrix[0].length;
        int mandelbrotHeight = mandelbrotMatrix.length;
        int mandelbrotWidth = mandelbrotMatrix[0].length;

        // Проверка размеров матриц
        if (height != mandelbrotHeight || width != mandelbrotWidth) {
            mandelbrotMatrix = resizeMandelbrotMatrix(mandelbrotMatrix, height, width);
        }

        double[][] decryptedMatrix = new double[height][width];

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                decryptedMatrix[i][j] = (int) encryptedMatrix[i][j] ^ (int) mandelbrotMatrix[i][j];
            }
        }
        return decryptedMatrix;
    }

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

    public Pair<BufferedImage, Map<Integer, Integer>> shuffleSegments(BufferedImage image, int segmentWidthSize, int segmentHeightSize) {
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

        int totalSegments = segmentWidthSize * segmentHeightSize;
        int[] segmentIndices = new int[totalSegments];
        for (int i = 0; i < totalSegments; i++) {
            segmentIndices[i] = i;
        }

        // Перемешивание индексов сегментов
        Random random = new Random();
        for (int i = totalSegments - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = segmentIndices[i];
            segmentIndices[i] = segmentIndices[j];
            segmentIndices[j] = temp;
        }

        Map<Integer, Integer> segmentMapping = new HashMap<>();
        for (int i = 0; i < totalSegments; i++) {
            int originalIndex = i; // Исходный индекс сегмента
            int shuffledIndex = segmentIndices[i]; // Перемешанный индекс сегмента
            int segmentX = (shuffledIndex % segmentWidthSize) * segmentWidth;
            int segmentY = (shuffledIndex / segmentWidthSize) * segmentHeight;
            g2d.drawImage(image.getSubimage(segmentX, segmentY, segmentWidth, segmentHeight),
                    (i % segmentWidthSize) * segmentWidth, (i / segmentWidthSize) * segmentHeight, null);
            segmentMapping.put(originalIndex, shuffledIndex); // Сохраняем исходный индекс как ключ и перемешанный индекс как значение
        }

        g2d.dispose();
        return new Pair<>(shuffledImage, segmentMapping);
    }

    public BufferedImage unshuffledSegments(BufferedImage shuffledImage, Map<Integer, Integer> segmentMapping, int segmentWidthSize, int segmentHeightSize) {
        int width = shuffledImage.getWidth();
        int height = shuffledImage.getHeight();
        int segmentWidth = width / segmentWidthSize;
        int segmentHeight = height / segmentHeightSize;

        BufferedImage unshuffledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = unshuffledImage.createGraphics();

        for (Map.Entry<Integer, Integer> entry : segmentMapping.entrySet()) {
            //ИМЕННО ТАК БЛЯТЬ И НИКАК ИНАЧЕ!!!! СПЕРВА VALUE, ПОТОМ KEY
            int originalIndex = entry.getValue(); // Исходный индекс сегмента
            int shuffledIndex = entry.getKey(); // Перемешанный индекс сегмента
            //=----------------------------------------------------------------=
            int segmentX = (shuffledIndex % segmentWidthSize) * segmentWidth;
            int segmentY = (shuffledIndex / segmentWidthSize) * segmentHeight;
            int originalX = (originalIndex % segmentWidthSize) * segmentWidth;
            int originalY = (originalIndex / segmentWidthSize) * segmentHeight;
            g2d.drawImage(shuffledImage.getSubimage(segmentX, segmentY, segmentWidth, segmentHeight),
                    originalX, originalY, null);
        }

        g2d.dispose();
        return unshuffledImage;
    }

    public static double[][] resizeMandelbrotMatrix(double[][] mandelbrotMatrix, int targetHeight, int targetWidth) {
        int mandelbrotHeight = mandelbrotMatrix.length;
        int mandelbrotWidth = mandelbrotMatrix[0].length;

        if (mandelbrotHeight == targetHeight && mandelbrotWidth == targetWidth) {
            return mandelbrotMatrix;
        }

        double[][] resizedMatrix = new double[targetHeight][targetWidth];

        for (int i = 0; i < targetHeight; i++) {
            for (int j = 0; j < targetWidth; j++) {
                int mandelbrotI = (int) ((double) i / targetHeight * mandelbrotHeight);
                int mandelbrotJ = (int) ((double) j / targetWidth * mandelbrotWidth);
                resizedMatrix[i][j] = mandelbrotMatrix[mandelbrotI][mandelbrotJ];
            }
        }
        return resizedMatrix;
    }
}