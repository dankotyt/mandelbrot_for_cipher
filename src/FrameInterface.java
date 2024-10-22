import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class FrameInterface {

    public static void main(String[] args) {
        // Запуск первого окна
        createStartWindow();
    }

    private static void createStartWindow() {
        JFrame startFrame = new JFrame("Шифратор Мандельброта");
        startFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        startFrame.setSize(400, 300);
        startFrame.setLayout(new GridBagLayout());
        startFrame.setLocationRelativeTo(null); // Центрируем окно на экране

        JButton encryptButton = new JButton("Зашифровать");
        JButton decryptButton = new JButton("Расшифровать");

        // Устанавливаем размеры для кнопок
        Dimension buttonSize = new Dimension(200, 40); // Предпочтительный размер кнопки
        encryptButton.setPreferredSize(buttonSize);
        encryptButton.setMinimumSize(buttonSize);
        encryptButton.setMaximumSize(buttonSize);

        decryptButton.setPreferredSize(buttonSize);
        decryptButton.setMinimumSize(buttonSize);
        decryptButton.setMaximumSize(buttonSize);

        // Устанавливаем шрифт для кнопок
        Font buttonFont = new Font("Arial", Font.BOLD, 16); // Шрифт размером 16
        encryptButton.setFont(buttonFont);
        decryptButton.setFont(buttonFont);

        encryptButton.addActionListener(e -> {
            startFrame.dispose();
            createEncrypt1Window();
        });

        decryptButton.addActionListener(e -> {
            startFrame.dispose();
            createDecrypt1Window();
        });

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(10, 10, 10, 10);

        constraints.gridx = 0;
        constraints.gridy = 0;
        startFrame.add(encryptButton, constraints);

        constraints.gridy = 1;
        startFrame.add(decryptButton, constraints);

        startFrame.setVisible(true);
    }

    private static void createEncrypt1Window() {
        JFrame encrypt1Frame = new JFrame("Шифратор Мандельброта");
        encrypt1Frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        encrypt1Frame.setSize(400, 300);
        encrypt1Frame.setLayout(new GridBagLayout());
        encrypt1Frame.setLocationRelativeTo(null); // Центрируем окно на экране

        // Создаем метку для кнопок
        JLabel fileLabel = new JLabel("Выберите файл для шифрования:");

        JButton uploadButton = new JButton("Загрузить файл");
        JButton backButton = new JButton("Вернуться");

        // Устанавливаем размеры для кнопок
        Dimension buttonSize = new Dimension(200, 40); // Предпочтительный размер кнопки
        uploadButton.setPreferredSize(buttonSize);
        uploadButton.setMinimumSize(buttonSize);
        uploadButton.setMaximumSize(buttonSize);

        backButton.setPreferredSize(buttonSize);
        backButton.setMinimumSize(buttonSize);
        backButton.setMaximumSize(buttonSize);

        // Устанавливаем шрифт для кнопок
        Font buttonFont = new Font("Arial", Font.BOLD, 16); // Шрифт размером 16
        uploadButton.setFont(buttonFont);
        backButton.setFont(buttonFont);

        uploadButton.addActionListener(e -> {
            encrypt1Frame.dispose();
            createEncrypt2Window();
        });

        backButton.addActionListener(e -> {
            encrypt1Frame.dispose();
            createStartWindow();
        });

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(10, 10, 10, 10);

        // Добавляем метку "Выберите файл для расшифрования:"
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        encrypt1Frame.add(fileLabel, constraints);

        constraints.gridx = 1;
        constraints.gridy = 1;
        encrypt1Frame.add(uploadButton, constraints);

        constraints.gridy = 2;
        encrypt1Frame.add(backButton, constraints);

        encrypt1Frame.setVisible(true);
    }

    private static void createEncrypt2Window() {
        JFrame encrypt2Frame = new JFrame("Шифратор Мандельброта");
        encrypt2Frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        encrypt2Frame.setSize(400, 300);
        encrypt2Frame.setLayout(new GridBagLayout());
        encrypt2Frame.setLocationRelativeTo(null); // Центрируем окно на экране

        JButton generateButton = new JButton("Сгенерировать");
        JButton manualButton = new JButton("Ввести вручную");
        JButton backButton = new JButton("Вернуться");

        // Устанавливаем размеры для кнопок
        Dimension buttonSize = new Dimension(200, 40); // Предпочтительный размер кнопки
        generateButton.setPreferredSize(buttonSize);
        generateButton.setMinimumSize(buttonSize);
        generateButton.setMaximumSize(buttonSize);

        manualButton.setPreferredSize(buttonSize);
        manualButton.setMinimumSize(buttonSize);
        manualButton.setMaximumSize(buttonSize);

        backButton.setPreferredSize(buttonSize);
        backButton.setMinimumSize(buttonSize);
        backButton.setMaximumSize(buttonSize);

        // Устанавливаем шрифт для кнопок
        Font buttonFont = new Font("Arial", Font.BOLD, 16); // Шрифт размером 16
        generateButton.setFont(buttonFont);
        manualButton.setFont(buttonFont);
        backButton.setFont(buttonFont);

        generateButton.addActionListener(e -> {
            encrypt2Frame.dispose();
            // Логика для генерации
        });

        manualButton.addActionListener(e -> {
            encrypt2Frame.dispose();
            createManualEncryptionWindow();
        });

        backButton.addActionListener(e -> {
            encrypt2Frame.dispose();
            createEncrypt1Window();
        });

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(10, 10, 10, 10);

        constraints.gridx = 0;
        constraints.gridy = 0;
        encrypt2Frame.add(generateButton, constraints);

        constraints.gridy = 1;
        encrypt2Frame.add(manualButton, constraints);

        constraints.gridy = 2;
        encrypt2Frame.add(backButton, constraints);

        encrypt2Frame.setVisible(true);
    }

    private static void createManualEncryptionWindow() {
        JFrame manualEncryptFrame = new JFrame("Шифратор Мандельброта");
        manualEncryptFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        manualEncryptFrame.setSize(400, 300);
        manualEncryptFrame.setLayout(new GridBagLayout());
        manualEncryptFrame.setLocationRelativeTo(null); // Центрируем окно на экране

        // Создаем метки и поля ввода
        JLabel label = new JLabel("Введите значения:");
        JLabel zoomLabel = new JLabel("Zoom:");
        JTextField zoomField = new JTextField(10);
        JLabel iterationsLabel = new JLabel("Iterations:");
        JTextField iterationsField = new JTextField(10);
        JLabel xLabel = new JLabel("X:");
        JTextField xField = new JTextField(10);
        JLabel yLabel = new JLabel("Y:");
        JTextField yField = new JTextField(10);
        JButton saveButton = new JButton("Сохранить");
        JButton backButton = new JButton("Вернуться");

        // Устанавливаем размеры для полей ввода
        Dimension fieldSize = new Dimension(200, 30); // Предпочтительный размер поля ввода
        zoomField.setPreferredSize(fieldSize);
        iterationsField.setPreferredSize(fieldSize);
        xField.setPreferredSize(fieldSize);
        yField.setPreferredSize(fieldSize);

        // Добавляем слушатели событий для кнопок
        saveButton.addActionListener(e -> {
            manualEncryptFrame.dispose();
            // Логика для сохранения
        });

        backButton.addActionListener(e -> {
            manualEncryptFrame.dispose();
            createEncrypt2Window();
        });

        // Создаем GridBagConstraints для управления расположением компонентов
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 10, 5, 10); // Отступы вокруг компонентов

        // Добавляем метку "Введите значения:"
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        manualEncryptFrame.add(label, constraints);

        // Добавляем метки и поля ввода
        constraints.gridwidth = 1;
        constraints.gridy = 1;
        manualEncryptFrame.add(zoomLabel, constraints);

        constraints.gridx = 1;
        manualEncryptFrame.add(zoomField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        manualEncryptFrame.add(iterationsLabel, constraints);

        constraints.gridx = 1;
        manualEncryptFrame.add(iterationsField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 3;
        manualEncryptFrame.add(xLabel, constraints);

        constraints.gridx = 1;
        manualEncryptFrame.add(xField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 4;
        manualEncryptFrame.add(yLabel, constraints);

        constraints.gridx = 1;
        manualEncryptFrame.add(yField, constraints);

        // Добавляем кнопки
        constraints.gridx = 0;
        constraints.gridy = 5;
        constraints.gridwidth = 2;
        manualEncryptFrame.add(saveButton, constraints);

        constraints.gridy = 6;
        manualEncryptFrame.add(backButton, constraints);

        manualEncryptFrame.setVisible(true);
    }

    private static void createDecrypt1Window() {
        JFrame decrypt1Frame = new JFrame("Шифратор Мандельброта");
        decrypt1Frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        decrypt1Frame.setSize(400, 300);
        decrypt1Frame.setLayout(new GridBagLayout());
        decrypt1Frame.setLocationRelativeTo(null); // Центрируем окно на экране

        // Создаем метку для кнопок
        JLabel fileLabel = new JLabel("Выберите файл для расшифрования:");

        JButton uploadButton = new JButton("Загрузить файл");
        JButton backButton = new JButton("Вернуться");

        // Устанавливаем размеры для кнопок
        Dimension buttonSize = new Dimension(200, 40); // Предпочтительный размер кнопки
        uploadButton.setPreferredSize(buttonSize);
        uploadButton.setMinimumSize(buttonSize);
        uploadButton.setMaximumSize(buttonSize);

        backButton.setPreferredSize(buttonSize);
        backButton.setMinimumSize(buttonSize);
        backButton.setMaximumSize(buttonSize);

        // Устанавливаем шрифт для кнопок
        Font buttonFont = new Font("Arial", Font.BOLD, 16); // Шрифт размером 16
        uploadButton.setFont(buttonFont);
        backButton.setFont(buttonFont);

        uploadButton.addActionListener(e -> {
            decrypt1Frame.dispose();
            createEncrypt2Window();
        });

        backButton.addActionListener(e -> {
            decrypt1Frame.dispose();
            createStartWindow();
        });

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(10, 10, 10, 10);

        // Добавляем метку "Выберите файл для расшифрования:"
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        decrypt1Frame.add(fileLabel, constraints);

        // Добавляем кнопки
        constraints.gridwidth = 1;
        constraints.gridy = 1;
        decrypt1Frame.add(uploadButton, constraints);

        constraints.gridy = 2;
        decrypt1Frame.add(backButton, constraints);

        decrypt1Frame.setVisible(true);
    }

    private static void createDecrypt2Window() {
        JFrame decrypt2Frame = new JFrame("Шифратор Мандельброта");
        decrypt2Frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        decrypt2Frame.setSize(400, 300);
        decrypt2Frame.setLayout(new GridBagLayout());
        decrypt2Frame.setLocationRelativeTo(null); // Центрируем окно на экране

        JButton uploadButton = new JButton("Загрузить файл");
        JButton manualButton = new JButton("Ввести вручную");
        JButton backButton = new JButton("Вернуться");

        // Устанавливаем размеры для кнопок
        Dimension buttonSize = new Dimension(200, 40); // Предпочтительный размер кнопки
        uploadButton.setPreferredSize(buttonSize);
        uploadButton.setMinimumSize(buttonSize);
        uploadButton.setMaximumSize(buttonSize);

        manualButton.setPreferredSize(buttonSize);
        manualButton.setMinimumSize(buttonSize);
        manualButton.setMaximumSize(buttonSize);

        backButton.setPreferredSize(buttonSize);
        backButton.setMinimumSize(buttonSize);
        backButton.setMaximumSize(buttonSize);

        // Устанавливаем шрифт для кнопок
        Font buttonFont = new Font("Arial", Font.BOLD, 16); // Шрифт размером 16
        uploadButton.setFont(buttonFont);
        manualButton.setFont(buttonFont);
        backButton.setFont(buttonFont);

        uploadButton.addActionListener(e -> {
            decrypt2Frame.dispose();
            // Логика для загрузки
        });

        manualButton.addActionListener(e -> {
            decrypt2Frame.dispose();
            createManualDecryptionWindow();
        });

        backButton.addActionListener(e -> {
            decrypt2Frame.dispose();
            createDecrypt1Window();
        });

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(10, 10, 10, 10);

        constraints.gridx = 0;
        constraints.gridy = 0;
        decrypt2Frame.add(uploadButton, constraints);

        constraints.gridy = 1;
        decrypt2Frame.add(manualButton, constraints);

        constraints.gridy = 2;
        decrypt2Frame.add(backButton, constraints);

        decrypt2Frame.setVisible(true);
    }

    private static void createManualDecryptionWindow() {
        JFrame manualDecryptFrame = new JFrame("Шифратор Мандельброта");
        manualDecryptFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        manualDecryptFrame.setSize(400, 300);
        manualDecryptFrame.setLayout(new GridBagLayout());
        manualDecryptFrame.setLocationRelativeTo(null); // Центрируем окно на экране

        // Создаем метки и поля ввода
        JLabel label = new JLabel("Введите значения:");
        JLabel zoomLabel = new JLabel("Zoom:");
        JTextField zoomField = new JTextField(10);
        JLabel iterationsLabel = new JLabel("Iterations:");
        JTextField iterationsField = new JTextField(10);
        JLabel xLabel = new JLabel("X:");
        JTextField xField = new JTextField(10);
        JLabel yLabel = new JLabel("Y:");
        JTextField yField = new JTextField(10);
        JButton saveButton = new JButton("Сохранить");
        JButton backButton = new JButton("Вернуться");

        // Устанавливаем размеры для полей ввода
        Dimension fieldSize = new Dimension(200, 30); // Предпочтительный размер поля ввода
        zoomField.setPreferredSize(fieldSize);
        iterationsField.setPreferredSize(fieldSize);
        xField.setPreferredSize(fieldSize);
        yField.setPreferredSize(fieldSize);

        // Добавляем слушатели событий для кнопок
        saveButton.addActionListener(e -> {
            manualDecryptFrame.dispose();
            // Логика для сохранения
        });

        backButton.addActionListener(e -> {
            manualDecryptFrame.dispose();
            createDecrypt2Window();
        });

        // Создаем GridBagConstraints для управления расположением компонентов
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 10, 5, 10); // Отступы вокруг компонентов

        // Добавляем метку "Введите значения:"
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        manualDecryptFrame.add(label, constraints);

        // Добавляем метки и поля ввода
        constraints.gridwidth = 1;
        constraints.gridy = 1;
        manualDecryptFrame.add(zoomLabel, constraints);

        constraints.gridx = 1;
        manualDecryptFrame.add(zoomField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        manualDecryptFrame.add(iterationsLabel, constraints);

        constraints.gridx = 1;
        manualDecryptFrame.add(iterationsField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 3;
        manualDecryptFrame.add(xLabel, constraints);

        constraints.gridx = 1;
        manualDecryptFrame.add(xField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 4;
        manualDecryptFrame.add(yLabel, constraints);

        constraints.gridx = 1;
        manualDecryptFrame.add(yField, constraints);

        // Добавляем кнопки
        constraints.gridx = 0;
        constraints.gridy = 5;
        constraints.gridwidth = 2;
        manualDecryptFrame.add(saveButton, constraints);

        constraints.gridy = 6;
        manualDecryptFrame.add(backButton, constraints);

        manualDecryptFrame.setVisible(true);
    }
}