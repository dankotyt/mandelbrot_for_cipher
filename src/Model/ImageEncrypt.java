package Model;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.Map;

import View.ImageView;

public class ImageEncrypt {
    private static BufferedImage originalImage;
    private static BufferedImage encryptedWholeImage;
    private static BufferedImage selectedImage;
    private static int startX, startY, endX, endY;
    private static boolean selecting = false;

    private static final String RESOURCES_PATH = "resources" + File.separator;

    private static String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private static String getResourcesPath() {
        return getProjectRootPath() + RESOURCES_PATH;
    }

    public static void encryptWholeOrSelected() {
        try {
            // Загрузка изображения
            originalImage = ImageIO.read(new File(getResourcesPath() + "input.png"));
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

        // Загружаем изображение множества Мандельброта
        BufferedImage mandelbrotImage = null;
        try {
            mandelbrotImage = ImageIO.read(new File(getResourcesPath() + "mandelbrot.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Вырезаем соответствующую область из изображения Мандельброта
        BufferedImage mandelbrotSelectedArea = mandelbrotImage.getSubimage(
                Math.min(startX, endX), Math.min(startY, endY),
                Math.abs(endX - startX), Math.abs(endY - startY));

        // Загружаем параметры из mandelbrot_params
        Object[] mandelbrotParams = BinaryFile.loadMandelbrotParamsFromBinaryFile(getResourcesPath() + "mandelbrot_params.bin");
        int startMandelbrotWidth = (int) mandelbrotParams[0];
        int startMandelbrotHeight = (int) mandelbrotParams[1];
        double ZOOM = (double) mandelbrotParams[2];
        double offsetX = (double) mandelbrotParams[3];
        double offsetY = (double) mandelbrotParams[4];
        int MAX_ITER = (int) mandelbrotParams[5];

        BufferedImage encryptedXORImage = XOR.performXOR(selectedImage, mandelbrotSelectedArea);

        // Сегментируем и перемешиваем зашифрованную область
        ImageSegmentShuffler mandelbrotModel = new ImageSegmentShuffler(encryptedXORImage);
        Pair<BufferedImage, Map<Integer, Integer>> shuffledResult = mandelbrotModel.shuffleSegments(encryptedXORImage, segmentWidthSize, segmentHeightSize);
        BufferedImage shuffledImage = shuffledResult.getKey();
        Map<Integer, Integer> segmentMapping = shuffledResult.getValue();

        // Заменяем выделенную область на зашифрованную в исходном изображении
        Graphics2D g2d = originalImage.createGraphics();
        g2d.drawImage(shuffledImage, Math.min(startX, endX), Math.min(startY, endY), null);
        g2d.dispose();

        // Отображаем исходное изображение с зашифрованной областью
        ImageView originalView = new ImageView("Original Image with Encrypted Area", originalImage);
        originalView.showImage();

        // Сохраняем зашифрованное изображение и параметры
        saveEncryptedImage(originalImage); // Сохраняем всю картинку с зашифрованной областью
        BinaryFile.saveKeyDecoderToBinaryFile(getResourcesPath() + "key_decoder.bin", startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER, segmentWidthSize, segmentHeightSize, segmentMapping, Math.min(startX, endX), Math.min(startY, endY), Math.abs(endX - startX), Math.abs(endY - startY));
    }

    public static void encryptWholeImage(BufferedImage image) {
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

        // Загружаем изображение множества Мандельброта
        BufferedImage mandelbrotImage = null;
        try {
            mandelbrotImage = ImageIO.read(new File(getResourcesPath() + "mandelbrot.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }


        // Изменяем размер изображения множества Мандельброта, если оно не совпадает с исходным изображением
        if (mandelbrotImage.getWidth() != width || mandelbrotImage.getHeight() != height) {
            mandelbrotImage = ImageSegmentShuffler.resizeImage(mandelbrotImage, width, height);
        }

        if (image.getWidth() != mandelbrotImage.getWidth() || image.getHeight() != mandelbrotImage.getHeight()) {
            throw new IllegalArgumentException("Размеры изображений должны совпадать");
        }

        // Преобразование типов изображений в BufferedImage.TYPE_INT_RGB
        image = ImageUtils.convertToType(image, BufferedImage.TYPE_INT_RGB);
        mandelbrotImage = ImageUtils.convertToType(mandelbrotImage, BufferedImage.TYPE_INT_RGB);

        // Проверка кодировки пикселей
        if (image.getType() != BufferedImage.TYPE_INT_RGB || mandelbrotImage.getType() != BufferedImage.TYPE_INT_RGB) {
            throw new IllegalArgumentException("Изображения должны быть типа BufferedImage.TYPE_INT_RGB");
        }

        BufferedImage encryptedXORImage = XOR.performXOR(image, mandelbrotImage);

        // Сегментируем и перемешиваем зашифрованное изображение
        ImageSegmentShuffler mandelbrotModel = new ImageSegmentShuffler(encryptedXORImage);
        Pair<BufferedImage, Map<Integer, Integer>> shuffledResult = mandelbrotModel.shuffleSegments(encryptedXORImage, segmentWidthSize, segmentHeightSize);
        BufferedImage shuffledImage = shuffledResult.getKey();
        Map<Integer, Integer> segmentMapping = shuffledResult.getValue();

        encryptedWholeImage = shuffledImage;

        // Загружаем параметры из mandelbrot_params
        Object[] mandelbrotParams = BinaryFile.loadMandelbrotParamsFromBinaryFile(getResourcesPath() + "mandelbrot_params.bin");
        int startMandelbrotWidth = (int) mandelbrotParams[0];
        int startMandelbrotHeight = (int) mandelbrotParams[1];
        double ZOOM = (double) mandelbrotParams[2];
        double offsetX = (double) mandelbrotParams[3];
        double offsetY = (double) mandelbrotParams[4];
        int MAX_ITER = (int) mandelbrotParams[5];

        // Сохраняем зашифрованное изображение и параметры
        saveEncryptedImage(encryptedWholeImage); // Сохраняем зашифрованное изображение
        BinaryFile.saveKeyDecoderToBinaryFile(getResourcesPath() + "key_decoder.bin", startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER, segmentWidthSize, segmentHeightSize, segmentMapping, 0, 0, width, height);
    }

    public BufferedImage getEncryptedImage() {
        return encryptedWholeImage;
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
            File outputFile = new File(getResourcesPath() + "encrypted_image.bmp");
            ImageIO.write(encryptedImage, "bmp", outputFile);
            System.out.println("Зашифрованное изображение сохранено как encrypted_image.bmp");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}