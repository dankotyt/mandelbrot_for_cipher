package View;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.io.File;
import java.io.FileNotFoundException;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.image.BufferedImage;
import java.util.List;

import Model.BinaryFile;
import Model.Mandelbrot;
import Model.ImageEncrypt;
import Model.ImageDecrypt;

/**
 * Класс Model.FrameInterface представляет собой графический интерфейс для работы с шифрованием и расшифровкой изображений с использованием множества Мандельброта.
 * Интерфейс состоит из нескольких панелей, каждая из которых отвечает за определенный этап процесса шифрования или расшифровки.
 *
 * @author Илья
 * @version 0.1
 */
public class FrameInterface extends BinaryFile {

    /**
     * Объект CardLayout, используемый для управления отображением различных панелей в главном окне.
     */
    private static CardLayout cardLayout;

    /**
     * Основная панель, содержащая все остальные панели, управляемые CardLayout.
     */
    private static JPanel mainPanel;

    /**
     * Ширина экрана, на котором отображается интерфейс.
     */
    private static int screenWidth;

    /**
     * Высота экрана, на котором отображается интерфейс.
     */
    private static int screenHeight;

    /**
     * Полоса загрузки, используемая для отображения прогресса загрузки или выполнения операций.
     */
    private static JProgressBar progressBar;

    /**
     * Массив путей к изображениям, используемым в процессе шифрования и расшифровки.
     */
    private static final String[] IMAGE_PATHS = {
            "mandelbrot1.png", "mandelbrot2.png", "mandelbrot3.png",
            "mandelbrot4.png", "mandelbrot5.png", "mandelbrot6.png"
    };

    /**
     * Цвет для верхней части градиента на панелях.
     */
    private static final int NORTH_COL = 0x011324;

    /**
     * Цвет для нижней части градиента на панелях.
     */
    private static final int SOUTH_COL = 0x011a30;

    /**
     * Шрифт, используемый для кнопок в интерфейсе.
     */
    private static final Font buttonFont = new Font("Arial", Font.BOLD, 16);

    /**
     * Путь к директории с ресурсами, такими, как изображения.
     */
    //private static final String RESOURCES_PATH = "C:/Users/8220920/IdeaProjects/InterfaceMandelbrot/resources/";
    private static final String RESOURCES_PATH = "resources" + File.separator;

    private static String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private static String getResourcesPath() {
        return getProjectRootPath() + RESOURCES_PATH;
    }

    /**
     * Размер кнопок в интерфейсе.
     */
    private static final Dimension buttonSize = new Dimension(400, 40);

    /**
     * Размер текстовых полей в интерфейсе.
     */
    private static final Dimension fieldSize = new Dimension(400, 30);

    //public static File selectedImage = null;

    /**
     * Точка входа в программу. Инициализирует размер экрана и создает главное окно.
     *
     * @param args Аргументы командной строки.
     */
    public static void main(String[] args) {
        initializeScreenSize();
        createMainFrame();
    }

