import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

public class Main {
    private static BufferedImage originalImage;
    private static BufferedImage selectedImage;
    private static int startX, startY, endX, endY;
    private static boolean selecting = false;

    //private static final String PROJECT_PATH = "C:/Users/r10021998/ideaProjects/mandelbrot_for_cipher-master/";
    private static final String PROJECT_PATH = "C:/Users/Danil/ideaProjects/mandelbrot_for_cipher/";

    public static void main(String[] args) {
        try {
            // Загрузка изображения
            originalImage = ImageIO.read(new File(PROJECT_PATH + "resources/input.jpg"));
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            // Создание окна для отображения изображения
            JFrame frame = new JFrame("Select Area for Encryption");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(width, height);

            // Создание панели для отображения изображения и выделения области
            JPanel panel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.drawImage(originalImage, 0, 0, null);
                    if (selecting) {
                        g.setColor(Color.RED);
                        g.drawRect(Math.min(startX, endX), Math.min(startY, endY),
                                Math.abs(endX - startX), Math.abs(endY - startY));
                    }
                }
            };

            // Добавление обработчика событий мыши
            panel.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    startX = e.getX();
                    startY = e.getY();
                    endX = startX;
                    endY = startY;
                    selecting = true;
                    panel.repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    endX = e.getX();
                    endY = e.getY();
                    selecting = false;
                    panel.repaint();

                    // Вырезаем выделенную область
                    selectedImage = originalImage.getSubimage(
                            Math.min(startX, endX), Math.min(startY, endY),
                            Math.abs(endX - startX), Math.abs(endY - startY));

