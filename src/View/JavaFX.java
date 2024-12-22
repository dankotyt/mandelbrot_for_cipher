package View;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.UnaryOperator;

import Model.Mandelbrot;
import Model.ImageEncrypt;
import Model.ImageDecrypt;
import Model.BinaryFile;

public class JavaFX extends Application {

    private StackPane mainPane;
    private Stage primaryStage;
    private Canvas canvas;
    private Task<Image> currentTask; // Ссылка на текущую задачу
    private static TextArea console;

    private Point2D startPoint;
    private Point2D endPoint;
    private boolean drawingRectangle = false;
    private List<Rectangle2D> rectangles = new ArrayList<>();
    private boolean rectangleSelected = false;

    public static String imageFilePath;
    public static String keyDecoderFilePath;

    private String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        mainPane = new StackPane();
        Scene scene = new Scene(mainPane, 1440, 800);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Шифр Мандельброта");

        // Добавление иконки приложения
        Image icon = new Image(getClass().getResourceAsStream("/elements/icon.png"));
        primaryStage.getIcons().add(icon);
        primaryStage.show();
        //createStartPanel();
        showLoadingScreen();
    }

    // Метод для создания градиента
    private LinearGradient createGradient() {
        return new LinearGradient(
                0, // Начальная точка по оси X (0 - слева)
                0, // Начальная точка по оси Y (0 - сверху)
                0, // Конечная точка по оси X (0 - слева)
                1, // Конечная точка по оси Y (1 - снизу)
                true, // Пропорциональный градиент
                CycleMethod.NO_CYCLE, // Не повторять градиент
                new Stop(0, Color.web("011324FF")), // Начальный цвет
                new Stop(1, Color.web("011A30FF"))  // Конечный цвет
        );
    }

    private void showLoadingScreen() {
        // Создаем заставку
        VBox loadingContainer = new VBox(20);
        loadingContainer.setAlignment(Pos.CENTER);
        loadingContainer.setPadding(new Insets(20));
        loadingContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Градиентный фон
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Создаем метку "Загрузка"
        Label loadingLabel = new Label("Mandelbrot Cipher");
        loadingLabel.setFont(Font.font("Intro Regular", 96));
        loadingLabel.setTextFill(Color.WHITE);

        // Создаем прогресс-бар
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(900);
        progressBar.setStyle("-fx-accent: #0065CA;"); // Зеленый цвет прогресс-бара

        // Добавляем элементы в контейнер
        loadingContainer.getChildren().addAll(loadingLabel, progressBar);

        // Добавляем заставку в основной контейнер
        mainPane.getChildren().add(loadingContainer);

        // Имитация загрузки
        Task<Void> loadingTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                for (int i = 0; i <= 100; i++) {
                    updateProgress(i, 100); // Обновляем прогресс
                    Thread.sleep(20); // Задержка для имитации загрузки
                }
                return null;
            }
        };

        // Привязываем прогресс-бар к Task
        progressBar.progressProperty().bind(loadingTask.progressProperty());

        // После завершения задачи переключаемся на startPanel
        loadingTask.setOnSucceeded(event -> {
            // Удаляем заставку
            mainPane.getChildren().remove(loadingContainer);

            // Показываем startPanel
            createStartPanel();
        });

        // Запускаем задачу
        new Thread(loadingTask).start();
    }

    private void createStartPanel() {
        GridPane startPanel = new GridPane();
        startPanel.setPadding(new Insets(10));
        startPanel.setHgap(10);
        startPanel.setVgap(0); // Увеличение вертикального отступа между кнопками

        // Загрузка изображений значков с использованием относительного пути
        Image lockImage = new Image(getClass().getResourceAsStream("/elements/icon_lock.png"));
        Image unlockImage = new Image(getClass().getResourceAsStream("/elements/icon_unlock.png"));

        // Создание ImageView для значков
        ImageView lockImageView = new ImageView(lockImage);
        lockImageView.setFitWidth(615); // Установка ширины иконки
        lockImageView.setFitHeight(127); // Установка высоты иконки

        ImageView unlockImageView = new ImageView(unlockImage);
        unlockImageView.setFitWidth(615); // Установка ширины иконки
        unlockImageView.setFitHeight(127); // Установка высоты иконки

        // Создание кнопок с иконками
        Button encryptButton = new Button("", lockImageView);
        encryptButton.setOnAction(e -> createEncryptBeginPanel());

        Button decryptButton = new Button("", unlockImageView);
        decryptButton.setOnAction(e -> createDecryptBeginPanel());

        // Применение стилей к кнопкам
        String buttonStyle = "-fx-background-color: transparent;";
        encryptButton.setStyle(buttonStyle);
        decryptButton.setStyle(buttonStyle);

        // Добавление отступов слева и справа для кнопок
        HBox encryptButtonBox = new HBox(encryptButton);
        encryptButtonBox.setPadding(new Insets(0, 0, 0, 250)); // Отступы справа и слева
        encryptButtonBox.setAlignment(Pos.CENTER);

        HBox decryptButtonBox = new HBox(decryptButton);
        decryptButtonBox.setPadding(new Insets(0, 250, 0, 0)); // Отступы справа и слева
        decryptButtonBox.setAlignment(Pos.CENTER);

        // Создание метки "Начало работы"
        Label startLabel = new Label("Начало работы");
        startLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 72px;");

        // Создание VBox для метки "Начало работы"
        VBox labelBox = new VBox(startLabel);
        labelBox.setAlignment(Pos.TOP_CENTER);

        // Создание VBox для кнопок
        VBox buttonBox = new VBox(0); // Установка вертикального отступа между кнопками
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(encryptButtonBox, decryptButtonBox);

        // Создание основного VBox для размещения метки и кнопок
        VBox mainBox = new VBox(40); // Установка вертикального отступа между меткой и кнопками
        mainBox.setAlignment(Pos.CENTER);
        mainBox.getChildren().addAll(labelBox, buttonBox);

        // Добавление основного VBox в GridPane
        startPanel.add(mainBox, 0, 0);

        // Установка градиентного фона для панели
        startPanel.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Выравнивание по центру
        startPanel.setAlignment(Pos.CENTER);

        mainPane.getChildren().add(startPanel);
    }

    public void createEncryptBeginPanel() {
        // Создание основного контейнера с фоном на весь экран
        BorderPane mainContainer = new BorderPane();
        // Установка градиентного фона для панели
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Создание текста "Шифратор"
        Label titleLabel = new Label("Шифратор");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 64px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowTitle).setPrefHeight(100); // Расстояние под текстом

        // Создание светло-серого прямоугольника
        Region lightGrayRect = new Region();
        lightGrayRect.setStyle("-fx-background-color: #494949;");
        lightGrayRect.setPrefSize(720, 540);
        lightGrayRect.setMinSize(720, 540); // Фиксируем минимальный размер
        lightGrayRect.setMaxSize(720, 540); // Фиксируем максимальный размер

        // Создание темно-серого прямоугольника
        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(640, 480);
        darkGrayRect.setMinSize(640, 480); // Фиксируем минимальный размер
        darkGrayRect.setMaxSize(640, 480); // Фиксируем максимальный размер
        darkGrayRect.setTranslateX(150); // Выглядывание на 50px

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowRect = new Region();
        spaceBelowRect.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowRect).setPrefHeight(113); // Расстояние под текстом

        // Создание кнопки "Выбрать файл"
        Button uploadButton = new Button("Выбрать файл");
        uploadButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular'; -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white; -fx-font-size: 28px;");
        uploadButton.setPrefSize(257, 68);
        uploadButton.setOnAction(e -> {
            String imagePath = selectImageFileForEncrypt(); // Получаем путь к файлу input.png
            if (imagePath != null) {
                createEncryptLoadPanel(imagePath); // Передаем путь к файлу в метод
            } else {
                JOptionPane.showMessageDialog(null, "Файл не выбран!",
                        "Ошибка выбора файла", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Создание кнопки "Вернуться назад" с иконкой
        Image backImage = new Image(getClass().getResourceAsStream("/elements/icon_back.png"));
        ImageView backImageView = new ImageView(backImage);
        backImageView.setFitWidth(50);
        backImageView.setFitHeight(50);

        Button backButton = new Button("", backImageView);
        backButton.setStyle("-fx-background-color: transparent;"); // Убираем обводку
        backButton.setPrefSize(50, 50); // Устанавливаем размер иконки
        backButton.setOnAction(e -> {
            mainPane.getChildren().clear();
            createStartPanel();
        });

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        // Размещение кнопки по центру светло-серого прямоугольника
        StackPane buttonContainer = new StackPane(uploadButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого и светло-серого прямоугольников
        StackPane rectContainer = new StackPane(darkGrayRect, lightGrayRect, buttonContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, titleLabel, spaceBelowTitle, rectContainer, spaceBelowRect);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topLeftContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    private void createEncryptLoadPanel(String imagePath) {
        // Создание основного контейнера с фоном на весь экран
        BorderPane mainContainer = new BorderPane();
        // Установка градиентного фона для панели
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Создание текста "Загруженное для шифрования изображение:"
        Label titleLabel = new Label("Загруженное для шифрования изображение:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 40px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowTitle).setPrefHeight(100); // Расстояние под текстом

        // Создание темно-серого прямоугольника
        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(640, 480);
        darkGrayRect.setMinSize(640, 480); // Фиксируем минимальный размер
        darkGrayRect.setMaxSize(640, 480); // Фиксируем максимальный размер
        darkGrayRect.setTranslateX(150); // Выглядывание на 50px

        // Загрузка изображения
        Image image = new Image("file:" + imagePath);
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(720);
        imageView.setFitHeight(540);

        // Создание кнопки "Продолжить шифрование"
        Button regenerateButton = new Button("Продолжить шифрование");
        regenerateButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular'; -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white; -fx-font-size: 20px;");
        regenerateButton.setPrefSize(290, 48);
        regenerateButton.setOnAction(e -> {
            createEncryptModePanel();
        });

        // Создание отдельного HBox для кнопки "Продолжить шифрование"
        HBox buttonContainer = new HBox(regenerateButton);
        buttonContainer.setAlignment(Pos.CENTER_LEFT); // Выравнивание кнопки по левому краю
        buttonContainer.setPadding(new Insets(0, 0, 0, 0)); // Отступ справа для визуального разделения

        // Создание кнопки "Вернуться назад" с иконкой
        Image backImage = new Image(getClass().getResourceAsStream("/elements/icon_back.png"));
        ImageView backImageView = new ImageView(backImage);
        backImageView.setFitWidth(50);
        backImageView.setFitHeight(50);

        Button backButton = new Button("", backImageView);
        backButton.setStyle("-fx-background-color: transparent;"); // Убираем обводку
        backButton.setPrefSize(50, 50); // Устанавливаем размер иконки
        backButton.setOnAction(e -> {
            mainPane.getChildren().clear();
            createEncryptBeginPanel();
        });

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        // Размещение темно-серого прямоугольника с изображением
        HBox contentContainer = new HBox(imageView);
        contentContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с контейнером
        StackPane rectContainer = new StackPane(darkGrayRect, contentContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, titleLabel, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topLeftContainer);

        // Создание контейнера для кнопок "Продолжить шифрование"
        HBox bottomButtonsContainer = new HBox(20, regenerateButton); // Кнопки в одной строке
        bottomButtonsContainer.setAlignment(Pos.CENTER); // Выравнивание по центру
        bottomButtonsContainer.setPadding(new Insets(0, 0, 30, 0)); // Отступ снизу

        // Установка контейнера с кнопками внизу
        mainContainer.setBottom(bottomButtonsContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    public BorderPane createEncryptModePanel() {
        // Создание основного контейнера с фоном на весь экран
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Создание текста "Выберите изображение-ключ:"
        Label titleLabel = new Label("Выберите изображение-ключ:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 40px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowTitle).setPrefHeight(100); // Расстояние под текстом

        // Создание темно-серого прямоугольника
        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(720, 540);
        darkGrayRect.setMinSize(720, 540); // Фиксируем минимальный размер
        darkGrayRect.setMaxSize(720, 540); // Фиксируем максимальный размер

        // Загрузка изображения из папки temp
        ImageView imageView = loadInputImageFromTemp();
        if (imageView == null) {
            // Возвращаем пустой контейнер или сообщение об ошибке
            return mainContainer;
        }
        imageView.setFitWidth(640);
        imageView.setFitHeight(480);
        imageView.setTranslateX(150); // Выглядывание на 50px

        // Создание кнопки "Сгенерировать изображение-ключ" с многострочным текстом и выравниванием по центру
        Button generateButton = new Button();
        Text generateText = new Text("Сгенерировать\nизображение-ключ");
        generateText.setFill(Color.WHITE);
        TextFlow generateTextFlow = new TextFlow(generateText);
        generateTextFlow.setTextAlignment(TextAlignment.CENTER); // Выравнивание текста по центру
        generateButton.setGraphic(generateTextFlow); // Устанавливаем TextFlow как графический элемент кнопки
        generateButton.setAlignment(Pos.CENTER); // Выравнивание кнопки по центру
        generateButton.setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-border-color: white; " +
                        "-fx-border-width: 5px; " +
                        "-fx-border-radius: 10px; " +
                        "-fx-text-fill: white; " + // Устанавливаем цвет текста на белый
                        "-fx-font-size: 20px;"
        );
        generateButton.setPrefSize(247, 78);
        generateButton.setOnAction(e -> {
            createEncryptGeneratePanel();
        });

        // Создание кнопки "Ввести параметры ключа вручную" с многострочным текстом и выравниванием по центру
        Button manualButton = new Button();
        Text manualText = new Text("Ввести параметры\nключа вручную");
        manualText.setFill(Color.WHITE);
        TextFlow manualTextFlow = new TextFlow(manualText);
        manualTextFlow.setTextAlignment(TextAlignment.CENTER); // Выравнивание текста по центру
        manualButton.setGraphic(manualTextFlow); // Устанавливаем TextFlow как графический элемент кнопки
        manualButton.setAlignment(Pos.CENTER); // Выравнивание кнопки по центру
        manualButton.setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-border-color: white; " +
                        "-fx-border-width: 5px; " +
                        "-fx-border-radius: 10px; " +
                        "-fx-text-fill: white; " + // Устанавливаем цвет текста на белый
                        "-fx-font-size: 20px;"
        );
        manualButton.setPrefSize(247, 78);
        manualButton.setOnAction(e -> {
            createManualEncryptionPanel();
        });

        // Создание кнопки "Ввести параметры ключа вручную" с многострочным текстом и выравниванием по центру
        Button chooseButton = new Button();
        Text chooseText = new Text("Выбрать\nизображение-ключ");
        chooseText.setFill(Color.WHITE);
        TextFlow chooseTextFlow = new TextFlow(chooseText);
        chooseTextFlow.setTextAlignment(TextAlignment.CENTER); // Выравнивание текста по центру
        chooseButton.setGraphic(chooseTextFlow); // Устанавливаем TextFlow как графический элемент кнопки
        chooseButton.setAlignment(Pos.CENTER); // Выравнивание кнопки по центру
        chooseButton.setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-border-color: white; " +
                        "-fx-border-width: 5px; " +
                        "-fx-border-radius: 10px; " +
                        "-fx-text-fill: white; " + // Устанавливаем цвет текста на белый
                        "-fx-font-size: 20px;"
        );
        chooseButton.setPrefSize(247, 78);
        chooseButton.setOnAction(e -> {
            File selectedFile = selectMandelbrotForEncrypt();
            if (selectedFile != null) {
                String getImagePath = selectedFile.getAbsolutePath();
                createChoosenMandelbrotPanel(getImagePath);
            } else {
                JOptionPane.showMessageDialog(null, "Файл не выбран!",
                        "Ошибка выбора файла", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Создание кнопки "Вернуться назад" с иконкой
        Image backImage = new Image(getClass().getResourceAsStream("/elements/icon_back.png"));
        ImageView backImageView = new ImageView(backImage);
        backImageView.setFitWidth(50);
        backImageView.setFitHeight(50);

        Button backButton = new Button("", backImageView);
        backButton.setStyle("-fx-background-color: transparent;"); // Убираем обводку
        backButton.setPrefSize(50, 50); // Устанавливаем размер иконки
        backButton.setOnAction(e -> {
            mainPane.getChildren().clear();
            createEncryptBeginPanel();
        });

        // Размещение кнопок в контейнере
        VBox buttonContainer = new VBox(20, generateButton, manualButton, chooseButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с кнопками
        StackPane rectContainer = new StackPane(darkGrayRect, buttonContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение изображения и темно-серого прямоугольника
        StackPane contentContainer = new StackPane(imageView, rectContainer);
        contentContainer.setAlignment(Pos.CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowRect = new Region();
        spaceBelowRect.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowRect).setPrefHeight(163); // Расстояние под текстом

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, titleLabel, spaceBelowTitle, contentContainer, spaceBelowRect);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topLeftContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);

        return mainContainer;
    }

    private void createEncryptGeneratePanel() {
        // Создание основного контейнера с фоном на весь экран
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Создание текста "Ваше изображение-ключ:"
        Label titleLabel = new Label("Ваше изображение-ключ:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 40px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowTitle).setPrefHeight(100); // Расстояние под текстом

        // Создание темно-серого прямоугольника
        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(720, 540);
        darkGrayRect.setMinSize(720, 540); // Фиксируем минимальный размер
        darkGrayRect.setMaxSize(720, 540); // Фиксируем максимальный размер

        // Загрузка изображения из папки temp
        ImageView imageView = loadInputImageFromTemp();
        if (imageView == null) {
            // Возвращаем пустой контейнер или сообщение об ошибке
            return;
        }
        imageView.setFitWidth(640);
        imageView.setFitHeight(480);
        imageView.setTranslateX(150); // Выглядывание на 150px

        // Создание текста "Картинка генерируется..."
        Label loadingLabel = new Label("Картинка генерируется...");
        loadingLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        loadingLabel.setAlignment(Pos.CENTER);

        // Создание контейнера для текста "Картинка генерируется..."
        StackPane loadingContainer = new StackPane(loadingLabel);
        loadingContainer.setAlignment(Pos.CENTER);

        // Создание кнопки "Сгенерировать заново вручную"
        Image writeParamsImage = new Image(getClass().getResourceAsStream("/elements/icon_writeParams.png"));
        ImageView writeParamsImageView = new ImageView(writeParamsImage);
        writeParamsImageView.setFitWidth(50); // Устанавливаем ширину иконки
        writeParamsImageView.setFitHeight(50); // Устанавливаем высоту иконки
        Button manualButton = new Button("", writeParamsImageView); // Иконка вместо текста
        manualButton.setStyle("-fx-background-color: transparent;");
        manualButton.setPrefSize(50, 50);
        manualButton.setOnAction(e -> {
            cancelCurrentTask(); // Отменяем текущую задачу
            createManualEncryptionPanel();
        });

        // Создание кнопки "Зашифровать изображение полностью" с иконкой
        Image nextImage = new Image(getClass().getResourceAsStream("/elements/icon_next.png"));
        ImageView nextImageView = new ImageView(nextImage);
        nextImageView.setFitWidth(50); // Устанавливаем ширину иконки
        nextImageView.setFitHeight(50); // Устанавливаем высоту иконки
        Button okayButton = new Button("", nextImageView); // Иконка вместо текста
        okayButton.setStyle("-fx-background-color: transparent;");
        okayButton.setPrefSize(50, 50);
        okayButton.setOnAction(e -> {
            // Загружаем изображение из папки temp
            BufferedImage imageToEncrypt = loadBufferedImageFromTemp(getTempPath() + "input.png");
            if (imageToEncrypt != null) {
                createEncryptFinalPanel(imageToEncrypt); // Передаем изображение в метод
            } else {
                System.err.println("Ошибка: изображение input.png не найдено в папке temp.");
            }
        });

        // Создание кнопки "Вернуться назад" с иконкой
        Image backImage = new Image(getClass().getResourceAsStream("/elements/icon_back.png"));
        ImageView backImageView = new ImageView(backImage);
        backImageView.setFitWidth(50);
        backImageView.setFitHeight(50);

        Button backButton = new Button("", backImageView);
        backButton.setStyle("-fx-background-color: transparent;"); // Убираем обводку
        backButton.setPrefSize(50, 50); // Устанавливаем размер иконки
        backButton.setOnAction(e -> {
            cancelCurrentTask(); // Отменяем текущую задачу
            mainPane.getChildren().clear();
            createEncryptModePanel();
        });

        // Добавление иконки swap
        Image swapImage = new Image(getClass().getResourceAsStream("/elements/icon_swap.png"));
        ImageView swapImageView = new ImageView(swapImage);
        swapImageView.setFitWidth(50);
        swapImageView.setFitHeight(50);

        Button swapButton = new Button("", swapImageView);
        swapButton.setStyle("-fx-background-color: transparent;");
        swapButton.setOnAction(e -> {
            panelForChooseAreaForEncrypt();
        });

        // Создание кнопки "Сгенерировать заново" с иконкой
        Button regenerateButton = new Button();
        Image repeatIcon = new Image(getClass().getResourceAsStream("/elements/icon_repeat.png"));
        ImageView repeatIconView = new ImageView(repeatIcon);
        repeatIconView.setFitWidth(50); // Установите нужный размер иконки
        repeatIconView.setFitHeight(50);
        regenerateButton.setGraphic(repeatIconView);
        regenerateButton.setStyle("-fx-background-color: transparent;");
        regenerateButton.setPrefSize(50, 50);
        regenerateButton.setOnAction(e -> {
            cancelCurrentTask(); // Отменяем текущую задачу
            loadingContainer.getChildren().clear();
            loadingContainer.getChildren().add(loadingLabel);
            generateImage(loadingContainer, regenerateButton, manualButton, okayButton, swapButton);
        });

        // Размещение кнопок в контейнере
        HBox buttonContainer = new HBox(20, regenerateButton, manualButton, okayButton, swapButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с картинкой
        StackPane rectContainer = new StackPane(imageView, darkGrayRect, loadingContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, titleLabel, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Создание блока-подсказки
        VBox hintBox = createHintBoxForEncrypt();
        hintBox.setAlignment(Pos.CENTER); // Выравниваем hintBox по вертикали
        hintBox.setPrefSize(300, 230); // Фиксированный размер hintBox (150x200)
        hintBox.setMinSize(300, 230); // Минимальный размер hintBox (150x200)
        hintBox.setMaxSize(300, 230); // Максимальный размер hintBox (150x200)

        // Создание консоли для отображения отладочных сообщений
        TextArea console = new TextArea();
        console.setEditable(false);
        console.setStyle("-fx-font-family: monospace; -fx-font-size: 14px; -fx-text-fill: black;");
        console.setPrefSize(400, 200); // Консоль под hintBox
        console.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE); // Позволяем консоли растягиваться

        // Устанавливаем консоль в статическую переменную
        setConsole(console);

        // Создаем VBox для консоли
        VBox consoleContainer = new VBox(console);
        consoleContainer.setAlignment(Pos.CENTER); // Выравниваем консоль по центру
        consoleContainer.setFillWidth(true); // Позволяем консоли растягиваться по ширине
        consoleContainer.setPrefSize(400,200);
        consoleContainer.setMinSize(400, 200);
        consoleContainer.setMaxSize(400, 200);
        consoleContainer.setStyle("-fx-background-color: #1e1e1e;");

        // Создаем GridPane для размещения hintBox и contentBox
        GridPane centerGrid = new GridPane();
        centerGrid.setAlignment(Pos.CENTER); // Выравниваем по центру
        centerGrid.add(hintBox, 0, 0); // hintBox в первой колонке
        centerGrid.add(contentBox, 1, 0); // contentBox во второй колонке
        centerGrid.setPadding(new Insets(0, 20, 0, 60)); // Отступ слева 60px

        // Создаем VBox для обертки hintBox и consoleContainer
        VBox hintAndConsoleContainer = new VBox(hintBox, consoleContainer);
        hintAndConsoleContainer.setSpacing(30); // Устанавливаем расстояние между hintBox и consoleContainer
        hintAndConsoleContainer.setAlignment(Pos.CENTER); // Выравниваем по центру

        // Добавляем hintAndConsoleContainer в GridPane
        centerGrid.add(hintAndConsoleContainer, 0, 0); // hintAndConsoleContainer в первой колонке

        // Устанавливаем отступы между hintBox и консолью
        VBox.setVgrow(consoleContainer, Priority.ALWAYS); // Позволяем консоли растягиваться по высоте
        // Устанавливаем пропорции для колонок
        ColumnConstraints hintColumn = new ColumnConstraints();
        hintColumn.setPrefWidth(300); // Фиксированная ширина для hintBox
        hintColumn.setHgrow(Priority.NEVER); // hintBox не растягивается

        ColumnConstraints contentColumn = new ColumnConstraints();
        contentColumn.setHgrow(Priority.ALWAYS); // contentBox занимает всё доступное пространство

        centerGrid.getColumnConstraints().addAll(hintColumn, contentColumn);

        // Установка центрального контента
        mainContainer.setCenter(centerGrid);

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topLeftContainer);

        // Создание контейнера для кнопок под прямоугольником
        VBox bottomContainer = new VBox(buttonContainer);
        bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
        bottomContainer.setPadding(new Insets(0, 0, 30, 0)); // Уменьшаем отступ снизу

        // Установка контейнера внизу
        mainContainer.setBottom(bottomContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);

        // Генерация изображения при первом открытии панели
        generateImage(loadingContainer, regenerateButton, manualButton, okayButton, swapButton);
    }

    private VBox createHintBoxForEncrypt() {
        // Создание прямоугольника с белой обводкой
        Region hintBackground = new Region();
        hintBackground.setStyle("-fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-background-color: transparent;");
        hintBackground.setPrefSize(300, 260); // Увеличиваем высоту для размещения всего текста

        // Создание заголовка "Подсказка"
        Text hintTitle = new Text("Подсказка");
        hintTitle.setStyle("-fx-font-family: 'Intro Regular'; -fx-font-weight: bold;");
        hintTitle.setFill(Color.WHITE);
        hintTitle.setFont(Font.font("Intro Regular", 18));

        // Создание текста подсказки
        Text hintText = new Text("Для шифрования части изображения - перейдите\nна исходную картинку и выделите желаемую часть");
        hintText.setFill(Color.WHITE); // Устанавливаем белый цвет текста
        hintText.setFont(Font.font("Intro Regular", 14)); // Устанавливаем шрифт и размер текста

        // Добавление иконок
        Image repeatIcon = new Image(getClass().getResourceAsStream("/elements/icon_repeat.png"));
        ImageView repeatIconView = new ImageView(repeatIcon);
        repeatIconView.setFitWidth(30);
        repeatIconView.setFitHeight(30);

        Image manualIcon = new Image(getClass().getResourceAsStream("/elements/icon_writeParams.png"));
        ImageView manualIconView = new ImageView(manualIcon);
        manualIconView.setFitWidth(30);
        manualIconView.setFitHeight(30);

        Image nextIcon = new Image(getClass().getResourceAsStream("/elements/icon_next.png"));
        ImageView nextIconView = new ImageView(nextIcon);
        nextIconView.setFitWidth(30);
        nextIconView.setFitHeight(30);

        Image swapIcon = new Image(getClass().getResourceAsStream("/elements/icon_swap.png"));
        ImageView swapIconView = new ImageView(swapIcon);
        swapIconView.setFitWidth(30);
        swapIconView.setFitHeight(30);

        // Создание контейнера для текста и иконок
        VBox hintContent = new VBox(5);
        hintContent.getChildren().addAll(
                hintTitle, // Заголовок "Подсказка"
                new HBox(repeatIconView, new Text(" \n - пересоздать ключ;")),
                new HBox(manualIconView, new Text(" \n - создать ключ по заданным вручную параметрам;")),
                new HBox(nextIconView, new Text(" \n - зашифровать изображение полностью;")),
                new HBox(swapIconView, new Text(" \n - свапнуть картинку;")),
                hintText // Дополнительный текст
        );
        hintContent.setAlignment(Pos.BASELINE_LEFT);

        // Устанавливаем стили для текста и иконок
        for (Node node : hintContent.getChildren()) {
            if (node instanceof HBox) {
                // Получаем текстовый элемент из HBox
                Text text = (Text) ((HBox) node).getChildren().get(1);
                // Устанавливаем белый цвет текста
                text.setFill(Color.WHITE);
                // Устанавливаем шрифт и размер текста
                text.setFont(Font.font("Intro Regular", 14));
            }
        }

        // Размещение hintContent внутри hintBackground
        StackPane hintContainer = new StackPane(hintBackground, hintContent);
        StackPane.setAlignment(hintContent, Pos.CENTER);
        StackPane.setMargin(hintContent, new Insets(10)); // Добавляем отступы внутри hintBackground

        // Возвращаем контейнер
        return new VBox(hintContainer);
    }

    private void generateImage(StackPane imageContainer, Button... buttonsToDisable) {
        // Отключаем кнопки перед началом генерации
        for (Button button : buttonsToDisable) {
            button.setDisable(true);
        }

        Task<Image> generateImageTask = new Task<Image>() {
            @Override
            protected Image call() throws Exception {
                Mandelbrot mandelbrot = new Mandelbrot();
                BufferedImage mandelbrotImage = mandelbrot.generateImage();
                return SwingFXUtils.toFXImage(mandelbrotImage, null);
            }
        };

        generateImageTask.setOnSucceeded(e -> {
            Image mandelbrotImage = generateImageTask.getValue();
            ImageView imageView = new ImageView(mandelbrotImage);
            imageView.setFitWidth(720);
            imageView.setFitHeight(540);
            imageContainer.getChildren().clear();
            imageContainer.getChildren().add(imageView);

            // Включаем кнопки после успешного завершения
            for (Button button : buttonsToDisable) {
                button.setDisable(false);
            }
        });

        generateImageTask.setOnFailed(e -> {
            System.err.println("Ошибка при генерации изображения: " + generateImageTask.getException().getMessage());

            // Включаем кнопки в случае сбоя
            for (Button button : buttonsToDisable) {
                button.setDisable(false);
            }
        });

        new Thread(generateImageTask).start();
    }

    private void cancelCurrentTask() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel(); // Отменяем текущую задачу
        }
    }

    private void panelForChooseAreaForEncrypt() {
        // Создание основного контейнера с фоном на весь экран
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Создание текста "Ваше изображение-ключ:"
        Label titleLabel = new Label("Шифруемое изображение:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 40px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowTitle).setPrefHeight(100); // Расстояние под текстом

        // Загрузка изображения input.png из папки temp
        String tempPath = getTempPath();
        String inputFilePath = tempPath + "input.png";
        File inputFile = new File(inputFilePath);
        if (!inputFile.exists() || !inputFile.canRead()) {
            System.err.println("Файл изображения не найден: " + inputFilePath);
            return; // Возвращаем пустой контейнер или сообщение об ошибке
        }
        Image image = new Image(inputFile.toURI().toString());
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(720);
        imageView.setFitHeight(540);

        // Загрузка изображения mandelbrot.png из папки temp
        String mandelbrotFilePath = tempPath + "mandelbrot.png";
        File mandelbrotFile = new File(mandelbrotFilePath);
        if (!mandelbrotFile.exists() || !mandelbrotFile.canRead()) {
            System.err.println("Файл изображения не найден: " + mandelbrotFilePath);
            return; // Возвращаем пустой контейнер или сообщение об ошибке
        }
        Image mandelbrotImage = new Image(mandelbrotFile.toURI().toString());
        ImageView mandelbrotImageView = new ImageView(mandelbrotImage);
        mandelbrotImageView.setFitWidth(640);
        mandelbrotImageView.setFitHeight(480);
        mandelbrotImageView.setTranslateX(150); // Выглядывание на 150px

        // Создание кнопки "Зашифровать изображение полностью" с иконкой
        Image encryptWholeImage = new Image(getClass().getResourceAsStream("/elements/icon_encryptWhole.png"));
        ImageView encryptWholeImageView = new ImageView(encryptWholeImage);
        encryptWholeImageView.setFitWidth(50); // Устанавливаем ширину иконки
        encryptWholeImageView.setFitHeight(50); // Устанавливаем высоту иконки
        Button encryptWholeButton = new Button("", encryptWholeImageView); // Иконка вместо текста
        encryptWholeButton.setStyle("-fx-background-color: transparent;");
        encryptWholeButton.setPrefSize(50, 50);
        encryptWholeButton.setOnAction(e -> {
            try {
                // Загружаем изображение из папки temp
                BufferedImage imageToEncrypt = loadBufferedImageFromTemp(inputFilePath);
                if (imageToEncrypt == null) {
                    System.err.println("Файл изображения не найден: " + inputFilePath);
                    return;
                }
                createEncryptFinalPanel(imageToEncrypt); // Передаем изображение в метод
            } catch (Exception ex) {
                System.err.println("Произошла ошибка: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        // Создание кнопки "Вернуться назад" с иконкой
        Image backImage = new Image(getClass().getResourceAsStream("/elements/icon_back.png"));
        ImageView backImageView = new ImageView(backImage);
        backImageView.setFitWidth(50);
        backImageView.setFitHeight(50);

        Button backButton = new Button("", backImageView);
        backButton.setStyle("-fx-background-color: transparent;"); // Убираем обводку
        backButton.setPrefSize(50, 50); // Устанавливаем размер иконки
        backButton.setOnAction(e -> {
            cancelCurrentTask(); // Отменяем текущую задачу
            clearRectangles();
            mainPane.getChildren().clear();
            createEncryptModePanel();
        });

        // Добавление иконки swap
        Image swapImage = new Image(getClass().getResourceAsStream("/elements/icon_swap.png"));
        ImageView swapImageView = new ImageView(swapImage);
        swapImageView.setFitWidth(50);
        swapImageView.setFitHeight(50);

        Button swapButton = new Button("", swapImageView);
        swapButton.setStyle("-fx-background-color: transparent;");
        swapButton.setOnAction(e -> {
            createChoosenMandelbrotPanel(getTempPath() + "mandelbrot.png");
        });

        StackPane imageContainer = new StackPane(imageView); // Canvas поверх изображения

        // Создаем слой для рисования прямоугольников
        Canvas canvas = new Canvas(720, 540); // Размер canvas соответствует отображаемому изображению
        //Canvas canvas = new Canvas(imageView.getFitWidth(), imageView.getFitHeight());
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Коэффициенты масштабирования для перевода координат с 720x540 на 1024x768
        double scaleX = 1024.0 / 720.0;
        double scaleY = 768.0 / 540.0;

        canvas.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                startPoint = new Point2D(e.getX(), e.getY());
                drawingRectangle = true;
                System.out.println("Начало выделения: " + startPoint);
            } else if (e.isSecondaryButtonDown()) {
                rectangles.clear();
                rectangleSelected = false;
                gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                System.out.println("Очистка прямоугольников");
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.PRIMARY && !rectangleSelected) {
                endPoint = new Point2D(e.getX(), e.getY());
                drawingRectangle = false;
                System.out.println("Конечная точка: " + endPoint); // Отладочный вывод
                if (startPoint != null && endPoint != null && !startPoint.equals(endPoint)) {
                    double x = Math.min(startPoint.getX(), endPoint.getX());
                    double y = Math.min(startPoint.getY(), endPoint.getY());
                    double width = Math.abs(startPoint.getX() - endPoint.getX());
                    double height = Math.abs(startPoint.getY() - endPoint.getY());

                    // Преобразуем координаты и размеры в оригинальный размер изображения
                    double originalX = x * scaleX;
                    double originalY = y * scaleY;
                    double originalWidth = width * scaleX;
                    double originalHeight = height * scaleY;

                    rectangles.add(new Rectangle2D(originalX, originalY, originalWidth, originalHeight));
                    rectangleSelected = true;
                    System.out.println("Прямоугольник добавлен: " + rectangles.get(rectangles.size() - 1));
                }
                gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                drawRectangles(gc, scaleX, scaleY);
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (e.isPrimaryButtonDown() && !rectangleSelected) {
                endPoint = new Point2D(e.getX(), e.getY());
                gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                drawRectangles(gc, scaleX, scaleY);
                if (drawingRectangle && startPoint != null && endPoint != null) {
                    double x = Math.min(startPoint.getX(), endPoint.getX());
                    double y = Math.min(startPoint.getY(), endPoint.getY());
                    double width = Math.abs(startPoint.getX() - endPoint.getX());
                    double height = Math.abs(startPoint.getY() - endPoint.getY());
                    gc.setStroke(Color.web("#eb3471"));
                    gc.strokeRect(x, y, width, height);
                }
            }
        });

        canvas.setFocusTraversable(true);
        canvas.requestFocus();

        imageContainer.getChildren().add(canvas);

        // Создание кнопки "Продолжить шифрование" с иконкой
        Image encryptPartImage = new Image(getClass().getResourceAsStream("/elements/icon_encryptPart.png"));
        ImageView encryptPartImageView = new ImageView(encryptPartImage);
        encryptPartImageView.setFitWidth(50); // Устанавливаем ширину иконки
        encryptPartImageView.setFitHeight(50); // Устанавливаем высоту иконки
        Button encryptPartButton = new Button("", encryptPartImageView); // Иконка вместо текста
        encryptPartButton.setStyle("-fx-background-color: transparent;");
        encryptPartButton.setPrefSize(50, 50);
        encryptPartButton.setOnAction(e -> {
            if (hasRectangle()) {
                Rectangle2D selectedRectangle = getSelectedRectangle();
                System.out.println("Выделенный прямоугольник: " + selectedRectangle);

                // Загружаем изображение из папки temp
                BufferedImage imageToEncrypt = loadBufferedImageFromTemp(inputFilePath);
                if (imageToEncrypt == null) {
                    System.err.println("Файл изображения не найден: " + inputFilePath);
                    return;
                }

                BufferedImage selectedSubImage = imageToEncrypt.getSubimage(
                        (int) selectedRectangle.getMinX(),
                        (int) selectedRectangle.getMinY(),
                        (int) selectedRectangle.getWidth(),
                        (int) selectedRectangle.getHeight());

                BufferedImage encryptedImage = ImageEncrypt.encryptSelectedArea(imageToEncrypt, selectedSubImage,
                        (int) selectedRectangle.getMinX(),
                        (int) selectedRectangle.getMinY(),
                        (int) selectedRectangle.getWidth(),
                        (int) selectedRectangle.getHeight());

                createEncryptFinalPanelForSelectedImage(encryptedImage);
                clearRectangles();
            } else {
                showErrorMessage("Необходимо выделить зону шифрования!");
            }
        });

        // Создание кнопки "Выбрать другую область" с иконкой
        Image resetPartImage = new Image(getClass().getResourceAsStream("/elements/icon_resetPart.png"));
        ImageView resetPartImageView = new ImageView(resetPartImage);
        resetPartImageView.setFitWidth(50);
        resetPartImageView.setFitHeight(50);
        Button resetPartButton = new Button("", resetPartImageView);
        resetPartButton.setStyle("-fx-background-color: transparent;");
        resetPartButton.setPrefSize(50, 50);
        resetPartButton.setOnAction(e -> {
            rectangles.clear();
            rectangleSelected = false;
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight()); // Очищаем canvas
            clearRectangles();
        });


        // Размещение кнопок в контейнере
        HBox buttonContainer = new HBox(20, encryptWholeButton, encryptPartButton, resetPartButton, swapButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с картинкой
        StackPane rectContainer = new StackPane(mandelbrotImageView, imageContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, titleLabel, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Создание блока-подсказки
        VBox hintBox = createHintBoxForEncryptPart();
        hintBox.setAlignment(Pos.CENTER); // Выравниваем hintBox по вертикали
        hintBox.setPrefSize(300, 230); // Фиксированный размер hintBox (150x200)
        hintBox.setMinSize(300, 230); // Минимальный размер hintBox (150x200)
        hintBox.setMaxSize(300, 230); // Максимальный размер hintBox (150x200)

        // Создаем GridPane для размещения hintBox и contentBox
        GridPane centerGrid = new GridPane();
        centerGrid.setAlignment(Pos.CENTER); // Выравниваем по центру
        centerGrid.add(hintBox, 0, 0); // hintBox в первой колонке
        centerGrid.add(contentBox, 1, 0); // contentBox во второй колонке
        centerGrid.setPadding(new Insets(0, 20, 0, 60)); // Отступ слева 60px

        // Устанавливаем пропорции для колонок
        ColumnConstraints hintColumn = new ColumnConstraints();
        hintColumn.setPrefWidth(300); // Фиксированная ширина для hintBox
        hintColumn.setHgrow(Priority.NEVER); // hintBox не растягивается

        ColumnConstraints contentColumn = new ColumnConstraints();
        contentColumn.setHgrow(Priority.ALWAYS); // contentBox занимает всё доступное пространство

        centerGrid.getColumnConstraints().addAll(hintColumn, contentColumn);

        // Установка центрального контента
        mainContainer.setCenter(centerGrid);

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topLeftContainer);

        // Создание контейнера для кнопок под прямоугольником
        VBox bottomContainer = new VBox(buttonContainer);
        bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
        bottomContainer.setPadding(new Insets(0, 0, 30, 0)); // Уменьшаем отступ снизу

        // Установка контейнера внизу
        mainContainer.setBottom(bottomContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    private VBox createHintBoxForEncryptPart() {
        // Создание прямоугольника с белой обводкой
        Region hintBackground = new Region();
        hintBackground.setStyle("-fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-background-color: transparent;");
        hintBackground.setPrefSize(370, 230);
        hintBackground.setMaxSize(370, 230);
        hintBackground.setMinSize(370, 230);

        // Создание заголовка "Подсказка"
        Text hintTitle = new Text("Подсказка");
        hintTitle.setStyle("-fx-font-family: 'Intro Regular'; -fx-font-weight: bold;");
        hintTitle.setFill(Color.WHITE);
        hintTitle.setFont(Font.font("Intro Regular", 18));

        // Создание текста подсказки
        Text hintText = new Text("Для шифрования части изображения - выделите\nжелаемую часть.");
        hintText.setFill(Color.WHITE); // Устанавливаем белый цвет текста
        hintText.setFont(Font.font("Intro Regular", 14)); // Устанавливаем шрифт и размер текста

        // Иконки для кнопок
        Image encryptWholeIcon = new Image(getClass().getResourceAsStream("/elements/icon_encryptWhole.png"));
        ImageView encryptWholeIconView = new ImageView(encryptWholeIcon);
        encryptWholeIconView.setFitWidth(30);
        encryptWholeIconView.setFitHeight(30);

        Image resetPartIcon = new Image(getClass().getResourceAsStream("/elements/icon_resetPart.png"));
        ImageView resetPartIconView = new ImageView(resetPartIcon);
        resetPartIconView.setFitWidth(30);
        resetPartIconView.setFitHeight(30);

        Image encryptPartIcon = new Image(getClass().getResourceAsStream("/elements/icon_encryptPart.png"));
        ImageView encryptPartIconView = new ImageView(encryptPartIcon);
        encryptPartIconView.setFitWidth(30);
        encryptPartIconView.setFitHeight(30);

        // Создание контейнера для текста и иконок
        VBox hintContent = new VBox(5);
        hintContent.getChildren().addAll(
                hintTitle, // Заголовок "Подсказка"
                new HBox(encryptWholeIconView, new Text(" \n - зашифровать изображение полностью;")),
                new HBox(encryptPartIconView, new Text(" \n - зашифровать выделенную часть\n  (после выделения области);")),
                new HBox(resetPartIconView, new Text(" \n - сбрасывает ранее выделенную часть;")),
                hintText // Дополнительный текст
        );
        hintContent.setAlignment(Pos.BASELINE_LEFT);

        // Устанавливаем стили для текста и иконок
        for (Node node : hintContent.getChildren()) {
            if (node instanceof HBox) {
                // Получаем текстовый элемент из HBox
                Text text = (Text) ((HBox) node).getChildren().get(1);
                // Устанавливаем белый цвет текста
                text.setFill(Color.WHITE);
                // Устанавливаем шрифт и размер текста
                text.setFont(Font.font("Intro Regular", 14));
            }
        }

        // Размещение hintContent внутри hintBackground
        StackPane hintContainer = new StackPane(hintBackground, hintContent);
        StackPane.setAlignment(hintContent, Pos.CENTER);
        StackPane.setMargin(hintContent, new Insets(15)); // Добавляем отступы внутри hintBackground

        // Возвращаем контейнер
        return new VBox(hintContainer);
    }

    private void createEncryptGeneratePanelWithParams(String filePath) {
        // Создание основного контейнера с градиентным фоном
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Создание текста "Ваше изображение-ключ:"
        Label titleLabel = new Label("Ваше изображение-ключ:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 40px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowTitle).setPrefHeight(100); // Расстояние под текстом

        // Создание темно-серого прямоугольника
        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(720, 540);
        darkGrayRect.setMinSize(720, 540); // Фиксируем минимальный размер
        darkGrayRect.setMaxSize(720, 540); // Фиксируем максимальный размер

        // Загрузка изображения из папки temp
        ImageView imageView = loadInputImageFromTemp();
        if (imageView == null) {
            // Возвращаем пустой контейнер или сообщение об ошибке
            return;
        }
        imageView.setFitWidth(640);
        imageView.setFitHeight(480);
        imageView.setTranslateX(150); // Выглядывание на 150px

        // Создание текста "Картинка генерируется..."
        Label loadingLabel = new Label("Картинка генерируется...");
        loadingLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        loadingLabel.setAlignment(Pos.CENTER);

        // Создание контейнера для текста "Картинка генерируется..."
        StackPane loadingContainer = new StackPane(loadingLabel);
        loadingContainer.setAlignment(Pos.CENTER);

//        StackPane imageContainer = new StackPane(imageView);
//        imageContainer.setAlignment(Pos.CENTER);

        // Создание кнопки "Сгенерировать заново вручную" с иконкой
        Image writeParamsImage = new Image(getClass().getResourceAsStream("/elements/icon_writeParams.png"));
        ImageView writeParamsImageView = new ImageView(writeParamsImage);
        writeParamsImageView.setFitWidth(50); // Устанавливаем ширину иконки
        writeParamsImageView.setFitHeight(50); // Устанавливаем высоту иконки
        Button manualButton = new Button("", writeParamsImageView); // Иконка вместо текста
        manualButton.setStyle("-fx-background-color: transparent;");
        manualButton.setPrefSize(50, 50);
        manualButton.setOnAction(e -> {
            cancelCurrentTask(); // Отменяем текущую задачу
            createManualEncryptionPanel();
        });

        // Создание кнопки "Зашифровать изображение полностью" с иконкой
        Image nextImage = new Image(getClass().getResourceAsStream("/elements/icon_next.png"));
        ImageView nextImageView = new ImageView(nextImage);
        nextImageView.setFitWidth(50); // Устанавливаем ширину иконки
        nextImageView.setFitHeight(50); // Устанавливаем высоту иконки
        Button okayButton = new Button("", nextImageView); // Иконка вместо текста
        okayButton.setStyle("-fx-background-color: transparent;");
        okayButton.setPrefSize(50, 50);
        okayButton.setOnAction(e -> {
            // Загружаем изображение из папки temp
            BufferedImage imageToEncrypt = loadBufferedImageFromTemp(getTempPath() + "input.png");
            if (imageToEncrypt != null) {
                createEncryptFinalPanel(imageToEncrypt); // Передаем изображение в метод
            } else {
                System.err.println("Ошибка: изображение input.png не найдено в папке temp.");
            }
        });

        // Создание кнопки "Вернуться назад" с иконкой
        Image backImage = new Image(getClass().getResourceAsStream("/elements/icon_back.png"));
        ImageView backImageView = new ImageView(backImage);
        backImageView.setFitWidth(50);
        backImageView.setFitHeight(50);
        Button backButton = new Button("", backImageView);
        backButton.setStyle("-fx-background-color: transparent;"); // Убираем обводку
        backButton.setPrefSize(50, 50); // Устанавливаем размер иконки
        backButton.setOnAction(e -> {
            cancelCurrentTask(); // Отменяем текущую задачу
            mainPane.getChildren().clear();
            createEncryptModePanel();
        });

        // Создание кнопки "Сгенерировать заново" с иконкой
        Button regenerateButton = new Button();
        Image repeatIcon = new Image(getClass().getResourceAsStream("/elements/icon_repeat.png"));
        ImageView repeatIconView = new ImageView(repeatIcon);
        repeatIconView.setFitWidth(50); // Установите нужный размер иконки
        repeatIconView.setFitHeight(50);
        regenerateButton.setGraphic(repeatIconView);
        regenerateButton.setStyle("-fx-background-color: transparent;");
        regenerateButton.setPrefSize(50, 50);
        regenerateButton.setOnAction(e -> {
            cancelCurrentTask(); // Отменяем текущую задачу
            loadingContainer.getChildren().clear();
            loadingContainer.getChildren().add(loadingLabel);
            createEncryptGeneratePanel();
        });

        // Добавление иконки swap
        Image swapImage = new Image(getClass().getResourceAsStream("/elements/icon_swap.png"));
        ImageView swapImageView = new ImageView(swapImage);
        swapImageView.setFitWidth(50);
        swapImageView.setFitHeight(50);

        Button swapButton = new Button("", swapImageView);
        swapButton.setStyle("-fx-background-color: transparent;");
        swapButton.setOnAction(e -> {
            panelForChooseAreaForEncrypt();
        });

        // Размещение кнопок в контейнере
        HBox buttonContainer = new HBox(20, regenerateButton, manualButton, okayButton, swapButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с картинкой
        StackPane rectContainer = new StackPane(imageView, darkGrayRect, loadingContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, titleLabel, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Создание блока-подсказки
        VBox hintBox = createHintBoxForEncrypt();
        hintBox.setAlignment(Pos.CENTER); // Выравниваем hintBox по вертикали
        hintBox.setPrefSize(300, 230); // Фиксированный размер hintBox (150x200)
        hintBox.setMinSize(300, 230); // Минимальный размер hintBox (150x200)
        hintBox.setMaxSize(300, 230); // Максимальный размер hintBox (150x200)

        // Создаем GridPane для размещения hintBox и contentBox
        GridPane centerGrid = new GridPane();
        centerGrid.setAlignment(Pos.CENTER); // Выравниваем по центру
        centerGrid.add(hintBox, 0, 0); // hintBox в первой колонке
        centerGrid.add(contentBox, 1, 0); // contentBox во второй колонке
        centerGrid.setPadding(new Insets(0, 20, 0, 60)); // Отступ слева 60px

        // Устанавливаем пропорции для колонок
        ColumnConstraints hintColumn = new ColumnConstraints();
        hintColumn.setPrefWidth(300); // Фиксированная ширина для hintBox
        hintColumn.setHgrow(Priority.NEVER); // hintBox не растягивается

        ColumnConstraints contentColumn = new ColumnConstraints();
        contentColumn.setHgrow(Priority.ALWAYS); // contentBox занимает всё доступное пространство

        centerGrid.getColumnConstraints().addAll(hintColumn, contentColumn);

        // Установка центрального контента
        mainContainer.setCenter(centerGrid);

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topLeftContainer);

        // Создание контейнера для кнопок под прямоугольником
        VBox bottomContainer = new VBox(buttonContainer);
        bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
        bottomContainer.setPadding(new Insets(0, 0, 30, 0)); // Уменьшаем отступ снизу

        // Установка контейнера внизу
        mainContainer.setBottom(bottomContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);

        // Генерация изображения с использованием параметров из бинарного файла
        generateImageWithParams(loadingContainer, filePath);
    }

    private void generateImageWithParams(StackPane imageContainer, String filePath) {
        Label loadingLabel = new Label("Картинка генерируется...");
        imageContainer.getChildren().clear();
        imageContainer.getChildren().add(loadingLabel);

        Task<Image> generateImageTask = new Task<Image>() {
            @Override
            protected Image call() throws Exception {
                Object[] params = BinaryFile.loadMandelbrotParamsFromBinaryFile(filePath);

                int startMandelbrotWidth = (int) params[0];
                int startMandelbrotHeight = (int) params[1];
                double ZOOM = (double) params[2];
                double offsetX = (double) params[3];
                double offsetY = (double) params[4];
                int MAX_ITER = (int) params[5];

                Mandelbrot mandelbrot = new Mandelbrot();
                BufferedImage mandelbrotImage = mandelbrot.generateImage(startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER);
                return SwingFXUtils.toFXImage(mandelbrotImage, null);
            }
        };

        generateImageTask.setOnSucceeded(e -> {
            Image mandelbrotImage = generateImageTask.getValue();
            ImageView imageView = new ImageView(mandelbrotImage);
            imageView.setFitWidth(720);
            imageView.setFitHeight(540);
            imageContainer.getChildren().clear();
            imageContainer.getChildren().add(imageView);

            // Сохраняем изображение после отображения
            saveMandelbrotToTemp(SwingFXUtils.fromFXImage(mandelbrotImage, null));
        });

        generateImageTask.setOnFailed(e -> {
            System.err.println("Ошибка при генерации изображения: " + generateImageTask.getException().getMessage());
        });

        new Thread(generateImageTask).start();
    }

    private void drawRectangles(GraphicsContext gc, double scaleX, double scaleY) {
        gc.clearRect(0, 0, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());
        for (Rectangle2D rect : rectangles) {
            double x = rect.getMinX() / scaleX;
            double y = rect.getMinY() / scaleY;
            double width = rect.getWidth() / scaleX;
            double height = rect.getHeight() / scaleY;
            gc.setStroke(Color.web("#eb3471"));
            gc.strokeRect(x, y, width, height);
        }
    }

    private boolean hasRectangle() {
        boolean hasRect = !rectangles.isEmpty();
        System.out.println("Есть ли прямоугольник: " + hasRect);
        return hasRect;
    }

    private Rectangle2D getSelectedRectangle() {
        if (!rectangles.isEmpty()) {
            return rectangles.get(rectangles.size() - 1);
        }
        return null;
    }

    // Метод для очистки прямоугольников
    private void clearRectangles() {
        rectangles.clear();
        rectangleSelected = false;
        if (canvas != null && canvas.getGraphicsContext2D() != null) {
            canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        }
    }

    private void showErrorMessage(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка выделения");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void createEncryptFinalPanel(BufferedImage image) {
        // Создание основного контейнера с градиентным фоном
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Создание текста "Полученное зашифрованное изображение:"
        Label titleLabel = new Label("Полученное зашифрованное изображение:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 40px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowTitle).setPrefHeight(100); // Расстояние под текстом

        // Шифруем изображение
        ImageEncrypt imageEncrypt = new ImageEncrypt();
        imageEncrypt.encryptWholeImage(image);

        // Получаем зашифрованное изображение
        BufferedImage encryptedImage = imageEncrypt.getEncryptedImage();

        // Создание ImageView для зашифрованного изображения
        ImageView imageView = new ImageView(SwingFXUtils.toFXImage(encryptedImage, null));
        imageView.setFitWidth(640);
        imageView.setFitHeight(450);

        // Создание контейнера для изображения
        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setAlignment(Pos.CENTER);

        // Создание кнопки "Сохранить изображение" с иконкой
        Image saveIcon = new Image(getClass().getResourceAsStream("/elements/icon_save.png"));
        ImageView saveIconView = new ImageView(saveIcon);
        saveIconView.setFitWidth(50);
        saveIconView.setFitHeight(50);
        Button saveButton = new Button("", saveIconView);
        saveButton.setStyle("-fx-background-color: transparent;");
        saveButton.setPrefSize(50, 50);
        saveButton.setOnAction(e -> {
            saveEncryptedImage(encryptedImage);
            //createStartPanel();
        });

        // Создание кнопки "Вернуться назад" с иконкой
        Image backImage = new Image(getClass().getResourceAsStream("/elements/icon_back.png"));
        ImageView backImageView = new ImageView(backImage);
        backImageView.setFitWidth(50);
        backImageView.setFitHeight(50);

        Button backButton = new Button("", backImageView);
        backButton.setStyle("-fx-background-color: transparent;"); // Убираем обводку
        backButton.setPrefSize(50, 50); // Устанавливаем размер иконки
        backButton.setOnAction(e -> {
            mainPane.getChildren().clear();
            panelForChooseAreaForEncrypt();
        });

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        // Размещение кнопок в контейнере
        HBox buttonContainer = new HBox(20, saveButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с картинкой
        StackPane rectContainer = new StackPane(imageContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, titleLabel, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topLeftContainer);

        // Создание контейнера для кнопок под прямоугольником
        VBox bottomContainer = new VBox(buttonContainer);
        bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
        bottomContainer.setPadding(new Insets(0, 0, 30, 0)); // Уменьшаем отступ снизу

        // Установка контейнера внизу
        mainContainer.setBottom(bottomContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    private void createEncryptFinalPanelForSelectedImage(BufferedImage image) {
        // Создание основного контейнера с градиентным фоном
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Создание текста "Полученное зашифрованное изображение:"
        Label titleLabel = new Label("Полученное зашифрованное изображение:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 40px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowTitle).setPrefHeight(100); // Расстояние под текстом

        // Создание ImageView для зашифрованного изображения
        ImageView imageView = new ImageView(SwingFXUtils.toFXImage(image, null));
        imageView.setFitWidth(640);
        imageView.setFitHeight(450);

        // Создание контейнера для изображения
        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setAlignment(Pos.CENTER);

        // Создание кнопки "Сохранить изображение" с иконкой
        Image saveIcon = new Image(getClass().getResourceAsStream("/elements/icon_save.png"));
        ImageView saveIconView = new ImageView(saveIcon);
        saveIconView.setFitWidth(50);
        saveIconView.setFitHeight(50);
        Button saveButton = new Button("", saveIconView);
        saveButton.setStyle("-fx-background-color: transparent;");
        saveButton.setPrefSize(50, 50);
        saveButton.setOnAction(e -> {
            saveEncryptedImage(image);
            createStartPanel();
        });

        // Создание кнопки "Вернуться назад" с иконкой
        Image backImage = new Image(getClass().getResourceAsStream("/elements/icon_back.png"));
        ImageView backImageView = new ImageView(backImage);
        backImageView.setFitWidth(50);
        backImageView.setFitHeight(50);

        Button backButton = new Button("", backImageView);
        backButton.setStyle("-fx-background-color: transparent;"); // Убираем обводку
        backButton.setPrefSize(50, 50); // Устанавливаем размер иконки
        backButton.setOnAction(e -> {
            mainPane.getChildren().clear();
            panelForChooseAreaForEncrypt();
        });

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        // Размещение кнопок в контейнере
        HBox buttonContainer = new HBox(20, saveButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с картинкой
        StackPane rectContainer = new StackPane(imageContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, titleLabel, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topLeftContainer);

        // Создание контейнера для кнопок под прямоугольником
        VBox bottomContainer = new VBox(buttonContainer);
        bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
        bottomContainer.setPadding(new Insets(0, 0, 30, 0)); // Уменьшаем отступ снизу

        // Установка контейнера внизу
        mainContainer.setBottom(bottomContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    private void createManualEncryptionPanel() {
        // Создание основного контейнера с градиентным фоном
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Создание GridPane для размещения элементов
        GridPane manualEncryptPanel = new GridPane();
        manualEncryptPanel.setPadding(new Insets(20));
        manualEncryptPanel.setHgap(15);
        manualEncryptPanel.setVgap(15);

        // Заголовок
        Label label = new Label("Введите значения параметров:");
        label.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");

        // Подписи и поля ввода
        Label widthLabel = new Label("Ширина изображения:");
        widthLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        TextField widthField = new TextField();
        widthField.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: black; -fx-font-size: 22px;");
        widthField.setPromptText("1024");
        widthField.setTextFormatter(new TextFormatter<>(createIntegerFilter(4)));

        Label heightLabel = new Label("Высота изображения:");
        heightLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        TextField heightField = new TextField();
        heightField.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: black; -fx-font-size: 22px;");
        heightField.setPromptText("768");
        heightField.setTextFormatter(new TextFormatter<>(createIntegerFilter(4)));

        // Zoom
        Label zoomLabel = new Label("Масштаб множества:");
        zoomLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        TextField zoomField = new TextField();
        zoomField.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: black; -fx-font-size: 22px;");
        zoomField.setPromptText("135000");
        zoomField.setTextFormatter(new TextFormatter<>(createIntegerFilter(7)));

        Label iterationsLabel = new Label("Число итераций:");
        iterationsLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        TextField iterationsField = new TextField();
        iterationsField.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: black; -fx-font-size: 22px;");
        iterationsField.setPromptText("1000");
        iterationsField.setTextFormatter(new TextFormatter<>(createIntegerFilter(5)));

        Label xLabel = new Label("Смещение по оси X:");
        xLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        TextField xField = new TextField();
        xField.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: black; -fx-font-size: 22px;");
        xField.setPromptText("-0.9999");
        xField.setTextFormatter(new TextFormatter<>(createDoubleFilter()));

        Label yLabel = new Label("Смещение по оси Y:");
        yLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        TextField yField = new TextField();
        yField.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: black; -fx-font-size: 22px;");
        yField.setPromptText("0.9999");
        yField.setTextFormatter(new TextFormatter<>(createDoubleFilter()));

        // Кнопка "Сохранить сгенерированный ключ"
        Button saveButton = new Button("Сохранить сгенерированный ключ");
        saveButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px; -fx-border-radius: 10px; -fx-border-width: 5px; -fx-border-color: white;");
        saveButton.setOnAction(e -> {
            try {
                int startMandelbrotWidth = Integer.parseInt(widthField.getText());
                int startMandelbrotHeight = Integer.parseInt(heightField.getText());
                double zoom = Double.parseDouble(zoomField.getText());
                int iterations = Integer.parseInt(iterationsField.getText());
                double x = Double.parseDouble(xField.getText());
                double y = Double.parseDouble(yField.getText());

                if (zoom <= 0 || iterations <= 0 || startMandelbrotWidth <= 0 || startMandelbrotHeight <= 0) {
                    throw new IllegalArgumentException("Некорректные данные");
                }

                // Сохранение параметров в бинарный файл
                String filePath = getTempPath() + "mandelbrot_params.bin";
                BinaryFile.saveMandelbrotParamsToBinaryFile(filePath, startMandelbrotWidth, startMandelbrotHeight, zoom, x, y, iterations);

                // Переход на новую панель
                createEncryptGeneratePanelWithParams(filePath);
            } catch (NumberFormatException ex) {
                showErrorDialog("Некорректный формат данных");
            } catch (IllegalArgumentException ex) {
                showErrorDialog(ex.getMessage());
            }
        });

        // Создание HBox для выравнивания кнопки "Сохранить сгенерированный ключ" по центру
        HBox saveButtonContainer = new HBox(saveButton);
        saveButtonContainer.setAlignment(Pos.CENTER); // Выравнивание по центру

        // Кнопка "Вернуться назад" с иконкой
        Image backImage = new Image(getClass().getResourceAsStream("/elements/icon_back.png"));
        ImageView backImageView = new ImageView(backImage);
        backImageView.setFitWidth(50);
        backImageView.setFitHeight(50);

        Button backButton = new Button("", backImageView);
        backButton.setStyle("-fx-background-color: transparent;"); // Убираем обводку
        backButton.setPrefSize(50, 50); // Устанавливаем размер иконки
        backButton.setOnAction(e -> {
            mainPane.getChildren().clear();
            if (imageFilePath != getTempPath() + "mandelbrot.png") {
                createEncryptModePanel();
            } else {
                panelForChooseAreaForEncrypt();
            }
        });

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        // Добавляем элементы в GridPane
        manualEncryptPanel.add(label, 0, 0, 2, 1); // Заголовок
        manualEncryptPanel.add(widthLabel, 0, 1); // Метка для ширины
        manualEncryptPanel.add(widthField, 1, 1); // Поле для ширины
        manualEncryptPanel.add(heightLabel, 0, 2); // Метка для высоты
        manualEncryptPanel.add(heightField, 1, 2); // Поле для высоты
        manualEncryptPanel.add(zoomLabel, 0, 3);
        manualEncryptPanel.add(zoomField, 1, 3);
        manualEncryptPanel.add(iterationsLabel, 0, 4);
        manualEncryptPanel.add(iterationsField, 1, 4);
        manualEncryptPanel.add(xLabel, 0, 5);
        manualEncryptPanel.add(xField, 1, 5);
        manualEncryptPanel.add(yLabel, 0, 6);
        manualEncryptPanel.add(yField, 1, 6);
        manualEncryptPanel.add(saveButton, 0, 7, 2, 1); // Кнопка сохранения

        // Создание HBox для обертки GridPane и выравнивания по центру
        HBox gridPaneContainer = new HBox(manualEncryptPanel);
        gridPaneContainer.setAlignment(Pos.CENTER); // Выравнивание по центру

        // Создание VBox для вертикального выравнивания
        VBox vbox = new VBox(20, gridPaneContainer);
        vbox.setAlignment(Pos.CENTER); // Выравнивание по центру
        vbox.setPadding(new Insets(50)); // Добавляем отступы слева и справа

        // Установка VBox в центр основного контейнера
        mainContainer.setCenter(vbox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topLeftContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    // Метод для создания фильтра для целых чисел
    private UnaryOperator<TextFormatter.Change> createIntegerFilter(int maxLength) {
        return change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty()) {
                return change;
            }
            if (newText.matches("([1-9]\\d*|0)?") && newText.length() <= maxLength) {
                return change;
            } else {
                return null;
            }
        };
    }

    // Метод для создания фильтра для чисел с плавающей точкой
    private UnaryOperator<TextFormatter.Change> createDoubleFilter() {
        return change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty()) {
                return change;
            }
            if (newText.matches("-?([1-9]\\d*|0)?(\\.\\d*)?") && newText.length() <= 15) {
                return change;
            } else {
                return null;
            }
        };
    }

    private void createChoosenMandelbrotPanel(String imagePath) {
        // Создание основного контейнера с фоном на весь экран
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Создание текста "Ваше изображение-ключ:"
        Label titleLabel = new Label("Ваше изображение-ключ:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 40px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowTitle).setPrefHeight(100); // Расстояние под текстом

        // Проверяем, существует ли файл mandelbrot.png в папке temp
        String mandelbrotFilePath = getTempPath() + "mandelbrot.png";
        File mandelbrotFile = new File(mandelbrotFilePath);

        BufferedImage mandelbrotImage = null;

        if (!mandelbrotFile.exists()) {
            // Если файл не существует, генерируем изображение
            Mandelbrot mandelbrot = new Mandelbrot();
            mandelbrotImage = mandelbrot.generateAfterGetParams(imagePath); // Получаем изображение

            if (mandelbrotImage == null) {
                System.err.println("Ошибка: не удалось сгенерировать изображение.");
                return; // Если изображение не сгенерировано, завершаем метод
            }

            // Сохраняем сгенерированное изображение в файл mandelbrot.png
            try {
                ImageIO.write(mandelbrotImage, "png", mandelbrotFile);
            } catch (IOException e) {
                System.err.println("Ошибка при сохранении изображения: " + e.getMessage());
                return;
            }
        } else {
            // Если файл существует, загружаем изображение из файла
            try {
                mandelbrotImage = ImageIO.read(mandelbrotFile);
            } catch (IOException e) {
                System.err.println("Ошибка при загрузке изображения: " + e.getMessage());
                return;
            }
        }

        // Загрузка изображения mandelbrot.png из папки temp
        Image imageMandelbrot = loadImageFromTemp(mandelbrotFilePath);
        if (imageMandelbrot == null) {
            return; // Если файл не найден, завершаем метод
        }
        ImageView imageViewMandelbrot = new ImageView(imageMandelbrot);
        imageViewMandelbrot.setFitWidth(720);
        imageViewMandelbrot.setFitHeight(540);

        // Загрузка изображения input.png из папки temp
        String inputFilePath = getTempPath() + "input.png";
        Image imageInput = loadImageFromTemp(inputFilePath);
        if (imageInput == null) {
            System.err.println("Файл изображения не найден: " + inputFilePath);
            return; // Если файл не найден, завершаем метод
        }
        ImageView imageView = new ImageView(imageInput);
        imageView.setFitWidth(640);
        imageView.setFitHeight(480);
        imageView.setTranslateX(150); // Выглядывание на 150px

        // Создание кнопки "Сгенерировать заново" с иконкой
        Button regenerateButton = new Button();
        Image repeatIcon = new Image(getClass().getResourceAsStream("/elements/icon_repeat.png"));
        ImageView repeatIconView = new ImageView(repeatIcon);
        repeatIconView.setFitWidth(50); // Установите нужный размер иконки
        repeatIconView.setFitHeight(50);
        regenerateButton.setGraphic(repeatIconView);
        regenerateButton.setStyle("-fx-background-color: transparent;");
        regenerateButton.setPrefSize(50, 50);
        regenerateButton.setOnAction(e -> {
            cancelCurrentTask(); // Отменяем текущую задачу
            createEncryptGeneratePanel();
            System.out.println("Кнопка 'Сгенерировать заново' нажата");
        });

        // Создание кнопки "Сгенерировать заново вручную"
        Image writeParamsImage = new Image(getClass().getResourceAsStream("/elements/icon_writeParams.png"));
        ImageView writeParamsImageView = new ImageView(writeParamsImage);
        writeParamsImageView.setFitWidth(50); // Устанавливаем ширину иконки
        writeParamsImageView.setFitHeight(50); // Устанавливаем высоту иконки
        Button manualButton = new Button("", writeParamsImageView); // Иконка вместо текста
        manualButton.setStyle("-fx-background-color: transparent;");
        manualButton.setPrefSize(50, 50);
        manualButton.setOnAction(e -> {
            cancelCurrentTask(); // Отменяем текущую задачу
            createManualEncryptionPanel();
        });

        // Создание кнопки "Зашифровать изображение полностью" с иконкой
        Image nextImage = new Image(getClass().getResourceAsStream("/elements/icon_next.png"));
        ImageView nextImageView = new ImageView(nextImage);
        nextImageView.setFitWidth(50); // Устанавливаем ширину иконки
        nextImageView.setFitHeight(50); // Устанавливаем высоту иконки
        Button okayButton = new Button("", nextImageView); // Иконка вместо текста
        okayButton.setStyle("-fx-background-color: transparent;");
        okayButton.setPrefSize(50, 50);
        okayButton.setOnAction(e -> {
            try {
                // Загружаем изображение из папки temp
                BufferedImage imageToEncrypt = loadBufferedImageFromTemp(inputFilePath);
                if (imageToEncrypt == null) {
                    System.err.println("Файл изображения не найден: " + inputFilePath);
                    return;
                }
                createEncryptFinalPanel(imageToEncrypt); // Передаем изображение в метод
            } catch (Exception ex) {
                System.err.println("Произошла ошибка: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        // Создание кнопки "Вернуться назад" с иконкой
        Image backImage = new Image(getClass().getResourceAsStream("/elements/icon_back.png"));
        ImageView backImageView = new ImageView(backImage);
        backImageView.setFitWidth(50);
        backImageView.setFitHeight(50);

        Button backButton = new Button("", backImageView);
        backButton.setStyle("-fx-background-color: transparent;"); // Убираем обводку
        backButton.setPrefSize(50, 50); // Устанавливаем размер иконки
        backButton.setOnAction(e -> {
            cancelCurrentTask(); // Отменяем текущую задачу
            mainPane.getChildren().clear();
            createEncryptModePanel();
        });

        // Добавление иконки swap
        Image swapImage = new Image(getClass().getResourceAsStream("/elements/icon_swap.png"));
        ImageView swapImageView = new ImageView(swapImage);
        swapImageView.setFitWidth(50);
        swapImageView.setFitHeight(50);

        Button swapButton = new Button("", swapImageView);
        swapButton.setStyle("-fx-background-color: transparent;");
        swapButton.setOnAction(e -> {
            panelForChooseAreaForEncrypt();
        });

        // Размещение кнопок в контейнере
        HBox buttonContainer = new HBox(20, regenerateButton, manualButton, okayButton, swapButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение изображения в контейнере
        StackPane imageContainer = new StackPane(imageView, imageViewMandelbrot);
        imageContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, titleLabel, spaceBelowTitle, imageContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Создание блока-подсказки
        VBox hintBox = createHintBoxForEncrypt();
        hintBox.setAlignment(Pos.CENTER); // Выравниваем hintBox по вертикали
        hintBox.setPrefSize(300, 230); // Фиксированный размер hintBox (150x200)
        hintBox.setMinSize(300, 230); // Минимальный размер hintBox (150x200)
        hintBox.setMaxSize(300, 230); // Максимальный размер hintBox (150x200)

        // Создаем GridPane для размещения hintBox и contentBox
        GridPane centerGrid = new GridPane();
        centerGrid.setAlignment(Pos.CENTER); // Выравниваем по центру
        centerGrid.add(hintBox, 0, 0); // hintBox в первой колонке
        centerGrid.add(contentBox, 1, 0); // contentBox во второй колонке
        centerGrid.setPadding(new Insets(0, 20, 0, 60)); // Отступ слева 60px

        // Устанавливаем пропорции для колонок
        ColumnConstraints hintColumn = new ColumnConstraints();
        hintColumn.setPrefWidth(300); // Фиксированная ширина для hintBox
        hintColumn.setHgrow(Priority.NEVER); // hintBox не растягивается

        ColumnConstraints contentColumn = new ColumnConstraints();
        contentColumn.setHgrow(Priority.ALWAYS); // contentBox занимает всё доступное пространство

        centerGrid.getColumnConstraints().addAll(hintColumn, contentColumn);

        // Установка центрального контента
        mainContainer.setCenter(centerGrid);

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topLeftContainer);

        // Создание контейнера для кнопок под прямоугольником
        VBox bottomContainer = new VBox(buttonContainer);
        bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
        bottomContainer.setPadding(new Insets(0, 0, 30, 0)); // Уменьшаем отступ снизу

        // Установка контейнера внизу
        mainContainer.setBottom(bottomContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    public void createDecryptBeginPanel() {
        // Создание основного контейнера с градиентным фоном
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Создание текста "Дешифратор"
        Label titleLabel = new Label("Дешифратор");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 64px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowTitle).setPrefHeight(100); // Расстояние под текстом

        // Создание светло-серого прямоугольника
        Region lightGrayRect = new Region();
        lightGrayRect.setStyle("-fx-background-color: #494949;");
        lightGrayRect.setPrefSize(720, 540);
        lightGrayRect.setMinSize(720, 540); // Фиксируем минимальный размер
        lightGrayRect.setMaxSize(720, 540); // Фиксируем максимальный размер

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowRect = new Region();
        spaceBelowRect.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowRect).setPrefHeight(163); // Расстояние под текстом

        // Создание темно-серого прямоугольника
        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(640, 480);
        darkGrayRect.setMinSize(640, 480); // Фиксируем минимальный размер
        darkGrayRect.setMaxSize(640, 480); // Фиксируем максимальный размер
        darkGrayRect.setTranslateX(150); // Выглядывание на 150px

        // Создание кнопки "Выбрать файл"
        Button uploadButton = new Button("Выбрать файл");
        uploadButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular'; -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white; -fx-font-size: 28px;");
        uploadButton.setPrefSize(257, 68);
        uploadButton.setOnAction(e -> {
            String imagePath = selectImageFileForDecrypt(); // Получаем путь к файлу input.png
            if (imagePath != null) {
                createDecryptLoadPanel(imagePath); // Передаем путь к файлу в метод
            } else {
                showErrorDialog("Файл не выбран!");
            }
        });

        // Создание кнопки "Вернуться назад" с иконкой
        Image backImage = new Image(getClass().getResourceAsStream("/elements/icon_back.png"));
        ImageView backImageView = new ImageView(backImage);
        backImageView.setFitWidth(50);
        backImageView.setFitHeight(50);

        Button backButton = new Button("", backImageView);
        backButton.setStyle("-fx-background-color: transparent;"); // Убираем обводку
        backButton.setPrefSize(50, 50); // Устанавливаем размер иконки
        backButton.setOnAction(e -> {
            mainPane.getChildren().clear();
            createStartPanel();
        });

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        // Размещение кнопки по центру светло-серого прямоугольника
        StackPane buttonContainer = new StackPane(uploadButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого и светло-серого прямоугольников
        StackPane rectContainer = new StackPane(darkGrayRect, lightGrayRect, buttonContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, titleLabel, spaceBelowTitle, rectContainer, spaceBelowRect);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topLeftContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    private void createDecryptLoadPanel(String imagePath) {
        // Создание основного контейнера с градиентным фоном
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Создание текста "Загруженное для расшифровки изображение:"
        Label titleLabel = new Label("Загруженное для расшифровки изображение:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 40px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowTitle).setPrefHeight(100); // Расстояние под текстом

        // Создание темно-серого прямоугольника
        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(640, 480);
        darkGrayRect.setMinSize(640, 480); // Фиксируем минимальный размер
        darkGrayRect.setMaxSize(640, 480); // Фиксируем максимальный размер
        darkGrayRect.setTranslateX(150); // Выглядывание на 150px

        // Загрузка изображения
        Image image = new Image("file:" + imagePath);
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(720);
        imageView.setFitHeight(540);

        // Создание кнопки "Продолжить расшифровку"
        Button regenerateButton = new Button("Продолжить расшифровку");
        regenerateButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular'; -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white; -fx-font-size: 20px;");
        regenerateButton.setPrefSize(320, 48);
        regenerateButton.setOnAction(e -> {
            createDecryptModePanel();
        });

        // Создание кнопки "Вернуться назад" с иконкой
        Image backImage = new Image(getClass().getResourceAsStream("/elements/icon_back.png"));
        ImageView backImageView = new ImageView(backImage);
        backImageView.setFitWidth(50);
        backImageView.setFitHeight(50);

        Button backButton = new Button("", backImageView);
        backButton.setStyle("-fx-background-color: transparent;"); // Убираем обводку
        backButton.setPrefSize(50, 50); // Устанавливаем размер иконки
        backButton.setOnAction(e -> {
            mainPane.getChildren().clear();
            createDecryptBeginPanel();
        });

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        // Размещение темно-серого прямоугольника с изображением
        HBox contentContainer = new HBox(imageView);
        contentContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с контейнером
        StackPane rectContainer = new StackPane(darkGrayRect, contentContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, titleLabel, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topLeftContainer);

        // Создание контейнера для кнопок "Продолжить расшифровку"
        HBox bottomButtonsContainer = new HBox(20, regenerateButton); // Кнопки в одной строке
        bottomButtonsContainer.setAlignment(Pos.CENTER); // Выравнивание по центру
        bottomButtonsContainer.setPadding(new Insets(0, 0, 30, 0)); // Отступ снизу

        // Установка контейнера с кнопками внизу
        mainContainer.setBottom(bottomButtonsContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    public void createDecryptModePanel() {
        // Создание основного контейнера с градиентным фоном
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Создание текста "Дешифратор"
        Label titleLabel = new Label("Дешифратор");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 64px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowTitle).setPrefHeight(100); // Расстояние под текстом

        // Загрузка изображения input.png
        Image inputImage = new Image("file:" + getTempPath() + "input.png");
        ImageView inputImageView = new ImageView(inputImage);
        inputImageView.setFitWidth(640); // Ширина изображения
        inputImageView.setFitHeight(480); // Высота изображения
        inputImageView.setTranslateX(150); // Сдвигаем прямоугольник вправо на 150px

        // Создание светло-серого прямоугольника
        Region lightGrayRect = new Region();
        lightGrayRect.setStyle("-fx-background-color: #494949;");
        lightGrayRect.setPrefSize(720, 540); // Уменьшаем размер прямоугольника
        lightGrayRect.setMinSize(720, 540); // Фиксируем минимальный размер
        lightGrayRect.setMaxSize(720, 540); // Фиксируем максимальный размер

        // Создание кнопки "Загрузить файл-ключ"
        Button manualButton = new Button("Загрузить файл-ключ");
        manualButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular'; -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white; -fx-font-size: 22px;");
        manualButton.setPrefSize(287, 68);
        manualButton.setOnAction(e -> {
            File selectedFile = selectKeyFileForDecrypt();
            if (selectedFile != null) {
                createDecryptFinalPanel(selectedFile.getAbsolutePath());
            } else {
                showErrorDialog("Файл-ключ не выбран!");
            }
        });

        // Создание кнопки "Вернуться назад" с иконкой
        Image backImage = new Image(getClass().getResourceAsStream("/elements/icon_back.png"));
        ImageView backImageView = new ImageView(backImage);
        backImageView.setFitWidth(50);
        backImageView.setFitHeight(50);

        Button backButton = new Button("", backImageView);
        backButton.setStyle("-fx-background-color: transparent;"); // Убираем обводку
        backButton.setPrefSize(50, 50); // Устанавливаем размер иконки
        backButton.setOnAction(e -> {
            mainPane.getChildren().clear();
            createDecryptBeginPanel();
        });

        // Размещение кнопки "Загрузить файл-ключ" по центру светло-серого прямоугольника
        StackPane buttonContainer = new StackPane(manualButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение светло-серого прямоугольника с кнопкой
        StackPane rectContainer = new StackPane(lightGrayRect, buttonContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение изображения input.png под прямоугольником
        StackPane imageContainer = new StackPane(inputImageView, rectContainer);
        imageContainer.setAlignment(Pos.CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowRect = new Region();
        spaceBelowRect.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowRect).setPrefHeight(163); // Расстояние под текстом

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, titleLabel, spaceBelowTitle, imageContainer, spaceBelowRect);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topLeftContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    private void createDecryptFinalPanel(String keyFilePath) {
        // Создание основного контейнера с градиентным фоном
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(), // Создаем градиент
                CornerRadii.EMPTY, // Без закругленных углов
                Insets.EMPTY // Без отступов
        )));

        // Создание текста "Расшифрованное изображение"
        Label titleLabel = new Label("Расшифрованное изображение");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 40px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowTitle).setPrefHeight(100); // Расстояние под текстом

        // Создание контейнера для изображения
        StackPane imageContainer = new StackPane();
        Label loadingLabel = new Label("Расшифровка изображения...");
        loadingLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        imageContainer.getChildren().add(loadingLabel);

        // Размещение темно-серого прямоугольника с изображением
        StackPane rectContainer = new StackPane(imageContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Создание кнопки "Вернуться назад" с иконкой
        Image backImage = new Image(getClass().getResourceAsStream("/elements/icon_back.png"));
        ImageView backImageView = new ImageView(backImage);
        backImageView.setFitWidth(50);
        backImageView.setFitHeight(50);
        Button backButton = new Button("", backImageView);
        backButton.setStyle("-fx-background-color: transparent;"); // Убираем обводку
        backButton.setPrefSize(50, 50); // Устанавливаем размер иконки
        backButton.setOnAction(e -> {
            mainPane.getChildren().clear();
            createStartPanel();
        });

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        // Создание прозрачного прямоугольника для расстояния под текстом
        Node spaceBelowRect = new Region();
        spaceBelowRect.setStyle("-fx-background-color: transparent;");
        ((Region) spaceBelowRect).setPrefHeight(113); // Расстояние под текстом

        // Создание кнопки "Сохранить изображение" с иконкой
        Image saveIcon = new Image(getClass().getResourceAsStream("/elements/icon_save.png"));
        ImageView saveIconView = new ImageView(saveIcon);
        saveIconView.setFitWidth(50); // Устанавливаем ширину иконки
        saveIconView.setFitHeight(50); // Устанавливаем высоту иконки

        Button saveButton = new Button("", saveIconView); // Иконка вместо текста
        saveButton.setStyle("-fx-background-color: transparent;"); // Прозрачный фон
        saveButton.setPrefSize(50, 50); // Устанавливаем размер кнопки
        saveButton.setOnAction(e -> {
            saveDecryptedImage();
        }); // Логика сохранения изображения

        // Размещение кнопок в контейнере
        HBox buttonContainer = new HBox(20, saveButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение кнопки "Сохранить изображение" на 30px от нижнего края окна
        StackPane saveButtonContainer = new StackPane(saveButton);
        saveButtonContainer.setAlignment(Pos.BOTTOM_CENTER);
        saveButtonContainer.setPadding(new Insets(0, 0, 30, 0)); // Отступ снизу 30px

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, titleLabel, spaceBelowTitle, rectContainer, spaceBelowRect);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопками внизу
        mainContainer.setBottom(saveButtonContainer);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topLeftContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);

        // Запуск задачи расшифровки изображения
        Task<Void> decryptImageTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                ImageDecrypt imageDecrypt = new ImageDecrypt();
                imageDecrypt.decryptImage(keyFilePath);
                return null;
            }
        };

        decryptImageTask.setOnSucceeded(e -> {
            try {
                Image decryptedImage = new Image("file:" + getTempPath() + "decrypted_image.png");
                ImageView imageView = new ImageView(decryptedImage);
                imageView.setFitWidth(720);
                imageView.setFitHeight(540);
                imageContainer.getChildren().clear();
                imageContainer.getChildren().add(imageView);
            } catch (Exception ex) {
                showErrorDialog("Ошибка при загрузке расшифрованного изображения: " + ex.getMessage());
            }
        });

        decryptImageTask.setOnFailed(e -> {
            showErrorDialog("Ошибка при расшифровке изображения: " + decryptImageTask.getException().getMessage());
        });

        new Thread(decryptImageTask).start();
    }

    public void saveEncryptedImage(BufferedImage encryptedImage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить зашифрованное изображение");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "png"));

        File fileToSave = fileChooser.showSaveDialog(primaryStage);

        if (fileToSave != null) {
            imageFilePath = fileToSave.getAbsolutePath();

            // Добавляем расширение файла, если оно не указано
            if (!imageFilePath.toLowerCase().endsWith(".png")) {
                imageFilePath += ".png";
            }

            try {
                // Сохраняем изображение по выбранному пути
                ImageIO.write(encryptedImage, "png", new File(imageFilePath));

                // Сохраняем key_decoder.bin
                saveFileWithPathToEncryptImage(imageFilePath);
                saveKeyDecoder(imageFilePath);
                System.out.println("Путь к файлу: " + imageFilePath);
            } catch (IOException e) {
                showErrorDialog("Ошибка при сохранении изображения: " + e.getMessage());
            }
        }
    }

    private void saveKeyDecoder(String imageFilePath) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить key_decoder.bin");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Файл-ключ", "*.bin"));

        File fileToSave = fileChooser.showSaveDialog(primaryStage);

        if (fileToSave != null) {
            keyDecoderFilePath = fileToSave.getAbsolutePath();

            // Добавляем расширение файла, если оно не указано
            if (!keyDecoderFilePath.toLowerCase().endsWith(".bin")) {
                keyDecoderFilePath += ".bin";
            }

            try {
                // Получаем абсолютный путь к папке temp
                String resourcesPath = getTempPath() + "key_decoder.bin";
                Path sourcePath = Paths.get(resourcesPath);
                Path destinationPath = Paths.get(keyDecoderFilePath);

                // Копируем файл из папки resources в выбранный путь
                Files.copy(sourcePath, destinationPath);

                System.out.println("key_decoder.bin сохранен в: " + keyDecoderFilePath);

                // Уведомление о том, что изображение и бинарный файл успешно сохранены
                showSuccessDialog("Изображение и файл-ключ успешно сохранены:\n" +
                        "Изображение: " + imageFilePath + "\n" +
                        "Файл-ключ: " + keyDecoderFilePath);

                createStartPanel();
            } catch (Exception e) {
                System.err.println("Ошибка при сохранении key_decoder.bin: " + e.getMessage());
                showErrorDialog("Ошибка при сохранении файл-ключа: " + e.getMessage());
            }
        }
    }

    public File selectMandelbrotForEncrypt() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите файл-ключ");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Бинарный файл", "*.bin")
        );

        // Показываем диалог выбора файла
        File selectedFile = fileChooser.showOpenDialog(primaryStage);

        // Проверяем, был ли выбран файл
        if (selectedFile != null) {
            String extension = getFileExtension(selectedFile);

            if (extension.equals("bin")) {
                // Если выбран бинарный файл, генерируем изображение с помощью Mandelbrot
                try {
                    Object[] params = BinaryFile.loadMandelbrotParamsFromBinaryFile(selectedFile.getAbsolutePath());

                    // Проверяем, что параметры были успешно загружены
                    if (params == null || params.length < 6) {
                        showErrorDialog("Файл-ключ имеет неправильный формат: недостаточно данных.");
                        return null;
                    }

                    // Извлекаем параметры для генерации изображения
                    int startMandelbrotWidth = (int) params[0];
                    int startMandelbrotHeight = (int) params[1];
                    double ZOOM = (double) params[2];
                    double offsetX = (double) params[3];
                    double offsetY = (double) params[4];
                    int MAX_ITER = (int) params[5];

                    // Генерируем изображение с помощью Mandelbrot
                    Mandelbrot mandelbrot = new Mandelbrot();
                    BufferedImage generatedImage = mandelbrot.generateImage(
                            startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER
                    );
                    saveMandelbrotToTemp(generatedImage);
                    BinaryFile.saveMandelbrotParamsToBinaryFile(getTempPath() + "mandelbrot_params.bin", startMandelbrotWidth, startMandelbrotHeight, ZOOM, offsetX, offsetY, MAX_ITER);

                    //createChoosenMandelbrotPanel(tempFile.getAbsolutePath());
                } catch (Exception e) {
                    showErrorDialog("Ошибка при чтении файла-ключа: " + e.getMessage());
                }
            }
        }

        // Возвращаем выбранный файл
        return selectedFile;
    }

    private String getFileExtension(File file) {
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1).toLowerCase();
    }

    private String selectImageFileForEncrypt() {
        // Создаем FileChooser для выбора изображения
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите изображение для шифрования");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg"));

        // Открываем диалоговое окно и получаем выбранный файл
        File selectedFile = fileChooser.showOpenDialog(primaryStage);

        if (selectedFile != null) {
            // Сохраняем выбранное изображение в папку temp
            saveInputImageToTemp(selectedFile);
            // Возвращаем путь к файлу input.png в папке temp
            return getTempPath() + "input.png";
        } else {
            System.out.println("Файл не выбран.");
            return null; // Возвращаем null, если файл не выбран
        }
    }

    private String selectImageFileForDecrypt() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите изображение для расшифрования");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.jpg", "*.jpeg", "*.png", "*.bmp"));

        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile != null) {
            try {
                // Загружаем выбранное изображение
                BufferedImage image = ImageIO.read(selectedFile);
                if (image != null) {
                    // Сохраняем изображение в папку temp
                    saveInputImageToTemp(selectedFile);
                    System.out.println("Изображение сохранено в папку temp: " + selectedFile.getName());
                    return selectedFile.getAbsolutePath(); // Возвращаем путь к выбранному файлу
                } else {
                    System.err.println("Ошибка при загрузке изображения: " + selectedFile.getAbsolutePath());
                }
            } catch (IOException e) {
                System.err.println("Ошибка при загрузке изображения: " + e.getMessage());
            }
        }
        return null;
    }

    public void showErrorDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private BufferedImage loadImage(String filePath) {
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

    public void saveDecryptedImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить расшифрованное изображение");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG изображение", "png"));

        File fileToSave = fileChooser.showSaveDialog(primaryStage);

        if (fileToSave != null) {
            String filePath = fileToSave.getAbsolutePath();

            // Добавляем расширение файла, если оно не указано
            if (!filePath.toLowerCase().endsWith(".png")) {
                filePath += ".png";
            }

            try {
                // Загружаем расшифрованное изображение
                BufferedImage decryptedImage = ImageIO.read(new File(getTempPath() + "decrypted_image.png"));

                // Сохраняем изображение по выбранному пути
                ImageIO.write(decryptedImage, "png", new File(filePath));

                // Дублируем изображение в src/temp под именем decrypted_image.png
                String resourcesPath = getTempPath() + "decrypted_image.png";
                ImageIO.write(decryptedImage, "png", new File(resourcesPath));

                // Уведомление о том, что изображение успешно сохранено
                showSuccessDialog("Изображение успешно сохранено в: " + filePath);

                System.out.println("Изображение сохранено в: " + filePath);

                createStartPanel();
            } catch (IOException e) {
                System.err.println("Ошибка при сохранении изображения: " + e.getMessage());
                showErrorDialog("Ошибка при сохранении изображения: " + e.getMessage());
            }
        }
    }

    private void showSuccessDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Успех");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private File selectKeyFileForDecrypt() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите файл-ключ для расшифрования");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Файлы ключей", "*.bin"));
        return fileChooser.showOpenDialog(primaryStage);
    }

    // Метод для установки консоли
    public static void setConsole(TextArea console) {
        JavaFX.console = console;
    }

    // Метод для добавления текста в консоль
    public static void logToConsole(String message) {
        if (console != null) {
            console.appendText(message + "\n");
        }
    }

    private void createTempFolder() {
        String tempPath = getTempPath();
        File tempDir = new File(tempPath);

        // Создаем папку temp, если она не существует
        if (!tempDir.exists()) {
            tempDir.mkdir();
        }

        // Добавляем хук для удаления папки при завершении программы
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            deleteFolder(tempDir);
        }));
    }

    // Метод для рекурсивного удаления папки
    private void deleteFolder(File folder) {
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteFolder(file); // Рекурсивно удаляем файлы в папке
                }
            }
        }
        folder.delete(); // Удаляем папку или файл
    }

    private void saveInputImageToTemp(File selectedFile) {
        // Получаем путь к папке temp
        String tempPath = getTempPath();
        File tempDir = new File(tempPath);

        createTempFolder();

        // Путь к файлу input.png в папке temp
        String tempFilePath = tempPath + "input.png";
        File tempFile = new File(tempFilePath);

        try {
            // Копируем выбранный файл в папку temp
            try (InputStream inputStream = new FileInputStream(selectedFile);
                 OutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("Изображение сохранено в папку temp: " + tempFile.getAbsolutePath());

            // Убеждаемся, что файл существует
            if (tempFile.exists() && tempFile.canRead()) {
                System.out.println("Файл существует: " + tempFile.getAbsolutePath());
            } else {
                System.err.println("Файл не существует или не доступен для чтения: " + tempFile.getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении изображения: " + e.getMessage());
        }
    }

    // Загрузка изображения из папки temp
    private ImageView loadInputImageFromTemp() {
        // Получаем путь к файлу input.png в папке temp
        String tempPath = getTempPath();
        String tempFilePath = tempPath + "input.png";
        File tempFile = new File(tempFilePath);

        // Проверяем, существует ли файл и доступен ли он для чтения
        if (!tempFile.exists() || !tempFile.canRead()) {
            System.err.println("Файл изображения не найден: " + tempFilePath);
            return null; // Возвращаем null, если файл не найден
        }

        // Загружаем изображение из файла
        Image image = new Image(tempFile.toURI().toString());
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(640);
        imageView.setFitHeight(480);
        imageView.setTranslateX(150); // Выглядывание на 50px

        return imageView;
    }

    private BufferedImage loadBufferedImageFromTemp(String filePath) {
        File tempFile = new File(filePath);
        if (!tempFile.exists() || !tempFile.canRead()) {
            System.err.println("Файл изображения не найден: " + filePath);
            return null;
        }

        try {
            return ImageIO.read(tempFile);
        } catch (IOException e) {
            System.err.println("Ошибка при загрузке изображения: " + e.getMessage());
            return null;
        }
    }

    private Image loadImageFromTemp(String filePath) {
        File tempFile = new File(filePath);
        if (!tempFile.exists() || !tempFile.canRead()) {
            System.err.println("Файл изображения не найден: " + filePath);
            return null;
        }

        try {
            return new Image(tempFile.toURI().toString());
        } catch (Exception e) {
            System.err.println("Ошибка при загрузке изображения: " + e.getMessage());
            return null;
        }
    }

    private void saveMandelbrotToTemp(BufferedImage image) {
        // Получаем путь к папке temp
        String tempPath = getTempPath();
        File tempDir = new File(tempPath);

        createTempFolder();

        // Путь к файлу в папке temp
        String tempFilePath = tempPath + "mandelbrot.png";
        File tempFile = new File(tempFilePath);

        try {
            // Сохраняем изображение в папку temp
            ImageIO.write(image, "png", tempFile);
            System.out.println("Изображение сохранено в папку temp: " + tempFile.getAbsolutePath());

            // Убеждаемся, что файл существует
            if (tempFile.exists() && tempFile.canRead()) {
                System.out.println("Файл существует: " + tempFile.getAbsolutePath());
                // Переходим к созданию панели со сгенерированным изображением
                //createChoosenMandelbrotPanel(tempFile.getAbsolutePath());
            } else {
                System.err.println("Файл не существует или не доступен для чтения: " + tempFile.getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении изображения: " + e.getMessage());
        }
    }

    private void saveFileWithPathToEncryptImage(String imageFilePath) {
        String userDesktop = System.getProperty("user.home") + File.separator + "Desktop";
        File file = new File(userDesktop, "encrypted_image_paths.txt"); // Файл на рабочем столе

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(imageFilePath);
            writer.newLine();
            System.out.println("Путь к изображению успешно записан в файл: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Ошибка при записи в файл: " + e.getMessage());
        }
    }
}