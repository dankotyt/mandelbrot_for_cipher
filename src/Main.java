import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Mandelbrot");
            MandelbrotView view = new MandelbrotView();
            MandelbrotModel model = new MandelbrotModel();
            MandelbrotController controller = new MandelbrotController(model, view);

            frame.add(view);
            frame.setSize(1024, 720);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);

            controller.generateImage();
            controller.showImageMatrix();
        });
    }
//main для шифратора и дешифратора, когда будет работать
//    public static void main(String[] args) {
//        try {
//            // Loading image
//            BufferedImage originalImage = ImageIO.read(new File("resources/input.jpg"));
//            int width = originalImage.getWidth();
//            int height = originalImage.getHeight();
//
//            // Изменение размеров исходного изображения, если они не совпадают с требуемыми
//            if (originalImage.getWidth() != width || originalImage.getHeight() != height) {
//                originalImage = Model.resizeImage(originalImage, width, height);
//            }
//
//            // Translate image into matrix of pixels
//            Model originalModel = new Model(originalImage, height, width);
//            originalModel.translatePixelsToNumbers(height, width);
//
//            // Show image and matrix
//            View originalView = new View("Original image", originalImage, originalModel.getImageMatrix());
//            originalView.showImage();
//            originalView.setTitle("Original matrix");
//            originalView.showMatrix();
//
//            BufferedImage mandelbrotImage = ImageIO.read(new File("resources/mandelbrot1.png"));
//            int mandelbrotImageWidth = mandelbrotImage.getWidth();
//            int mandelbrotImageHeight = mandelbrotImage.getHeight();
//
//            // Изменение размеров изображения множества Мандельброта, если они не совпадают с требуемыми
//            if (mandelbrotImageWidth != width || mandelbrotImageHeight != height) {
//                mandelbrotImage = Model.resizeImage(mandelbrotImage, width, height);
//                mandelbrotImageWidth = width;
//                mandelbrotImageHeight = height;
//            }
//            // Translate image into matrix of pixels
//            Model mandelbrotModel = new Model(mandelbrotImage, mandelbrotImageHeight, mandelbrotImageWidth);
//            mandelbrotModel.translatePixelsToNumbers(mandelbrotImageHeight, mandelbrotImageWidth);
//
//            // Show image and matrix
//            View mandelbrotView = new View("Mandelbrot image", mandelbrotImage, mandelbrotModel.getImageMatrix());
//            mandelbrotView.showImage();
//            mandelbrotView.setTitle("Mandelbrot matrix");
//            mandelbrotView.showMatrix();
//
//            // Шифрование исходного изображения
//            double[][] encryptedMatrix = originalModel.encryptImage(mandelbrotModel.getImageMatrix());
//            BufferedImage encryptedImage = originalModel.matrixToImage(encryptedMatrix, width, height);
//
//            // Отображение зашифрованного изображения
//            View encryptedView = new View("Encrypted Image", encryptedImage, encryptedMatrix);
//            encryptedView.showImage();
//
//            // Запрос пользователю о расшифровке изображения
//            int option = JOptionPane.showConfirmDialog(null, "Хотите расшифровать изображение?", "Расшифровать изображение", JOptionPane.YES_NO_OPTION);
//            if (option == JOptionPane.YES_OPTION) {
//                // Дешифрование изображения
//                double[][] decryptedMatrix = originalModel.decryptImage(encryptedMatrix, mandelbrotModel.getImageMatrix());
//                BufferedImage decryptedImage = originalModel.matrixToImage(decryptedMatrix, width, height);
//
//                // Отображение расшифрованного изображения
//                View decryptedView = new View("Decrypted Image", decryptedImage, decryptedMatrix);
//                decryptedView.showImage();
//            } else {
//                // Изображение остается зашифрованным
//                JOptionPane.showMessageDialog(null, "Изображение осталось зашифрованным.");
//            }
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
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
//    }
}