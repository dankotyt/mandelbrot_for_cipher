import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            // Loading image
            String destinationPath = "C:/Users/r10021998/ideaProjects/mandelbrot_for_cipher-master/resources/";
            BufferedImage originalImage = ImageIO.read(new File(destinationPath + "input.jpg"));
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            // Проверка, что размеры изображения делятся на количество сегментов без остатка
            int segmentWidthSize = 32; // Например, 32 сегмента по ширине
            int segmentHeightSize = 16; // Например, 16 сегментов по высоте
            if (width % segmentWidthSize != 0 || height % segmentHeightSize != 0) {
                throw new IllegalArgumentException("Размеры изображения должны быть кратны количеству сегментов");
            }

            // Изменение размеров исходного изображения, если они не совпадают с требуемыми
            if (originalImage.getWidth() != width || originalImage.getHeight() != height) {
                originalImage = Model_ImageMatrix.resizeImage(originalImage, width, height);
            }

            // Translate image into matrix of pixels
            Model_ImageMatrix originalModel = new Model_ImageMatrix(originalImage, height, width);
            originalModel.translatePixelsToNumbers(height, width);

            // Show image and matrix
            View_ImageMatrix originalView = new View_ImageMatrix("Original image", originalImage, originalModel.getImageMatrix());
            originalView.showImage();
            originalView.setTitle("Original matrix");
            originalView.showMatrix();

            BufferedImage mandelbrotImage = ImageIO.read(new File(destinationPath + "mandelbrot1.png"));
            int mandelbrotImageWidth = mandelbrotImage.getWidth();
            int mandelbrotImageHeight = mandelbrotImage.getHeight();

            // Изменение размеров изображения множества Мандельброта, если они не совпадают с требуемыми
            if (mandelbrotImageWidth != width || mandelbrotImageHeight != height) {
                mandelbrotImage = Model_ImageMatrix.resizeImage(mandelbrotImage, width, height);
                mandelbrotImageWidth = width;
                mandelbrotImageHeight = height;
            }
            // Translate image into matrix of pixels
            Model_ImageMatrix mandelbrotModel = new Model_ImageMatrix(mandelbrotImage, mandelbrotImageHeight, mandelbrotImageWidth);
            mandelbrotModel.translatePixelsToNumbers(mandelbrotImageHeight, mandelbrotImageWidth);

            // Show image and matrix
            View_ImageMatrix mandelbrotView = new View_ImageMatrix("Mandelbrot image", mandelbrotImage, mandelbrotModel.getImageMatrix());
            mandelbrotView.showImage();
            mandelbrotView.setTitle("Mandelbrot matrix");
            mandelbrotView.showMatrix();

            // Шифрование исходного изображения
            double[][] encryptedMatrix = originalModel.encryptImage(mandelbrotModel.getImageMatrix(), 320);
            BufferedImage encryptedImage = originalModel.matrixToImage(encryptedMatrix, width, height);

            // Перемешивание сегментов изображения
            BufferedImage shuffledImage = originalModel.shuffleSegments(encryptedImage, segmentWidthSize, segmentHeightSize);

            // Отображение зашифрованного изображения
            View_ImageMatrix encryptedView = new View_ImageMatrix("Encrypted Image", shuffledImage, encryptedMatrix);
            encryptedView.showImage();

            // Сохранение зашифрованного изображения
            ImageIO.write(shuffledImage, "jpg", new File(destinationPath + "encrypted_image.jpg"));
            // Запрос пользователю о расшифровке изображения
            int option = JOptionPane.showConfirmDialog(null, "Хотите расшифровать изображение?", "Расшифровать изображение", JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                // Дешифрование изображения
                double[][] decryptedMatrix = originalModel.decryptImage(encryptedMatrix, mandelbrotModel.getImageMatrix());
                BufferedImage decryptedImage = originalModel.matrixToImage(decryptedMatrix, width, height);

                // Отображение расшифрованного изображения
                View_ImageMatrix decryptedView = new View_ImageMatrix("Decrypted Image", decryptedImage, decryptedMatrix);
                decryptedView.showImage();

                // Сохранение расшифрованного изображения
                ImageIO.write(decryptedImage, "jpg", new File(destinationPath + "decrypted_image.jpg"));
            } else {
                // Изображение остается зашифрованным
                JOptionPane.showMessageDialog(null, "Изображение осталось зашифрованным.");
            }

            // Закрытие всех окон и завершение работы программы
            JOptionPane.showMessageDialog(null, "Работа программы завершена.");
            System.exit(0);

        } catch (IOException e) {
            e.printStackTrace();
        }
//        SwingUtilities.invokeLater(() -> {
//            JFrame frame = new JFrame("Mandelbrot");
//            Mandelbrot mandelbrot = new Mandelbrot();
//            frame.add(mandelbrot);
//            frame.setSize(1024, 720);
//            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//            frame.setVisible(true);
//
//            mandelbrot.generateImage();
//        });

    }
}