                    // Шифруем выделенную область
                    encryptSelectedArea(selectedImage);
                }
            });

            panel.addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    endX = e.getX();
                    endY = e.getY();
                    panel.repaint();
                }
            });

            frame.add(panel);
            frame.setVisible(true);

            // Запрос пользователю о выборе области или всего изображения
            int option = JOptionPane.showOptionDialog(null, "Выберите действие:", "Шифрование изображения",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    new String[]{"Зашифровать часть картинки", "Зашифровать всю картинку"}, "Зашифровать часть картинки");

            if (option == JOptionPane.YES_OPTION) {
                // Пользователь выбрал "Зашифровать часть картинки"
                // Обработка выделения области будет выполнена в обработчике мыши
            } else if (option == JOptionPane.NO_OPTION) {
                // Пользователь выбрал "Зашифровать всю картинку"
                encryptWholeImage(originalImage);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void encryptSelectedArea(BufferedImage selectedImage) {
        int selectedWidth = selectedImage.getWidth();
        int selectedHeight = selectedImage.getHeight();

        // Проверка и корректировка размеров для сегментации
        int segmentWidthSize = 16; // Например, 32 сегмента по ширине
        int segmentHeightSize = 8; // Например, 16 сегментов по высоте

        // Уменьшаем размеры сегментов, если они не делят размеры выделенной области без остатка
        while (selectedWidth % segmentWidthSize != 0) {
            segmentWidthSize--;
        }
        while (selectedHeight % segmentHeightSize != 0) {
            segmentHeightSize--;
        }

        // Создаем модель для выделенной области
        Model_ImageMatrix selectedModel = new Model_ImageMatrix(selectedImage, selectedHeight, selectedWidth);
        selectedModel.translatePixelsToNumbers(selectedHeight, selectedWidth);

        // Загружаем изображение множества Мандельброта
        BufferedImage mandelbrotImage = null;
        try {
            mandelbrotImage = ImageIO.read(new File(PROJECT_PATH + "/resources/mandelbrot1.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Изменяем размер изображения множества Мандельброта, если оно не совпадает с выделенной областью
        if (mandelbrotImage.getWidth() != selectedWidth || mandelbrotImage.getHeight() != selectedHeight) {
            mandelbrotImage = Model_ImageMatrix.resizeImage(mandelbrotImage, selectedWidth, selectedHeight);
        }

        // Создаем модель для изображения множества Мандельброта
        Model_ImageMatrix mandelbrotModel = new Model_ImageMatrix(mandelbrotImage, selectedHeight, selectedWidth);
        mandelbrotModel.translatePixelsToNumbers(selectedHeight, selectedWidth);

        // Шифруем выделенную область
        double[][] encryptedMatrix = selectedModel.encryptImage(mandelbrotModel.getImageMatrix(), 320);
        BufferedImage encryptedImage = selectedModel.matrixToImage(encryptedMatrix, selectedWidth, selectedHeight);

        // Сегментируем и перемешиваем зашифрованную область
        Pair<BufferedImage, int[]> shuffledResult = mandelbrotModel.shuffleSegments(encryptedImage, segmentWidthSize, segmentHeightSize);
        BufferedImage shuffledImage = shuffledResult.getKey();
        int[] segmentIndices = shuffledResult.getValue();

        // Заменяем выделенную область на зашифрованную в исходном изображении
        Graphics2D g2d = originalImage.createGraphics();
        g2d.drawImage(shuffledImage, Math.min(startX, endX), Math.min(startY, endY), null);
        g2d.dispose();

        // Отображаем исходное изображение с зашифрованной областью
        View_ImageMatrix originalView = new View_ImageMatrix("Original Image with Encrypted Area", originalImage, null);
        originalView.showImage();

        // Сохраняем зашифрованное изображение и параметры
        saveEncryptedImage(originalImage); // Сохраняем всю картинку с зашифрованной областью
        saveParametersToBinaryFile(PROJECT_PATH + "resources/mandelbrot_params.bin", segmentWidthSize, segmentHeightSize, segmentIndices);

        // Предлагаем дешифровать картинку
        if (JOptionPane.showConfirmDialog(null, "Хотите дешифровать картинку?", "Дешифрование", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            decryptSelectedArea(shuffledImage, mandelbrotModel.getImageMatrix(), segmentWidthSize, segmentHeightSize, segmentIndices);
        }
    }

    private static void encryptWholeImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Проверка и корректировка размеров для сегментации
        int segmentWidthSize = 32; // Например, 32 сегмента по ширине
        int segmentHeightSize = 16; // Например, 16 сегментов по высоте

        if (width % segmentWidthSize != 0) {
            width = (width / segmentWidthSize + 1) * segmentWidthSize;
        }
        if (height % segmentHeightSize != 0) {
            height = (height / segmentHeightSize + 1) * segmentHeightSize;
        }

        // Изменение размера изображения, если необходимо
        if (image.getWidth() != width || image.getHeight() != height) {
            image = resizeImage(image, width, height);
        }

        // Создаем модель для всего изображения
        Model_ImageMatrix originalModel = new Model_ImageMatrix(image, height, width);
        originalModel.translatePixelsToNumbers(height, width);

        // Загружаем изображение множества Мандельброта
        BufferedImage mandelbrotImage = null;
        try {
            mandelbrotImage = ImageIO.read(new File(PROJECT_PATH + "resources/mandelbrot1.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Изменяем размер изображения множества Мандельброта, если оно не совпадает с исходным изображением
        if (mandelbrotImage.getWidth() != width || mandelbrotImage.getHeight() != height) {
            mandelbrotImage = Model_ImageMatrix.resizeImage(mandelbrotImage, width, height);
        }

        // Создаем модель для изображения множества Мандельброта
        Model_ImageMatrix mandelbrotModel = new Model_ImageMatrix(mandelbrotImage, height, width);
        mandelbrotModel.translatePixelsToNumbers(height, width);

        // Шифруем все изображение
        double[][] encryptedMatrix = originalModel.encryptImage(mandelbrotModel.getImageMatrix(), 320);
        BufferedImage encryptedImage = originalModel.matrixToImage(encryptedMatrix, width, height);

        // Сегментируем и перемешиваем зашифрованное изображение
        Pair<BufferedImage, int[]> shuffledResult = mandelbrotModel.shuffleSegments(encryptedImage, segmentWidthSize, segmentHeightSize);
        BufferedImage shuffledImage = shuffledResult.getKey();
        int[] segmentIndices = shuffledResult.getValue();

        // Отображаем зашифрованное изображение
        View_ImageMatrix encryptedView = new View_ImageMatrix("Encrypted Image", shuffledImage, encryptedMatrix);
        encryptedView.showImage();

        // Сохраняем зашифрованное изображение и параметры
        saveEncryptedImage(shuffledImage); // Сохраняем всю картинку с зашифрованной областью
        saveParametersToBinaryFile(PROJECT_PATH + "resources/mandelbrot_params.bin", segmentWidthSize, segmentHeightSize, segmentIndices);

        // Предлагаем дешифровать картинку
        if (JOptionPane.showConfirmDialog(null, "Хотите дешифровать картинку?", "Дешифрование", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            decryptWholeImage(shuffledImage, mandelbrotModel.getImageMatrix(), segmentWidthSize, segmentHeightSize, segmentIndices);
        }
    }

    private static void decryptSelectedArea(BufferedImage encryptedImage, double[][] mandelbrotMatrix, int segmentWidthSize, int segmentHeightSize, int[] segmentIndices) {
        int width = encryptedImage.getWidth();
        int height = encryptedImage.getHeight();

        // Восстанавливаем исходный порядок сегментов
        Model_ImageMatrix encryptedModel = new Model_ImageMatrix(encryptedImage, height, width);
        BufferedImage unshuffledImage = encryptedModel.unshuffledSegments(encryptedImage, segmentIndices, segmentWidthSize, segmentHeightSize);

        // Создаем модель для восстановленного изображения
        encryptedModel = new Model_ImageMatrix(unshuffledImage, height, width);
        encryptedModel.translatePixelsToNumbers(height, width);

        // Дешифруем выделенную область
        double[][] decryptedMatrix = encryptedModel.decryptImage(encryptedModel.getImageMatrix(), mandelbrotMatrix);
        BufferedImage decryptedImage = encryptedModel.matrixToImage(decryptedMatrix, width, height);

        // Заменяем зашифрованную область на дешифрованную в исходном изображении
        Graphics2D g2d = originalImage.createGraphics();
        g2d.drawImage(decryptedImage, Math.min(startX, endX), Math.min(startY, endY), null);
        g2d.dispose();

        // Отображаем исходное изображение с дешифрованной областью
        View_ImageMatrix originalView = new View_ImageMatrix("Original Image with Decrypted Area", originalImage, null);
        originalView.showImage();

        // Сохраняем дешифрованное изображение
        saveDecryptedImage(originalImage);
    }

    private static void decryptWholeImage(BufferedImage encryptedImage, double[][] mandelbrotMatrix, int segmentWidthSize, int segmentHeightSize, int[] segmentIndices) {
        int width = encryptedImage.getWidth();
        int height = encryptedImage.getHeight();

        // Восстанавливаем исходный порядок сегментов
        Model_ImageMatrix encryptedModel = new Model_ImageMatrix(encryptedImage, height, width);
        BufferedImage unshuffledImage = encryptedModel.unshuffledSegments(encryptedImage, segmentIndices, segmentWidthSize, segmentHeightSize);

        // Создаем модель для восстановленного изображения
        encryptedModel = new Model_ImageMatrix(unshuffledImage, height, width);
        encryptedModel.translatePixelsToNumbers(height, width);

        // Дешифруем все изображение
        double[][] decryptedMatrix = encryptedModel.decryptImage(encryptedModel.getImageMatrix(), mandelbrotMatrix);
        BufferedImage decryptedImage = encryptedModel.matrixToImage(decryptedMatrix, width, height);

        // Отображаем дешифрованное изображение
        View_ImageMatrix decryptedView = new View_ImageMatrix("Decrypted Image", decryptedImage, decryptedMatrix);
        decryptedView.showImage();

        // Сохраняем дешифрованное изображение
        saveDecryptedImage(decryptedImage);
    }

    private static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        graphics2D.dispose();
        return resizedImage;
    }

    /**
     * Сохраняет зашифрованное изображение в файл.
     *
     * @param encryptedImage Зашифрованное изображение для сохранения.
     */
    private static void saveEncryptedImage(BufferedImage encryptedImage) {
        try {
            ImageIO.write(encryptedImage, "png", new File(PROJECT_PATH + "resources/encrypted_image.png"));
            System.out.println("Зашифрованное изображение сохранено как encrypted_image.png");
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
     * Сохраняет параметры в бинарный файл.
     *
     * @param filePath         Путь к файлу для сохранения параметров.
     * @param segmentWidthSize Ширина сегмента.
     * @param segmentHeightSize Высота сегмента.
     * @param segmentIndices   Индексы сегментов.
     */
    private static void saveParametersToBinaryFile(String filePath, int segmentWidthSize, int segmentHeightSize, int[] segmentIndices) {
        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
            Mandelbrot mandelbrot = new Mandelbrot();
            dos.writeDouble(mandelbrot.getZOOM());
            dos.writeDouble(mandelbrot.getOffsetX());
            dos.writeDouble(mandelbrot.getOffsetY());
            dos.writeInt(mandelbrot.getMAX_ITER());
            dos.writeInt(segmentWidthSize);
            dos.writeInt(segmentHeightSize);
            dos.writeInt(segmentIndices.length);
            for (int index : segmentIndices) {
                dos.writeInt(index);
            }
            System.out.println("Параметры сохранены в файл " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}