    /**
     * Инициализирует размеры экрана, на котором будет отображаться интерфейс.
     * В случае ошибки устанавливает размеры по умолчанию (640x480).
     */
    public static void initializeScreenSize() {
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Dimension screenSize = toolkit.getScreenSize();
            screenWidth = screenSize.width;
            screenHeight = screenSize.height;
        } catch (Exception e) {
            System.err.println("Ошибка при получении размеров экрана: " + e.getMessage());
            screenWidth = 640;
            screenHeight = 480;
        }
    }

    /**
     * Создает главное окно приложения и настраивает его.
     */
    public static void createMainFrame() {
        try {
            JFrame mainFrame = new JFrame("Шифр Мандельброта");
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            mainFrame.setSize(screenWidth, screenHeight);
            mainFrame.setLocationRelativeTo(null);

            cardLayout = new CardLayout();
            mainPanel = new JPanel(cardLayout);
            mainFrame.add(mainPanel);
            mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);

            createLoadingScreen(mainFrame);
        } catch (Exception e) {
            System.err.println("Ошибка при создании главного фрейма: " + e.getMessage());
        }
    }

    /**
     * Создает экран загрузки, который отображается при запуске приложения.
     *
     * @param mainFrame Главное окно приложения.
     */
    private static void createLoadingScreen(JFrame mainFrame) {
        try {
            JPanel loadingPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            loadingPanel.setLayout(new GridBagLayout());

            GridBagConstraints gbc = new GridBagConstraints();
            setGridConstraints(gbc, 0, 0, -1, -1, GridBagConstraints.HORIZONTAL);
            gbc.insets = new Insets(0, 0, 64, 0);

            JLabel loadingLabel = initializeNewLabel("Mandelbrot", 96, 0);
            loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);
            loadingPanel.add(loadingLabel, gbc);

            progressBar = new JProgressBar(0, 1000);
            progressBar.setIndeterminate(false);
            progressBar.setPreferredSize(new Dimension(screenWidth / 2, 32));
            progressBar.setUI(new GradientProgressBarUI());
            progressBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.WHITE, 1),
                    BorderFactory.createEmptyBorder(1, 1, 1, 1)
            ));

            setGridConstraints(gbc, -1, 1, -1, -1, -1);
            gbc.insets = new Insets(0, 0, 0, 0);
            loadingPanel.add(progressBar, gbc);

            mainPanel.add(loadingPanel, "LoadingPanel");
            cardLayout.show(mainPanel, "LoadingPanel");
            mainFrame.setVisible(true);

            // Используем SwingWorker для имитации загрузки
            SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
                @Override
                protected Void doInBackground() {
                    try {
                        for (int i = 0; i <= 1000; i++) {
                            publish(i);
                            Thread.sleep(1); // Задержка для имитации загрузки
                        }
                    } catch (InterruptedException e) {
                        System.err.println("Ошибка в SwingWorker: " + e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }

                @Override
                protected void process(java.util.List<Integer> chunks) {
                    for (int progress : chunks) {
                        progressBar.setValue(progress);
                    }
                }

                @Override
                protected void done() {
                    createStartPanel();
                    cardLayout.show(mainPanel, "StartPanel");
                }
            };

            worker.execute();
        } catch (Exception e) {
            System.err.println("Ошибка при создании экрана загрузки: " + e.getMessage());
        }
    }

    /**
     * Создает стартовую панель с кнопками для шифрования и расшифровки изображений.
     */
    private static void createStartPanel() {
        try {
            JPanel startPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            startPanel.setLayout(new GridBagLayout());

            JButton encryptButton = initializeNewButton("Зашифровать изображение", buttonSize, buttonFont,
                    e -> {createEncryptBeginPanel();
                        cardLayout.show(mainPanel, "EncryptBeginPanel"); });
            JButton decryptButton = initializeNewButton("Расшифровать изображение", buttonSize, buttonFont,
                    e -> {createDecryptBeginPanel();
                        cardLayout.show(mainPanel, "DecryptBeginPanel"); });

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(10, 10, 10, 10);

            addComponent(startPanel, encryptButton, constraints, 0, 0, 1, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL);
            addComponent(startPanel, decryptButton, constraints, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL);

            mainPanel.add(startPanel,"StartPanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }



    /**
     * Создает панель для начала процесса шифрования изображения.
     */

    private static void createEncryptBeginPanel() {
        try {
            JPanel encryptBeginPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            encryptBeginPanel.setLayout(new GridBagLayout());
            JLabel fileLabel = initializeNewLabel("Выберите изображение для шифрования:", 32, 0);
            JButton uploadButton = initializeNewButton("Загрузить изображение", buttonSize, buttonFont,
                    e -> {
                        File selectedFile = selectImageFileForEncrypt(); // Выбираем файл
                        if (selectedFile != null) {
                            createEncryptLoadPanel(selectedFile.getAbsolutePath());
                            cardLayout.show(mainPanel, "EncryptLoadPanel");
                        } else {
                            JOptionPane.showMessageDialog(encryptBeginPanel, "Файл не выбран!",
                                    "Ошибка выбора файла", JOptionPane.ERROR_MESSAGE);
                        }
                    });
            JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "StartPanel");});

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(10, 10, 10, 10);

            addComponent(encryptBeginPanel, fileLabel, constraints, 0, 0, -1, GridBagConstraints.CENTER, -1);
            addComponent(encryptBeginPanel, uploadButton, constraints, -1, 1, -1, -1, -1);
            addComponent(encryptBeginPanel, backButton, constraints, -1, 2, -1, -1, -1);
            mainPanel.add(encryptBeginPanel, "EncryptBeginPanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    /**
     * Создает панель для загрузки изображения, которое будет зашифровано.
     */
    private static void createEncryptLoadPanel(String imagePath) {
        try {
            JPanel encryptLoadPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            encryptLoadPanel.setLayout(new GridBagLayout());
            JLabel imageLabel = initializeNewLabel("Загруженное для шифрования изображение:", 32, 0);

            ImageIcon imageIcon = new ImageIcon(imagePath);
            JPanel imageContainer = initializeImageContainer(imageIcon);

            JButton regenerateButton = initializeNewButton("Продолжить шифрование", buttonSize, buttonFont,
                    e -> {createEncryptModePanel();
                        cardLayout.show(mainPanel, "EncryptModePanel");});
            JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "EncryptBeginPanel");});

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(10, 10, 10, 10);

            addComponent(encryptLoadPanel, imageLabel, constraints, 1, 0, -1, GridBagConstraints.CENTER, -1);
            addComponent(encryptLoadPanel, imageContainer, constraints, 1, 1, 1, -1, GridBagConstraints.BOTH);
            setGridConstraints(constraints, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

            JPanel buttonPanel = new TransparentPanel(new GridBagLayout());
            GridBagConstraints buttonConstraints = new GridBagConstraints();
            buttonConstraints.insets = new Insets(5, 5, 5, 5);

            addComponent(buttonPanel, regenerateButton, buttonConstraints, 0, 0, -1, -1, -1);
            addComponent(buttonPanel, backButton, buttonConstraints, -1, 1, -1, -1, -1);
            encryptLoadPanel.add(buttonPanel, constraints);
            mainPanel.add(encryptLoadPanel, "EncryptLoadPanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    /**
     * Создает панель для выбора режима шифрования (автоматический или ручной).
     */
    private static void createEncryptModePanel() {
        try {
            JPanel encryptModePanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            encryptModePanel.setLayout(new GridBagLayout());

            JLabel fileLabel2 = initializeNewLabel("Выберите изображение-ключ:", 32, 0);
            JButton generateButton = initializeNewButton("Сгенерировать изображение-ключ", buttonSize, buttonFont,
                    e -> {createEncryptGeneratePanel();
                        cardLayout.show(mainPanel, "EncryptGeneratePanel");});
            JButton manualButton = initializeNewButton("Ввести параметры ключа вручную", buttonSize, buttonFont,
                    e -> {createManualEncryptionPanel();
                        cardLayout.show(mainPanel, "ManualEncryptionPanel");});
            JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "EncryptBeginPanel");});

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(10, 10, 10, 10);

            addComponent(encryptModePanel, fileLabel2, constraints, 0, 0, -1, GridBagConstraints.CENTER, -1);
            addComponent(encryptModePanel, generateButton, constraints, 0, 1, -1, -1, -1);
            addComponent(encryptModePanel, manualButton, constraints, 0, 2, -1, -1, -1);
            addComponent(encryptModePanel, backButton, constraints, 0, 3, -1, -1, -1);
            mainPanel.add(encryptModePanel, "EncryptModePanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    /**
     * Создает панель для генерации изображения-ключа для шифрования.
     */
    private static void createEncryptGeneratePanel() {
        try {
            JPanel encryptGeneratePanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            encryptGeneratePanel.setLayout(new GridBagLayout());
            JLabel imageLabel = initializeNewLabel("Ваше изображение-ключ:", 32, 0);

            JPanel imageContainer = new JPanel(new BorderLayout());
            JLabel loadingLabel = initializeNewLabel("Картинка генерируется...", 32, 0);
            imageContainer.add(loadingLabel, BorderLayout.CENTER);

            JButton regenerateButton = initializeNewButton("Сгенерировать заново", buttonSize, buttonFont,
                    e -> {
                        imageContainer.removeAll();
                        imageContainer.add(loadingLabel, BorderLayout.CENTER);
                        imageContainer.revalidate();
                        imageContainer.repaint();
                        generateImage(imageContainer);
                    });
            JButton manualButton = initializeNewButton("Сгенерировать заново вручную", buttonSize, buttonFont,
                    e -> {createManualEncryptionPanel();
                        cardLayout.show(mainPanel, "ManualEncryptionPanel");});
            JButton okayButton = initializeNewButton("Зашифровать изображение полностью", buttonSize, buttonFont,
                    e -> {BufferedImage imageToEncrypt = loadImage(getResourcesPath() + "input.png"); // Загружаем изображение
                        createEncryptFinalPanel(imageToEncrypt); // Передаем изображение в метод
                        cardLayout.show(mainPanel, "EncryptFinalPanel");});
            JButton partButton = initializeNewButton("Зашифровать часть изображения", buttonSize, buttonFont,
                    e -> {createEncryptPartialPanel();
                        cardLayout.show(mainPanel, "EncryptPartialPanel");});
            JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "EncryptModePanel");});

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(10, 10, 10, 10);

            addComponent(encryptGeneratePanel, imageLabel, constraints, 1, 0, -1, GridBagConstraints.CENTER, -1);
            addComponent(encryptGeneratePanel, imageContainer, constraints, 1, 1, 1, -1, GridBagConstraints.BOTH);
            setGridConstraints(constraints, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

            JPanel buttonPanel = new TransparentPanel(new GridBagLayout());
            GridBagConstraints buttonConstraints = new GridBagConstraints();
            buttonConstraints.insets = new Insets(5, 5, 5, 5);

            addComponent(buttonPanel, regenerateButton, buttonConstraints, 0, 0, -1, -1, -1);
            addComponent(buttonPanel, manualButton, buttonConstraints, -1, 1, -1, -1, -1);
            addComponent(buttonPanel, okayButton, buttonConstraints, -1, 2, -1, -1, -1);
            addComponent(buttonPanel, partButton, buttonConstraints, -1, 3, -1, -1, -1);
            addComponent(buttonPanel, backButton, buttonConstraints, -1, 4, -1, -1, -1);
            encryptGeneratePanel.add(buttonPanel, constraints);
            mainPanel.add(encryptGeneratePanel, "EncryptGeneratePanel");

            // Генерация изображения при первом открытии панели
            generateImage(imageContainer);
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    private static void generateImage(JPanel imageContainer) {
        // Добавляем индикатор загрузки
        JLabel loadingLabel = initializeNewLabel("Картинка генерируется...", 32, 0);
        imageContainer.removeAll();
        imageContainer.add(loadingLabel, BorderLayout.CENTER);
        imageContainer.revalidate();
        imageContainer.repaint();

        SwingWorker<BufferedImage, BufferedImage> worker = new SwingWorker<BufferedImage, BufferedImage>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                Mandelbrot mandelbrot = new Mandelbrot();
                return mandelbrot.generateImage();
            }

            @Override
            protected void process(List<BufferedImage> chunks) {
                // Обновляем изображение в EDT
                if (!chunks.isEmpty()) {
                    BufferedImage image = chunks.get(chunks.size() - 1);
                    ImageIcon imageIcon = new ImageIcon(image);
                    Image scaledImage = imageIcon.getImage().getScaledInstance(400, 300, Image.SCALE_SMOOTH);
                    ImageIcon scaledImageIcon = new ImageIcon(scaledImage);
                    imageContainer.removeAll();
                    imageContainer.add(new JLabel(scaledImageIcon), BorderLayout.CENTER);
                    imageContainer.revalidate();
                    imageContainer.repaint();
                }
            }

            @Override
            protected void done() {
                try {
                    BufferedImage image = get();
                    ImageIcon imageIcon = new ImageIcon(image);
                    Image scaledImage = imageIcon.getImage().getScaledInstance(400, 300, Image.SCALE_SMOOTH);
                    ImageIcon scaledImageIcon = new ImageIcon(scaledImage);
                    imageContainer.removeAll();
                    imageContainer.add(new JLabel(scaledImageIcon), BorderLayout.CENTER);
                    imageContainer.revalidate();
                    imageContainer.repaint();

                    // Сохраняем изображение после отображения
                    saveImageToResources(image);
                } catch (Exception e) {
                    System.err.println("Ошибка при генерации изображения: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private static void createEncryptGeneratePanelWithParams(String filePath) {
        try {
            JPanel encryptGeneratePanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            encryptGeneratePanel.setLayout(new GridBagLayout());
            JLabel imageLabel = initializeNewLabel("Ваше изображение-ключ:", 32, 0);

            JPanel imageContainer = new JPanel(new BorderLayout());
            JLabel loadingLabel = initializeNewLabel("Картинка генерируется...", 32, 0);
            imageContainer.add(loadingLabel, BorderLayout.CENTER);

            JButton manualButton = initializeNewButton("Сгенерировать заново вручную", buttonSize, buttonFont,
                    e -> {
                        createManualEncryptionPanel();
                        cardLayout.show(mainPanel, "ManualEncryptionPanel");
                    });
            JButton okayButton = initializeNewButton("Зашифровать изображение полностью", buttonSize, buttonFont,
                    e -> {
                        BufferedImage imageToEncrypt = loadImage(getResourcesPath() + "input.png"); // Загружаем изображение
                        createEncryptFinalPanel(imageToEncrypt); // Передаем изображение в метод
                        cardLayout.show(mainPanel, "EncryptFinalPanel");
                    });
            JButton partButton = initializeNewButton("Зашифровать часть изображения", buttonSize, buttonFont,
                    e -> {
                        createEncryptPartialPanel();
                        cardLayout.show(mainPanel, "EncryptPartialPanel");
                    });
            JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                    e -> {
                        cardLayout.show(mainPanel, "EncryptModePanel");
                    });

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(10, 10, 10, 10);

            addComponent(encryptGeneratePanel, imageLabel, constraints, 1, 0, -1, GridBagConstraints.CENTER, -1);
            addComponent(encryptGeneratePanel, imageContainer, constraints, 1, 1, 1, -1, GridBagConstraints.BOTH);
            setGridConstraints(constraints, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

            JPanel buttonPanel = new TransparentPanel(new GridBagLayout());
            GridBagConstraints buttonConstraints = new GridBagConstraints();
            buttonConstraints.insets = new Insets(5, 5, 5, 5);

            addComponent(buttonPanel, manualButton, buttonConstraints, 0, 0, -1, -1, -1);
            addComponent(buttonPanel, okayButton, buttonConstraints, -1, 1, -1, -1, -1);
            addComponent(buttonPanel, partButton, buttonConstraints, -1, 2, -1, -1, -1);
            addComponent(buttonPanel, backButton, buttonConstraints, -1, 3, -1, -1, -1);
            encryptGeneratePanel.add(buttonPanel, constraints);
            mainPanel.add(encryptGeneratePanel, "EncryptGeneratePanelWithParams");

            // Генерация изображения с использованием параметров из бинарного файла
            generateImageWithParams(imageContainer, filePath);
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    private static void generateImageWithParams(JPanel imageContainer, String filePath) {
        // Добавляем индикатор загрузки
        JLabel loadingLabel = initializeNewLabel("Картинка генерируется...", 32, 0);
        imageContainer.removeAll();
        imageContainer.add(loadingLabel, BorderLayout.CENTER);
        imageContainer.revalidate();
        imageContainer.repaint();

        SwingWorker<BufferedImage, BufferedImage> worker = new SwingWorker<BufferedImage, BufferedImage>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                Object[] params = BinaryFile.loadMandelbrotParamsFromBinaryFile(filePath);

                int startMandelbrotWidth = (int) params[0];
                int startMandelbrotHeight = (int) params[1];
                double ZOOM = (double) params[2];
                double offsetX = (double) params[3];
                double offsetY = (double) params[4];
                int MAX_ITER = (int) params[5];

                Mandelbrot mandelbrot = new Mandelbrot();
                return mandelbrot.generateImage(startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER);
            }

            @Override
            protected void process(List<BufferedImage> chunks) {
                // Обновляем изображение в EDT
                if (!chunks.isEmpty()) {
                    BufferedImage image = chunks.get(chunks.size() - 1);
                    ImageIcon imageIcon = new ImageIcon(image);
                    Image scaledImage = imageIcon.getImage().getScaledInstance(400, 300, Image.SCALE_SMOOTH);
                    ImageIcon scaledImageIcon = new ImageIcon(scaledImage);
                    imageContainer.removeAll();
                    imageContainer.add(new JLabel(scaledImageIcon), BorderLayout.CENTER);
                    imageContainer.revalidate();
                    imageContainer.repaint();
                }
            }

            @Override
            protected void done() {
                try {
                    BufferedImage image = get();
                    ImageIcon imageIcon = new ImageIcon(image);
                    Image scaledImage = imageIcon.getImage().getScaledInstance(400, 300, Image.SCALE_SMOOTH);
                    ImageIcon scaledImageIcon = new ImageIcon(scaledImage);
                    imageContainer.removeAll();
                    imageContainer.add(new JLabel(scaledImageIcon), BorderLayout.CENTER);
                    imageContainer.revalidate();
                    imageContainer.repaint();

                    // Сохраняем изображение после отображения
                    saveImageToResources(image);
                } catch (Exception e) {
                    System.err.println("Ошибка при генерации изображения: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    /**
     * Создает панель для выбора части изображения, которую нужно зашифровать.
     */
    private static void createEncryptPartialPanel() {
        try {
            JPanel encryptPartialPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            encryptPartialPanel.setLayout(new GridBagLayout());
            JLabel imageLabel = initializeNewLabel("Выберите область изображения для шифрования:", 32, 0);

            ImageIcon imageIcon = loadImageIcon(getResourcesPath() + "input.png");
            ImageContainerWithDrawing imageContainer = new ImageContainerWithDrawing(imageIcon);
            imageContainer.setPreferredSize(new Dimension(1024, 768));

            JButton regenerateButton = initializeNewButton("Продолжить шифрование", buttonSize, buttonFont,
                    e -> {
                        if (imageContainer.hasRectangle()) {
                            BufferedImage imageToEncrypt = loadImage(getResourcesPath() + "input.png"); // Загружаем изображение
                            createEncryptFinalPanel(imageToEncrypt); // Передаем изображение в метод
                            cardLayout.show(mainPanel, "EncryptFinalPanel");
                        } else {
                            JOptionPane.showMessageDialog(encryptPartialPanel, "Необходимо выделить зону шифрования!",
                                    "Ошибка выделения", JOptionPane.ERROR_MESSAGE);
                        }
                    });
            JButton againButton = initializeNewButton("Выбрать другую область", buttonSize, buttonFont,
                    e -> {});
            JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "EncryptGeneratePanel");});

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(10, 10, 10, 10);

            addComponent(encryptPartialPanel, imageLabel, constraints, 1, 0, -1, GridBagConstraints.CENTER, -1);
            addComponent(encryptPartialPanel, imageContainer, constraints, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH);
            setGridConstraints(constraints, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

            JPanel buttonPanel = new TransparentPanel(new GridBagLayout());
            GridBagConstraints buttonConstraints = new GridBagConstraints();
            buttonConstraints.insets = new Insets(5, 5, 5, 5);

            addComponent(buttonPanel, regenerateButton, buttonConstraints, 0, 0, -1, -1, -1);
            addComponent(buttonPanel, againButton, buttonConstraints, -1, 1, -1, -1, -1);
            addComponent(buttonPanel, backButton, buttonConstraints, -1, 2, -1, -1, -1);
            encryptPartialPanel.add(buttonPanel, constraints);
            mainPanel.add(encryptPartialPanel, "EncryptPartialPanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    /**
     * Создает панель для отображения зашифрованного изображения.
     */
    private static void createEncryptFinalPanel(BufferedImage image) {
        try {
            JPanel encryptFinalPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            encryptFinalPanel.setLayout(new GridBagLayout());
            JLabel imageLabel = initializeNewLabel("Полученное зашифрованное изображение:", 32, 0);

            //Шифруем изображение
            ImageEncrypt imageEncrypt = new ImageEncrypt();
            imageEncrypt.encryptWholeImage(image);

            // Получаем зашифрованное изображение
            BufferedImage encryptedImage = imageEncrypt.getEncryptedImage();

            JPanel imageContainer = initializeImageContainer(new ImageIcon(encryptedImage));

            JButton regenerateButton = initializeNewButton("Сгенерировать новый ключ", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "EncryptModePanel");});
            JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "StartPanel");});

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(10, 10, 10, 10);

            addComponent(encryptFinalPanel, imageLabel, constraints, 1, 0, -1, GridBagConstraints.CENTER, -1);
            addComponent(encryptFinalPanel, imageContainer, constraints, 1, 1, 1, -1, GridBagConstraints.BOTH);
            setGridConstraints(constraints, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

            JPanel buttonPanel = new TransparentPanel(new GridBagLayout());
            GridBagConstraints buttonConstraints = new GridBagConstraints();
            buttonConstraints.insets = new Insets(5, 5, 5, 5);

            addComponent(buttonPanel, regenerateButton, buttonConstraints, 0, 0, -1, -1, -1);
            addComponent(buttonPanel, backButton, buttonConstraints, -1, 1, -1, -1, -1);
            encryptFinalPanel.add(buttonPanel, constraints);
            mainPanel.add(encryptFinalPanel, "EncryptFinalPanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    /**
     * Создает панель для ручного ввода параметров шифрования.
     */
    private static void createManualEncryptionPanel() {
        try {
            JPanel manualEncryptPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            manualEncryptPanel.setLayout(new GridBagLayout());

            JLabel label = initializeNewLabel("Введите значения параметров:", 32, 0);
            JLabel widthLabel = initializeNewLabel("Ширина множества:", 20, 1);
            JTextField widthField = initializeNewTextField(20, fieldSize);
            JLabel heightLabel = initializeNewLabel("Высота множества:", 20, 1);
            JTextField heightField = initializeNewTextField(20, fieldSize);
            JLabel zoomLabel = initializeNewLabel("Масштаб множества:", 20, 1);
            JTextField zoomField = initializeNewTextField(20, fieldSize);
            JLabel iterationsLabel = initializeNewLabel("Число итераций:", 20, 1);
            JTextField iterationsField = initializeNewTextField(20, fieldSize);
            JLabel xLabel = initializeNewLabel("Смещение по оси X:", 20, 1);
            JTextField xField = initializeNewTextField(20, fieldSize);
            JLabel yLabel = initializeNewLabel("Смещение по оси Y:", 20, 1);
            JTextField yField = initializeNewTextField(20, fieldSize);

            JButton saveButton = initializeNewButton("Сохранить сгенерированный ключ", buttonSize, buttonFont,
                    e -> {
                        try {
                            int width = Integer.parseInt(widthField.getText());
                            int height = Integer.parseInt(heightField.getText());
                            double zoom = Double.parseDouble(zoomField.getText());
                            int iterations = Integer.parseInt(iterationsField.getText());
                            double x = Double.parseDouble(xField.getText());
                            double y = Double.parseDouble(yField.getText());

                            if (width <= 0 || height <= 0 || zoom <= 0 || iterations <= 0 ) {
                                throw new IllegalArgumentException("Некорректные данные");
                            }

                            // Сохранение параметров в бинарный файл
                            BinaryFile.saveMandelbrotParamsToBinaryFile(getResourcesPath() + "mandelbrot_params.bin", width, height, zoom, x, y, iterations);

                            // Используем SwingUtilities.invokeLater для перехода на новую панель
                            SwingUtilities.invokeLater(() -> {
                                createEncryptGeneratePanelWithParams(getResourcesPath() + "mandelbrot_params.bin");
                                cardLayout.show(mainPanel, "EncryptGeneratePanel");
                            });
                        } catch (NumberFormatException ex) {
                            JOptionPane.showMessageDialog(null, "Некорректный формат данных", "Ошибка", JOptionPane.ERROR_MESSAGE);
                        } catch (IllegalArgumentException ex) {
                            JOptionPane.showMessageDialog(null, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                        }
                    });
            JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "EncryptModePanel");});

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(5, 10, 5, 10);

            addComponent(manualEncryptPanel, label, constraints, 0, 0, 2, -1, -1);

            addComponent(manualEncryptPanel, widthLabel, constraints, 0, 1, 1, -1, -1);
            addComponent(manualEncryptPanel, widthField, constraints, 1, -1, -1, -1, -1);

            addComponent(manualEncryptPanel, heightLabel, constraints, 0, 2, 1, -1, -1);
            addComponent(manualEncryptPanel, heightField, constraints, 1, -1, -1, -1, -1);

            addComponent(manualEncryptPanel, zoomLabel, constraints, 0, 3, 1, -1, -1);
            addComponent(manualEncryptPanel, zoomField, constraints, 1, -1, -1, -1, -1);

            addComponent(manualEncryptPanel, iterationsLabel, constraints, 0, 4, 1, -1, -1);
            addComponent(manualEncryptPanel, iterationsField, constraints, 1, -1, -1, -1, -1);

            addComponent(manualEncryptPanel, xLabel, constraints, 0, 5, 1, -1, -1);
            addComponent(manualEncryptPanel, xField, constraints, 1, -1, -1, -1, -1);

            addComponent(manualEncryptPanel, yLabel, constraints, 0, 6, 1, -1, -1);
            addComponent(manualEncryptPanel, yField, constraints, 1, -1, -1, -1, -1);

            addComponent(manualEncryptPanel, saveButton, constraints, 0, 7, 2, -1, -1);
            addComponent(manualEncryptPanel, backButton, constraints, -1, 8, -1, -1, -1);

            mainPanel.add(manualEncryptPanel, "ManualEncryptionPanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    /**
     * Создает панель для начала процесса расшифровки изображения.
     */
    private static void createDecryptBeginPanel() {
        try {
            JPanel decryptBeginPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            decryptBeginPanel.setLayout(new GridBagLayout());

            JLabel fileLabel = initializeNewLabel("Выберите изображение для расшифрования:", 32, 0);
            JButton uploadButton = initializeNewButton("Загрузить изображение из файла", buttonSize, buttonFont,
                    e -> {createDecryptLoadPanel();
                        cardLayout.show(mainPanel, "DecryptLoadPanel");});
            JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "StartPanel");});

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(10, 10, 10, 10);

            addComponent(decryptBeginPanel, fileLabel, constraints, 0, 0, -1, -1, -1);
            addComponent(decryptBeginPanel, uploadButton, constraints, -1, 1, 1, -1, -1);
            addComponent(decryptBeginPanel, backButton, constraints, -1, 2, -1, -1, -1);
            mainPanel.add(decryptBeginPanel, "DecryptBeginPanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    /**
     * Создает панель для загрузки изображения, которое будет расшифровано.
     */
    private static void createDecryptLoadPanel() {
        try {
            JPanel decryptLoadPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            decryptLoadPanel.setLayout(new GridBagLayout());
            JLabel imageLabel = initializeNewLabel("Загруженное для расшифровки изображение:", 32, 0);

            ImageIcon imageIcon = new ImageIcon(selectImageFileForDecrypt());
            JPanel imageContainer = initializeImageContainer(imageIcon);

            JButton regenerateButton = initializeNewButton("Продолжить расшифровку", buttonSize, buttonFont,
                    e -> {createDecryptModePanel();
                        cardLayout.show(mainPanel, "DecryptModePanel");});
            JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "DecryptBeginPanel");});

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(10, 10, 10, 10);

            addComponent(decryptLoadPanel, imageLabel, constraints, 1, 0, -1, GridBagConstraints.CENTER, -1);
            addComponent(decryptLoadPanel, imageContainer, constraints, 1, 1, 1, -1, GridBagConstraints.BOTH);
            setGridConstraints(constraints, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

            JPanel buttonPanel = new TransparentPanel(new GridBagLayout());
            GridBagConstraints buttonConstraints = new GridBagConstraints();
            buttonConstraints.insets = new Insets(5, 5, 5, 5);

            addComponent(buttonPanel, regenerateButton, buttonConstraints, 0, 0, -1, -1, -1);
            addComponent(buttonPanel, backButton, buttonConstraints, -1, 1, -1, -1, -1);
            decryptLoadPanel.add(buttonPanel, constraints);
            mainPanel.add(decryptLoadPanel, "DecryptLoadPanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    /**
     * Создает панель для выбора режима расшифровки (автоматический или ручной).
     */
    private static void createDecryptModePanel() {
        try {
            JPanel decryptModePanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            decryptModePanel.setLayout(new GridBagLayout());
            JLabel fileLabel2 = initializeNewLabel("Выберите изображение-ключ:", 32, 0);

            JButton uploadButton = initializeNewButton("Загрузить изображение-ключ из файла", buttonSize, buttonFont,
                    e -> {createDecryptKeyPanel();
                        cardLayout.show(mainPanel, "DecryptKeyPanel");});
            JButton manualButton = initializeNewButton("Загрузить файл-ключ", buttonSize, buttonFont,
                    e -> {createDecryptFinalPanel();
                        cardLayout.show(mainPanel, "ManualDecryptionPanel");});
            JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "DecryptBeginPanel");});

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(10, 10, 10, 10);

            addComponent(decryptModePanel, fileLabel2, constraints, 0, 0, -1, -1, -1);
            addComponent(decryptModePanel, uploadButton, constraints, -1, 1, -1, -1, -1);
            addComponent(decryptModePanel, manualButton, constraints, -1, 2, -1, -1, -1);
            addComponent(decryptModePanel, backButton, constraints, -1, 3, -1, -1, -1);
            mainPanel.add(decryptModePanel, "DecryptModePanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    /**
     * Создает панель для загрузки изображения-ключа для расшифровки.
     */
    private static void createDecryptKeyPanel() {
        try {
            JPanel decryptKeyPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            decryptKeyPanel.setLayout(new GridBagLayout());
            JLabel imageLabel = initializeNewLabel("Ваше изображение-ключ:", 32, 0);

            String imagePath = getRandomImagePath();
            ImageIcon imageIcon = loadImageIcon(imagePath);
            JPanel imageContainer = initializeImageContainer(imageIcon);

            JButton regenerateButton = initializeNewButton("Загрузить другой ключ", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "DecryptModePanel");});
            JButton manualButton = initializeNewButton("Ввести другие параметры ключа", buttonSize, buttonFont,
                    e -> {createManualDecryptionPanel();
                        cardLayout.show(mainPanel, "ManualDecryptionPanel");});
            JButton okayButton = initializeNewButton("Расшифровать изображение", buttonSize, buttonFont,
                    e -> {createDecryptFinalPanel();
                        cardLayout.show(mainPanel, "DecryptFinalPanel");});
            JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "DecryptModePanel");});

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(10, 10, 10, 10);

            addComponent(decryptKeyPanel, imageLabel, constraints, 1, 0, -1, GridBagConstraints.CENTER, -1);
            addComponent(decryptKeyPanel, imageContainer, constraints, 1, 1, 1, -1, GridBagConstraints.BOTH);
            setGridConstraints(constraints, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

            JPanel buttonPanel = new TransparentPanel(new GridBagLayout());
            GridBagConstraints buttonConstraints = new GridBagConstraints();
            buttonConstraints.insets = new Insets(5, 5, 5, 5);

            addComponent(buttonPanel, regenerateButton, buttonConstraints, 0, 0, -1, -1, -1);
            addComponent(buttonPanel, manualButton, buttonConstraints, -1, 1, -1, -1, -1);
            addComponent(buttonPanel, okayButton, buttonConstraints, -1, 2, -1, -1, -1);
            addComponent(buttonPanel, backButton, buttonConstraints, -1, 3, -1, -1, -1);
            decryptKeyPanel.add(buttonPanel, constraints);
            mainPanel.add(decryptKeyPanel, "DecryptKeyPanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    /**
     * Создает панель для отображения расшифрованного изображения.
     */
    private static void createDecryptFinalPanel() {
        try {
            JPanel decryptFinalPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            decryptFinalPanel.setLayout(new GridBagLayout());
            JLabel imageLabel = initializeNewLabel("Расшифрованное изображение:", 32, 0);

            // Создаем экземпляр ImageDecrypt
            ImageDecrypt imageDecrypt = new ImageDecrypt();
            //
            //Попробовать убрать doInBackground и не дешифровывать в отдельном потоке, а прямо в том же
            //
            // Используем SwingWorker для выполнения дешифрования в фоновом потоке
            SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    imageDecrypt.decryptImage();
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        // Загружаем расшифрованное изображение
                        ImageIcon imageIcon = loadImageIcon(getResourcesPath() + "decrypted_image.png");
                        JPanel imageContainer = initializeImageContainer(imageIcon);

                        JButton reloadButton = initializeNewButton("Загрузить другое изображение", buttonSize, buttonFont,
                                e -> {cardLayout.show(mainPanel, "DecryptBeginPanel");});
                        JButton reloadKeyButton = initializeNewButton("Загрузить другой ключ", buttonSize, buttonFont,
                                e -> {cardLayout.show(mainPanel, "DecryptModePanel"); });
                        JButton saveButton = initializeNewButton("Сохранить изображение", buttonSize, buttonFont,
                                e -> {BufferedImage imageToEncrypt = loadImage(getResourcesPath() + "decrypted_image.png"); // Загружаем изображение
                                    createEncryptFinalPanel(imageToEncrypt); // Передаем изображение в метод
                                    cardLayout.show(mainPanel, "StartPanel");});
                        JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                                e -> {cardLayout.show(mainPanel, "StartPanel");});

                        GridBagConstraints constraints = new GridBagConstraints();
                        constraints.insets = new Insets(10, 10, 10, 10);

                        addComponent(decryptFinalPanel, imageLabel, constraints, 1, 0, -1, GridBagConstraints.CENTER, -1);
                        addComponent(decryptFinalPanel, imageContainer, constraints, 1, 1, 1, -1, GridBagConstraints.BOTH);
                        setGridConstraints(constraints, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

                        JPanel buttonPanel = new TransparentPanel(new GridBagLayout());
                        GridBagConstraints buttonConstraints = new GridBagConstraints();
                        buttonConstraints.insets = new Insets(5, 5, 5, 5);

                        addComponent(buttonPanel, reloadButton, buttonConstraints, 0, 0, -1, -1, -1);
                        addComponent(buttonPanel, reloadKeyButton, buttonConstraints, -1, 1, -1, -1, -1);
                        addComponent(buttonPanel, saveButton, buttonConstraints, -1, 2, -1, -1, -1);
                        addComponent(buttonPanel, backButton, buttonConstraints, -1, 3, -1, -1, -1);
                        decryptFinalPanel.add(buttonPanel, constraints);
                        mainPanel.add(decryptFinalPanel, "DecryptFinalPanel");

                        // Показываем новую панель
                        cardLayout.show(mainPanel, "DecryptFinalPanel");
                    } catch (Exception e) {
                        System.err.println("Ошибка при создании вкладки: " + e.getMessage());
                    }
                }
            };
            worker.execute();
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    /**
     * Создает панель для ручного ввода параметров расшифровки.
     */
    private static void createManualDecryptionPanel() {
        try {
            JPanel decryptKeyPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            decryptKeyPanel.setLayout(new GridBagLayout());
            JLabel imageLabel = initializeNewLabel("Ваше изображение-ключ:", 32, 0);

            ImageDecrypt imageDecrypt = new ImageDecrypt();
            imageDecrypt.decryptImage();

            ImageIcon imageIcon = loadImageIcon(imageDecrypt.loadDecryptedImage());
            JPanel imageContainer = initializeImageContainer(imageIcon);

            JButton regenerateButton = initializeNewButton("Загрузить другой ключ", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "DecryptModePanel");});
            JButton manualButton = initializeNewButton("Ввести другие параметры ключа", buttonSize, buttonFont,
                    e -> {createManualDecryptionPanel();
                        cardLayout.show(mainPanel, "ManualDecryptionPanel");});
            JButton okayButton = initializeNewButton("Расшифровать изображение", buttonSize, buttonFont,
                    e -> {createDecryptFinalPanel();
                        cardLayout.show(mainPanel, "DecryptFinalPanel");});
            JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                    e -> {cardLayout.show(mainPanel, "DecryptModePanel");});

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(10, 10, 10, 10);

            addComponent(decryptKeyPanel, imageLabel, constraints, 1, 0, -1, GridBagConstraints.CENTER, -1);
            addComponent(decryptKeyPanel, imageContainer, constraints, 1, 1, 1, -1, GridBagConstraints.BOTH);
            setGridConstraints(constraints, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

            JPanel buttonPanel = new TransparentPanel(new GridBagLayout());
            GridBagConstraints buttonConstraints = new GridBagConstraints();
            buttonConstraints.insets = new Insets(5, 5, 5, 5);

            addComponent(buttonPanel, regenerateButton, buttonConstraints, 0, 0, -1, -1, -1);
            addComponent(buttonPanel, manualButton, buttonConstraints, -1, 1, -1, -1, -1);
            addComponent(buttonPanel, okayButton, buttonConstraints, -1, 2, -1, -1, -1);
            addComponent(buttonPanel, backButton, buttonConstraints, -1, 3, -1, -1, -1);
            decryptKeyPanel.add(buttonPanel, constraints);
            mainPanel.add(decryptKeyPanel, "DecryptKeyPanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    /**
     * Инициализирует новое текстовое поле на заданное количество символов с заданным размером.
     *
     * @param my_columns Ожидаемое количество символов текстового поля.
     * @param my_size Размер текстового поля.
     * @return Инициализированное текстовое поле.
     */
    private static JTextField initializeNewTextField(int my_columns, Dimension my_size) {
        try {
            JTextField myField = new JTextField(my_columns);
            myField.setPreferredSize(my_size);
            myField.setHorizontalAlignment(JTextField.CENTER);

            return myField;
        } catch (Exception e) {
            System.err.println("Ошибка при инициализации текстового поля: " + e.getMessage());
            return null;
        }
    }

    /**
     * Инициализирует новую кнопку с заданным текстом, размером, шрифтом и обработчиком событий.
     *
     * @param my_text Текст на кнопке.
     * @param my_size Размер кнопки.
     * @param my_font Шрифт кнопки.
     * @param my_event Обработчик событий для кнопки.
     * @return Инициализированная кнопка.
     */
    private static JButton initializeNewButton(String my_text, Dimension my_size, Font my_font, ActionListener my_event) {
        try {
            JButton myButton = new CustomButton(my_text);
            myButton.setPreferredSize(my_size);
            myButton.setMinimumSize(my_size);
            myButton.setMaximumSize(my_size);
            myButton.setFont(my_font);

            myButton.addActionListener(e -> {
                try {
                    my_event.actionPerformed(e);
                } catch (Exception ex) {
                    System.err.println("Ошибка при обработке действия кнопки: " + ex.getMessage());
                    JOptionPane.showMessageDialog(null, "Произошла ошибка: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            });

            return myButton;
        } catch (Exception e) {
            System.err.println("Ошибка при инициализации кнопки: " + e.getMessage());
            return null;
        }
    }

    /**
     * Инициализирует новый текстовый элемент с заданным текстом, размером шрифта и флагом стиля (курсив/обычный текст).
     *
     * @param my_text Текст для элемента.
     * @param my_size Размер шрифта.
     * @param my_flag Флаг стиля (0 - курсив и жирный, 1 - обычный).
     * @return Инициализированный текстовый элемент.
     */
    private static JLabel initializeNewLabel(String my_text, int my_size, int my_flag) {
        try {
            JLabel myLabel = new JLabel(my_text);
            if (my_flag == 0)
                myLabel.setFont(new Font("Serif", Font.ITALIC + Font.BOLD, my_size));
            else if (my_flag == 1)
                myLabel.setFont(new Font("Serif", Font.PLAIN, my_size));

            myLabel.setForeground(Color.WHITE);
            return myLabel;
        } catch (Exception e) {
            System.err.println("Ошибка при инициализации текста: " + e.getMessage());
            return null;
        }
    }

    /**
     * Возвращает случайный путь к изображению из заданного массива.
     *
     * @return Случайный путь к изображению.
     */
    private static String getRandomImagePath() {
        try {
            Random random = new Random();
            String imageName = IMAGE_PATHS[random.nextInt(IMAGE_PATHS.length)];
            return getResourcesPath() + imageName;
        } catch (Exception e) {
            System.err.println("Ошибка при получении случайного пути к изображению: " + e.getMessage());
            return null;
        }
    }

    private static File selectImageFileForEncrypt() {
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Изображения", "jpg", "jpeg", "png", "bmp");
        fileChooser.setFileFilter(filter);
        int returnValue = fileChooser.showOpenDialog(null);
        if (returnValue == JFileChooser.APPROVE_OPTION) {

            File selectedFile = fileChooser.getSelectedFile();
            try {
                // Загружаем выбранное изображение
                BufferedImage image = ImageIO.read(selectedFile);
                if (image != null) {
                    // Сохраняем изображение в указанный путь
                    String savePath = getResourcesPath() + "input.png";
                    saveImageToFile(image, savePath);
                    System.out.println("Изображение сохранено в: " + savePath);
                } else {
                    System.err.println("Ошибка при загрузке изображения: " + selectedFile.getAbsolutePath());
                }
            } catch (IOException e) {
                System.err.println("Ошибка при загрузке изображения: " + e.getMessage());
            }
            return selectedFile;
        } else {
            return null;
        }
    }

    private static BufferedImage selectImageFileForDecrypt() {
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Изображения", "jpg", "jpeg", "png", "bmp");
        fileChooser.setFileFilter(filter);
        int returnValue = fileChooser.showOpenDialog(null);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                // Загружаем выбранное изображение
                BufferedImage image = ImageIO.read(selectedFile);
                if (image != null) {
                    // Сохраняем изображение в указанный путь
                    String savePath = getResourcesPath() + "encrypted_image.png";
                    saveImageToFile(image, savePath);
                    System.out.println("Изображение сохранено в: " + savePath);
                    return image;
                } else {
                    System.err.println("Ошибка при загрузке изображения: " + selectedFile.getAbsolutePath());
                }
            } catch (IOException e) {
                System.err.println("Ошибка при загрузке изображения: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Загружает изображение по заданному пути и возвращает его в виде ImageIcon.
     *
     * @param path Путь к изображению.
     * @return Загруженное изображение в виде ImageIcon.
     */
    private static ImageIcon loadImageIcon(String path) {
        try {
            File imageFile = new File(path);
            if (!imageFile.exists()) {
                throw new FileNotFoundException("Не удалось найти изображение по пути: " + path);
            }
            return new ImageIcon(path);
        } catch (Exception e) {
            System.err.println("Ошибка при загрузке изображения: " + e.getMessage());
            return null;
        }
    }

    private static ImageIcon loadImageIcon(BufferedImage image) {
        if (image == null) {
            System.err.println("Ошибка: изображение равно null");
            return null;
        }
        return new ImageIcon(image);
    }

    private static void saveImageToResources(BufferedImage image) {
        String savePath = getResourcesPath();
        File file = new File(savePath + "mandelbrot.png");

        try {
            ImageIO.write(image, "png", file);
            System.out.println("Изображение сохранено в папку resources: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении изображения: " + e.getMessage());
        }
    }

    private static void saveImageToFile(BufferedImage image, String filePath) {
        try {
            File outputFile = new File(filePath);
            ImageIO.write(image, "jpg", outputFile);
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении изображения: " + e.getMessage());
        }
    }

    private static BufferedImage loadImage(String filePath) {
        try {
            File imageFile = new File(filePath);
            if (!imageFile.exists()) {
                throw new FileNotFoundException("Не удалось найти изображение по пути: " + filePath);
            }
            return ImageIO.read(imageFile);
        } catch (Exception e) {
            System.err.println("Ошибка при загрузке изображения: " + e.getMessage());
            return null;
        }
    }

    /**
     * Устанавливает ограничения для GridBagConstraints.
     *
     * @param gbc Объект GridBagConstraints.
     * @param my_gridx Координата X в сетке.
     * @param my_gridy Координата Y в сетке.
     * @param my_gridwidth Ширина в сетке.
     * @param my_anchor Привязка компонента.
     * @param my_fill Заполнение компонента.
     */
    private static void setGridConstraints(GridBagConstraints gbc, int my_gridx, int my_gridy, int my_gridwidth, int my_anchor, int my_fill) {
        try {
            if (my_gridx != -1) {
                gbc.gridx = my_gridx;
            }
            if (my_gridy != -1) {
                gbc.gridy = my_gridy;
            }
            if (my_gridwidth != -1) {
                gbc.gridwidth = my_gridwidth;
            }
            if (my_anchor != -1) {
                gbc.anchor = my_anchor;
            }
            if (my_fill != -1) {
                gbc.fill = my_fill;
            }
        } catch (Exception e) {
            System.err.println("Ошибка при установке размеров макета: " + e.getMessage());
        }
    }

    /**
     * Добавляет компонент на панель с заданными ограничениями GridBagConstraints.
     *
     * @param my_panel Панель, на которую добавляется компонент.
     * @param my_component Компонент для добавления.
     * @param gbc Объект GridBagConstraints.
     * @param my_gridx Координата X в сетке.
     * @param my_gridy Координата Y в сетке.
     * @param my_gridwidth Ширина в сетке.
     * @param my_anchor Привязка компонента.
     * @param my_fill Заполнение компонента.
     */
    private static void addComponent(JPanel my_panel, Component my_component, GridBagConstraints gbc,
                                     int my_gridx, int my_gridy, int my_gridwidth, int my_anchor, int my_fill) {
        try {
            if (my_gridx != -1) {
                gbc.gridx = my_gridx;
            }
            if (my_gridy != -1) {
                gbc.gridy = my_gridy;
            }
            if (my_gridwidth != -1) {
                gbc.gridwidth = my_gridwidth;
            }
            if (my_anchor != -1) {
                gbc.anchor = my_anchor;
            }
            if (my_fill != -1) {
                gbc.fill = my_fill;
            }
            my_panel.add(my_component, gbc);
        } catch (Exception e) {
            System.err.println("Ошибка при добавлении компонента: " + e.getMessage());
        }
    }

    /**
     * Инициализирует контейнер для изображения с заданным ImageIcon.
     *
     * @param myIcon Изображение в виде ImageIcon.
     * @return Инициализированный контейнер для изображения.
     */
    private static JPanel initializeImageContainer(ImageIcon myIcon) {
        try {
            JPanel imageContainer = new JPanel(new BorderLayout());
            if (myIcon != null) {
                JLabel image = new JLabel(myIcon);
                imageContainer.add(image, BorderLayout.CENTER);
            } else {
                JLabel noImageLabel = new JLabel("Изображение не найдено");
                noImageLabel.setFont(new Font("Serif", Font.BOLD, 24));
                noImageLabel.setPreferredSize(new Dimension(1024, 720));
                noImageLabel.setForeground(Color.WHITE);
                noImageLabel.setHorizontalAlignment(JLabel.CENTER);
                noImageLabel.setVerticalAlignment(JLabel.CENTER);
                imageContainer.add(noImageLabel, BorderLayout.CENTER);
                imageContainer.setBackground(Color.BLACK);
            }

            imageContainer.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.WHITE, 2),
                    BorderFactory.createEmptyBorder(2, 2, 2, 2)
            ));

            return imageContainer;
        } catch (Exception e) {
            System.err.println("Ошибка при инициализации контейнера изображения: " + e.getMessage());
            return null;
        }
    }
}