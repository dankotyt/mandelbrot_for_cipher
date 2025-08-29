package com.cipher.view.javafx;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.service.EncryptionService;
import com.cipher.core.utils.*;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.application.Platform;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cipher.core.service.MandelbrotService;
import com.cipher.core.encryption.ImageEncrypt;
import com.cipher.core.encryption.ImageDecrypt;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class JavaFXImpl extends Application implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(JavaFXImpl.class);

    private StackPane mainPane;
    private Stage primaryStage;
    private Canvas canvas;
    private CoordinateUtils coordUtils;
    private Task<Image> currentTask;
    private static TextArea console;
    private ExecutorService executorService;

    @Setter
    private static ConfigurableApplicationContext springContext;

    private Point2D startPoint;
    private Point2D endPoint;
    private boolean drawingRectangle = false;
    private final List<Rectangle2D> rectangles = new ArrayList<>();
    private boolean rectangleSelected = false;

    private final DialogDisplayer dialogDisplayer = new DialogDisplayer();
    private final TempFileManager tempFileManager = new TempFileManager();
    private final NumberFilter numberFilter = new NumberFilter();

    private String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    public static void main(String[] args) {
        launch(JavaFXImpl.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        launch(JavaFXImpl.class, (String[]) null);
    }

    @Override
    public void start(Stage primaryStage) {
        this.executorService = Executors.newCachedThreadPool();
        this.primaryStage = primaryStage;
        mainPane = new StackPane();
        Scene scene = new Scene(mainPane, 1920, 980);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Шифр Мандельброта");

        // Добавление иконки приложения
        Image icon = loadImageResource("/elements/icon.png");
        primaryStage.getIcons().add(icon);
        primaryStage.show();
        showLoadingScreen();

        // В методе start() вашего JavaFXImpl
        primaryStage.setOnCloseRequest(event -> {
            event.consume(); // Предотвращаем immediate close
            shutdownApplication(); // Правильное завершение
        });
    }

    private void shutdownExecutors() {
        try {
            // Пытаемся gracefully shutdown
            executorService.shutdown();

            // Ждем завершения 5 секунд
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                // Принудительно завершаем если не успели
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void shutdownApplication() {
        try {
            // 1. Завершаем все фоновые потоки
            shutdownExecutors();

            // 2. Если есть Spring контекст - закрываем его
            if (springContext != null) {
                springContext.close();
            }

            // 3. Закрываем JavaFX
            Platform.exit();

            // 4. Принудительно завершаем JVM (если нужно)
            System.exit(0);

        } catch (Exception e) {
            logger.error("Error during shutdown: " + e.getMessage());
            System.exit(1);
        }
    }

    // Метод для создания градиента
    protected static LinearGradient createGradient() {
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
        VBox loadingContainer = new VBox(20);
        loadingContainer.setAlignment(Pos.CENTER);
        loadingContainer.setPadding(new Insets(20));
        loadingContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        Label loadingLabel = new Label("Mandelbrot Cipher");
        loadingLabel.setFont(Font.font("Intro Regular", 96));
        loadingLabel.setTextFill(Color.WHITE);

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(900);
        progressBar.setStyle("-fx-accent: #0065CA;");

        loadingContainer.getChildren().addAll(loadingLabel, progressBar);
        mainPane.getChildren().add(loadingContainer);

        Task<Void> loadingTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                for (int i = 0; i <= 100; i++) {
                    if (isCancelled()) {
                        break;
                    }
                    updateProgress(i, 100);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        if (isCancelled()) {
                            break;
                        }
                    }
                }
                return null;
            }
        };

        progressBar.progressProperty().bind(loadingTask.progressProperty());

        loadingTask.setOnSucceeded(event -> {
            mainPane.getChildren().remove(loadingContainer);
            createStartPanel();
        });

        loadingTask.setOnFailed(event -> {
            Throwable ex = loadingTask.getException();
            System.err.println("Loading failed: " + ex.getMessage());
            // Показать сообщение об ошибке
        });

        executorService.execute(loadingTask);
    }

    private void createStartPanel() {
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Загрузка изображений значков с использованием относительного пути
        Image lockImage = loadImageResource("/elements/icon_lock.png");
        Image unlockImage = loadImageResource("/elements/icon_unlock.png");

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

        Button exitButton = new Button("Завершить программу");
        exitButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-border-color: #3A5975; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white;" +
                " -fx-font-size: 28px;");
        exitButton.setPrefSize(350, 68);
        exitButton.setOnAction(e -> shutdownApplication());

        Button connectButton = new Button("Подключение к серверу");
        connectButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-border-color: #3A5975; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white;" +
                " -fx-font-size: 28px;");
        connectButton.setPrefSize(380, 68);
        connectButton.setOnAction(e -> createStartConnectionPanel());

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

        HBox connectButtonBox = new HBox(connectButton);
        connectButtonBox.setPadding(new Insets(30, 0, 0, 0));
        connectButtonBox.setAlignment(Pos.CENTER);

        HBox exitButtonBox = new HBox(exitButton);
        exitButtonBox.setPadding(new Insets(30, 0, 50, 0));
        exitButtonBox.setAlignment(Pos.CENTER);

        // Создание метки "Начало работы"
        Label startLabel = new Label("Начало работы");
        startLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 72px;");

        HBox topCenterContainer = new HBox(startLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        VBox buttonBox = new VBox(0);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(encryptButtonBox, decryptButtonBox, connectButtonBox, exitButtonBox);

        mainContainer.setTop(topCenterContainer);

        mainContainer.setCenter(buttonBox);

        mainPane.getChildren().add(mainContainer);
    }

    public void createStartConnectionPanel() {
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        Label titleLabel = new Label("Выберите желаемое действие:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.CENTER);

        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    mainPane.getChildren().clear();
                    createStartPanel();
                }
        );

        // Логика расположения объектов: создаем BorderPane = mainContainer. Далее создаем еще один
        // BorderPane = topContainer, в котором мы расположим backButton и titleLabel, который
        // в дальнейшем расположим в mainContainer.setTop(topContainer). А кнопки (buttonContainer)
        // будут находится в mainContainer.setCenter(buttonContainer).
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20));

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        BorderPane topContainer = new BorderPane();
        topContainer.setLeft(topLeftContainer);
        topContainer.setCenter(topCenterContainer);

        Button sendButton = new Button("Отправить");
        sendButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white;" +
                " -fx-font-size: 28px;");
        sendButton.setAlignment(Pos.CENTER);
        sendButton.setOnAction(e -> {

        });

        Button getButton = new Button("Получить");
        getButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white;" +
                " -fx-font-size: 28px;");
        getButton.setAlignment(Pos.CENTER);
        getButton.setOnAction(e -> {

        });

        HBox buttonContainer = new HBox(20, sendButton, getButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещаем контейнер с кнопками в центре BorderPane
        mainContainer.setTop(topContainer);

        mainContainer.setCenter(buttonContainer);

        // Очищаем mainPane и добавляем mainContainer
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    public void createEncryptBeginPanel() {
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Создание текста "Шифратор"
        Label titleLabel = new Label("Шифратор");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 72px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50); // Устанавливаем фиксированную высоту

        // Создание светло-серого прямоугольника
        Region lightGrayRect = new Region();
        lightGrayRect.setStyle("-fx-background-color: #494949;");
        lightGrayRect.setPrefSize(720, 540);
        lightGrayRect.setMinSize(720, 540);
        lightGrayRect.setMaxSize(720, 540);

        // Создание темно-серого прямоугольника
        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(640, 480);
        darkGrayRect.setMinSize(640, 480);
        darkGrayRect.setMaxSize(640, 480);
        darkGrayRect.setTranslateX(150); // Выглядывание на 150px

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowRect = new Region();
        spaceBelowRect.setStyle("-fx-background-color: transparent;");
        spaceBelowRect.setPrefHeight(50); // Устанавливаем фиксированную высоту

        // Создание кнопки "Выбрать файл"
        Button uploadButton = new Button("Выбрать файл");
        uploadButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white;" +
                " -fx-font-size: 28px;");
        uploadButton.setPrefSize(257, 68);
        uploadButton.setOnAction(e -> {
            String imagePath = selectImageFileForEncrypt(); // Получаем путь к файлу input.png
            if (imagePath != null) {
                createEncryptLoadPanel(imagePath); // Передаем путь к файлу в метод
            } else {
                dialogDisplayer.showErrorDialog("Файл не выбран!");
            }
        });

        // Создание кнопки "Вернуться назад" с иконкой
        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    mainPane.getChildren().clear();
                    createStartPanel();
                }
        );

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        BorderPane topContainer = new BorderPane();
        topContainer.setCenter(topCenterContainer);
        topContainer.setLeft(topLeftContainer);

        // Размещение кнопки по центру светло-серого прямоугольника
        StackPane buttonContainer = new StackPane(uploadButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого и светло-серого прямоугольников
        StackPane rectContainer = new StackPane(darkGrayRect, lightGrayRect, buttonContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        Region spaceInBottom = new Region();
        spaceInBottom.setStyle("-fx-background-color: transparent;");
        spaceInBottom.setPrefHeight(120); // 120, т.к. на следующей панеле 20+50+50 (bottomButtonsContainer)

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

        mainContainer.setBottom(spaceInBottom);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    private void createEncryptLoadPanel(String imagePath) {
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Создание текста "Загруженное для шифрования изображение:"
        Label titleLabel = new Label("Загруженное для шифрования изображение:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50); // Устанавливаем фиксированную высоту

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
        Button continueButton = new Button("Продолжить шифрование");
        continueButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white;" +
                " -fx-font-size: 20px;");
        continueButton.setPrefSize(290, 50);
        continueButton.setOnAction(e -> createEncryptModePanel());

        // Создание кнопки "Вернуться назад" с иконкой
        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    mainPane.getChildren().clear();
                    createEncryptBeginPanel();
                }
        );

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        BorderPane topContainer = new BorderPane();
        topContainer.setCenter(topCenterContainer);
        topContainer.setLeft(topLeftContainer);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowRect = new Region();
        spaceBelowRect.setStyle("-fx-background-color: transparent;");
        spaceBelowRect.setPrefHeight(50); // Устанавливаем фиксированную высоту

        // Размещение темно-серого прямоугольника с изображением
        HBox contentContainer = new HBox(imageView);
        contentContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с контейнером
        StackPane rectContainer = new StackPane(darkGrayRect, contentContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Создание контейнера для кнопок "Продолжить шифрование"
        HBox bottomButtonsContainer = new HBox(20, continueButton);
        bottomButtonsContainer.setAlignment(Pos.CENTER);
        bottomButtonsContainer.setPadding(new Insets(0, 0, 50, 0));

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

        mainContainer.setBottom(bottomButtonsContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    public void createEncryptModePanel() {
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Создание текста "Выберите изображение-ключ:"
        Label titleLabel = new Label("Выберите изображение-ключ:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50); // Устанавливаем фиксированную высоту

        // Создание темно-серого прямоугольника
        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(720, 540);
        darkGrayRect.setMinSize(720, 540);
        darkGrayRect.setMaxSize(720, 540);

        ImageView imageView = tempFileManager.loadInputImageFromTemp();
        if (imageView == null) {
            return;
        }

        Button generateButton = new Button("Сгенерировать изображение-ключ");
        generateButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white;" +
                " -fx-font-size: 20px;");
        generateButton.setPrefSize(400, 65);
        generateButton.setOnAction(e -> createEncryptGeneratePanel());

        // Создание кнопки "Ввести параметры ключа вручную"
        Button manualButton = new Button("Ввести параметры ключа вручную");
        manualButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white;" +
                " -fx-font-size: 20px;");
        manualButton.setPrefSize(400, 65);
        manualButton.setOnAction(e -> createManualEncryptionPanel());

        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    mainPane.getChildren().clear();
                    createEncryptBeginPanel();
                }
        );

        // Размещение кнопок в контейнере
        VBox buttonContainer = new VBox(20, generateButton, manualButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с кнопками
        StackPane rectContainer = new StackPane(darkGrayRect, buttonContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение изображения и темно-серого прямоугольника
        StackPane contentContainer = new StackPane(imageView, rectContainer);
        contentContainer.setAlignment(Pos.CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowRect = new Region();
        spaceBelowRect.setStyle("-fx-background-color: transparent;");
        spaceBelowRect.setPrefHeight(50); // Устанавливаем фиксированную высоту

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, spaceBelowTitle, contentContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        BorderPane topContainer = new BorderPane();
        topContainer.setCenter(topCenterContainer);
        topContainer.setLeft(topLeftContainer);

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);

    }

    private void createEncryptGeneratePanel() {
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Создание текста "Ваше изображение-ключ:"
        Label titleLabel = new Label("Ваше изображение-ключ:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(35); // Расстояние под текстом

        // Создание темно-серого прямоугольника
        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(720, 540);
        darkGrayRect.setMinSize(720, 540);
        darkGrayRect.setMaxSize(720, 540);

        // Загрузка изображения из папки temp
        ImageView imageView = tempFileManager.loadInputImageFromTemp();
        if (imageView == null) {
            return;
        }

        Label loadingLabel = new Label("Картинка генерируется...");
        loadingLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        loadingLabel.setAlignment(Pos.CENTER);

        StackPane loadingContainer = new StackPane(loadingLabel);
        loadingContainer.setAlignment(Pos.CENTER);

        // Создание кнопки "Сгенерировать заново вручную"
        Button manualButton = createIconButton(
                "/elements/icon_writeParams.png",
                () -> {
                    cancelCurrentTask();
                    createManualEncryptionPanel();
                }
        );

        // Создание кнопки "Зашифровать изображение полностью" с иконкой
        Button okayButton = createIconButton(
                "/elements/icon_next.png",
                () -> {
                    BufferedImage imageToEncrypt = tempFileManager.loadBufferedImageFromTemp(getTempPath() + "input.png");
                    if (imageToEncrypt != null) {
                        try {
                            createEncryptFinalPanel(imageToEncrypt);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        } finally {
                            imageToEncrypt.flush();
                        }
                    }
                }
        );

        // Создание кнопки "Назад"
        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    cancelCurrentTask();
                    mainPane.getChildren().clear();
                    createEncryptModePanel();
                }
        );

        // Добавление иконки swap
       Button swapButton = createIconButton(
                "/elements/icon_swap.png",
                this::panelForChooseAreaForEncrypt
        );

        // Создание кнопки "Сгенерировать заново" с иконкой
        Button regenerateButton = createIconButton(
                "/elements/icon_repeat.png",
                () -> {
                    cancelCurrentTask();
                    loadingContainer.getChildren().clear();
                    loadingContainer.getChildren().add(loadingLabel);
                    createEncryptGeneratePanel();
                }
        );

        // Размещение кнопок в контейнере
        HBox buttonContainer = new HBox(20, regenerateButton, manualButton, okayButton, swapButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с картинкой
        StackPane rectContainer = new StackPane(imageView, darkGrayRect, loadingContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Создание блока-подсказки
        VBox hintBox = createHintBoxForEncrypt();
        hintBox.setAlignment(Pos.CENTER);
        hintBox.setPrefSize(300, 230);
        hintBox.setMinSize(300, 230);
        hintBox.setMaxSize(300, 230);

        TextArea console = new TextArea();
        console.setEditable(false);
        console.setStyle("-fx-font-family: monospace; -fx-font-size: 14px; -fx-text-fill: white;" +
                " -fx-control-inner-background: #001F3D; -fx-background-color: #001F3D;");
        console.setPrefSize(400, 200); // Консоль под hintBox
        console.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE); // Позволяем консоли растягиваться
        // После добавления в сцену применяем стили к ползункам
        Platform.runLater(() -> {
            // Общие стили для скроллбаров
            String scrollBarStyle = "-fx-background-color: #001F3D; -fx-background-radius: 5px;";
            String trackStyle = "-fx-background-color: #001F3D; -fx-border-color: transparent;";
            String thumbStyle = "-fx-background-color: #004488; -fx-background-radius: 5px;";
            String arrowButtonStyle = "-fx-background-color: transparent; -fx-border-color: transparent;";
            String arrowStyle = "-fx-background-color: transparent;";

            // Настройка вертикального скроллбара
            configureScrollBar(console, ".scroll-bar:vertical", scrollBarStyle + " -fx-pref-width: 10px;");
            configureScrollBar(console, ".scroll-bar:vertical .track", trackStyle);
            configureScrollBar(console, ".scroll-bar:vertical .thumb", thumbStyle);
            configureScrollBar(console, ".scroll-bar:vertical .increment-button", arrowButtonStyle);
            configureScrollBar(console, ".scroll-bar:vertical .decrement-button", arrowButtonStyle);
            configureScrollBar(console, ".scroll-bar:vertical .increment-arrow", arrowStyle);
            configureScrollBar(console, ".scroll-bar:vertical .decrement-arrow", arrowStyle);

            // Настройка горизонтального скроллбара
            configureScrollBar(console, ".scroll-bar:horizontal", scrollBarStyle + " -fx-pref-height: 10px;");
            configureScrollBar(console, ".scroll-bar:horizontal .track", trackStyle);
            configureScrollBar(console, ".scroll-bar:horizontal .thumb", thumbStyle);
            configureScrollBar(console, ".scroll-bar:horizontal .increment-button", arrowButtonStyle);
            configureScrollBar(console, ".scroll-bar:horizontal .decrement-button", arrowButtonStyle);
            configureScrollBar(console, ".scroll-bar:horizontal .increment-arrow", arrowStyle);
            configureScrollBar(console, ".scroll-bar:horizontal .decrement-arrow", arrowStyle);
        });

        // Устанавливаем консоль в статическую переменную
        setConsole(console);

        // Создаем VBox для консоли
        VBox consoleContainer = new VBox(console);
        consoleContainer.setAlignment(Pos.CENTER); // Выравниваем консоль по центру
        consoleContainer.setFillWidth(true); // Позволяем консоли растягиваться по ширине
        consoleContainer.setPrefSize(400,200);
        consoleContainer.setMinSize(400, 200);
        consoleContainer.setMaxSize(400, 200);
        consoleContainer.setStyle("-fx-background-color: #001F3D;");

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

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        BorderPane topContainer = new BorderPane();
        topContainer.setCenter(topCenterContainer);
        topContainer.setLeft(topLeftContainer);

        // Создание контейнера для кнопок под прямоугольником
        VBox bottomContainer = new VBox(buttonContainer);
        bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
        bottomContainer.setPadding(new Insets(0, 0, 30, 0)); // Уменьшаем отступ снизу

        // Установка центрального контента
        mainContainer.setCenter(centerGrid);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

        // Установка контейнера внизу
        mainContainer.setBottom(bottomContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);

        // Генерация изображения при первом открытии панели
        generateImage(loadingContainer, getTempPath() + "input.png", regenerateButton,
                manualButton, okayButton, swapButton);
    }

    // Метод для настройки стилей скроллбара
    private void configureScrollBar(TextArea console, String selector, String style) {
        Node node = console.lookup(selector);
        if (node != null) {
            node.setStyle(style);
        } else {
            logger.error("Элемент не найден: {}", selector);
        }
    }

    private VBox createHintBoxForEncrypt() {
        Region hintBackground = new Region();
        hintBackground.setStyle("-fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px;"
                + " -fx-background-color: transparent;");
        hintBackground.setPrefSize(300, 260);

        // Создание заголовка "Подсказка"
        Text hintTitle = new Text("Подсказка");
        hintTitle.setStyle("-fx-font-family: 'Intro Regular'; -fx-font-weight: bold;");
        hintTitle.setFill(Color.WHITE);
        hintTitle.setFont(Font.font("Intro Regular", 18));

        // Создание текста подсказки (с переносом строки)
        Text hintText = new Text("Для шифрования части изображения - перейдите на исходную картинку и выделите желаемую часть");
        hintText.setFill(Color.WHITE);
        hintText.setFont(Font.font("Intro Regular", 14));
        hintText.setWrappingWidth(280); // Ограничиваем ширину, чтобы текст не выходил за рамки

        // Загрузка иконок
        Image repeatIcon = loadImageResource("/elements/icon_repeat.png");
        ImageView repeatIconView = new ImageView(repeatIcon);
        repeatIconView.setFitWidth(30);
        repeatIconView.setFitHeight(30);

        Image manualIcon = loadImageResource("/elements/icon_writeParams.png");
        ImageView manualIconView = new ImageView(manualIcon);
        manualIconView.setFitWidth(30);
        manualIconView.setFitHeight(30);

        Image nextIcon = loadImageResource("/elements/icon_next.png");
        ImageView nextIconView = new ImageView(nextIcon);
        nextIconView.setFitWidth(30);
        nextIconView.setFitHeight(30);

        Image swapIcon = loadImageResource("/elements/icon_swap.png");
        ImageView swapIconView = new ImageView(swapIcon);
        swapIconView.setFitWidth(30);
        swapIconView.setFitHeight(30);

        HBox repeatBox = createIconTextRow(repeatIconView, "пересоздать ключ;");
        HBox manualBox = createIconTextRow(manualIconView, "создать ключ по заданным вручную параметрам;");
        HBox nextBox = createIconTextRow(nextIconView, "зашифровать изображение полностью;");
        HBox swapBox = createIconTextRow(swapIconView, "свапнуть картинку;");

        VBox hintContent = new VBox(8);
        hintContent.getChildren().addAll(
                hintTitle,
                repeatBox,
                manualBox,
                nextBox,
                swapBox,
                hintText
        );
        hintContent.setAlignment(Pos.TOP_LEFT);
        hintContent.setPadding(new Insets(10, 15, 10, 15));

        // Общий контейнер (StackPane для фона и содержимого)
        StackPane hintContainer = new StackPane(hintBackground, hintContent);
        StackPane.setAlignment(hintContent, Pos.TOP_LEFT);

        // Основной VBox (можно регулировать размеры здесь)
        VBox mainHintBox = new VBox(hintContainer);
        mainHintBox.setAlignment(Pos.CENTER);
        mainHintBox.setPrefSize(300, 230);
        mainHintBox.setMinSize(300, 230);
        mainHintBox.setMaxSize(300, 230);

        return mainHintBox;
    }

    // Вспомогательный метод для создания строки с иконкой и текстом
    private HBox createIconTextRow(ImageView iconView, String text) {
        Text textNode = new Text(" " + text); // Добавляем пробел для отступа от иконки
        textNode.setFill(Color.WHITE);
        textNode.setFont(Font.font("Intro Regular", 14));

        HBox row = new HBox(5, iconView, textNode); // Отступ между иконкой и текстом 5px
        row.setAlignment(Pos.CENTER_LEFT); // Выравниваем по центру по вертикали

        return row;
    }

    private void generateImage(StackPane imageContainer, String imagePath, Button... buttonsToDisable) {
        for (Button button : buttonsToDisable) {
            button.setDisable(true);
        }

        Task<Image> generateImageTask = new Task<>() {
            @Override
            protected Image call() {
                Image image = new Image("file:" + imagePath);
                MandelbrotService mandelbrotService =
                        new MandelbrotService((int) image.getWidth(), (int) image.getHeight());
                BufferedImage mandelbrotImage = mandelbrotService.generateImage();

                if (isCancelled()) {
                    updateMessage("Задача отменена");
                    mandelbrotImage.flush();
                    return null;
                }

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

            for (Button button : buttonsToDisable) {
                button.setDisable(false);
            }
        });

        generateImageTask.setOnFailed(e -> {
            logger.error("Ошибка при генерации изображения: {}", generateImageTask.getException().getMessage());
            dialogDisplayer.showErrorDialog("Ошибка при генерации изображения: " + generateImageTask.getMessage());

            for (Button button : buttonsToDisable) {
                button.setDisable(false);
            }
        });

        generateImageTask.setOnCancelled(e -> {
            logger.info("Задача генерации изображения отменена");
            for (Button button : buttonsToDisable) {
                button.setDisable(false);
            }
        });

        new Thread(generateImageTask).start();

        currentTask = generateImageTask;
    }

    private void cancelCurrentTask() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
            currentTask = null;
        }
    }

    private void panelForChooseAreaForEncrypt() {
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        Label titleLabel = new Label("Шифруемое изображение:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(35);

        String tempPath = getTempPath();
        String inputFilePath = tempPath + "input.png";
        File inputFile = new File(inputFilePath);
        if (!inputFile.exists() || !inputFile.canRead()) {
            logger.error("Файл изображения не найден: {}", inputFilePath);
            dialogDisplayer.showErrorDialog("Выбранный файл изображения не найден: " + inputFilePath);
            return;
        }
        Image image = new Image(inputFile.toURI().toString());
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(720);
        imageView.setFitHeight(540);

        String mandelbrotFilePath = tempPath + "mandelbrot.png";
        File mandelbrotFile = new File(mandelbrotFilePath);
        if (!mandelbrotFile.exists() || !mandelbrotFile.canRead()) {
            logger.error("Файл изображения не найден: {}", mandelbrotFilePath);
            dialogDisplayer.showErrorDialog("Изображение-ключ не найден: " + mandelbrotFilePath);
            return;
        }
        Image mandelbrotImage = new Image(mandelbrotFile.toURI().toString());
        ImageView mandelbrotImageView = new ImageView(mandelbrotImage);
        mandelbrotImageView.setFitWidth(640);
        mandelbrotImageView.setFitHeight(480);
        mandelbrotImageView.setTranslateX(150); // Выглядывание на 150px

        Button encryptWholeButton = createIconButton(
                "/elements/icon_encryptWhole.png",
                () -> {
                    BufferedImage imageToEncrypt = tempFileManager.loadBufferedImageFromTemp(inputFilePath);
                    if (imageToEncrypt != null) {
                        try {
                            createEncryptFinalPanel(imageToEncrypt);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        } finally {
                            imageToEncrypt.flush();
                        }
                    }
                }
        );

        // Создание кнопки "Вернуться назад" с иконкой
        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    cancelCurrentTask();
                    clearRectangles();
                    mainPane.getChildren().clear();
                    createEncryptModePanel();
                }
        );

        // Добавление иконки swap
        Button swapButton = createIconButton(
                "/elements/icon_swap.png",
                () -> {
                    createChoosenMandelbrotPanel(getTempPath() + "mandelbrot.png");
                    clearRectangles();
                }
        );

        StackPane imageContainer = new StackPane(imageView); // Canvas поверх изображения

        canvas = new Canvas(720, 540); // Размер canvas соответствует отображаемому изображению
        coordUtils = new CoordinateUtils(canvas);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        double scaleX = image.getWidth() / canvas.getWidth();
        double scaleY = image.getHeight() / canvas.getHeight();

        canvas.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                startPoint = new Point2D(e.getX(), e.getY());
                drawingRectangle = true;
                logger.info("Начало выделения: {}", startPoint);
            } else if (e.isSecondaryButtonDown()) {
                rectangles.clear();
                rectangleSelected = false;
                gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                logger.info("Очистка прямоугольников");
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.PRIMARY && !rectangleSelected) {
                endPoint = new Point2D(e.getX(), e.getY());
                drawingRectangle = false;
                logger.info("Конечная точка: {}", endPoint);
                if (startPoint != null && endPoint != null && !startPoint.equals(endPoint)) {
                    double x = Math.min(startPoint.getX(), endPoint.getX());
                    double y = Math.min(startPoint.getY(), endPoint.getY());
                    double width = Math.abs(startPoint.getX() - endPoint.getX());
                    double height = Math.abs(startPoint.getY() - endPoint.getY());

                    Rectangle2D imageRect = coordUtils.convertCanvasToImageCoords(
                            x, y, width, height,
                            image.getWidth(), image.getHeight());

                    rectangles.add(imageRect);
                    rectangleSelected = true;
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
        Button encryptPartButton = createIconButton(
                "/elements/icon_encryptPart.png",
                () -> {
                    if (!hasRectangle()) {
                        dialogDisplayer.showErrorMessage("Необходимо выделить зону шифрования!");
                        return;
                    }

                    try {
                        BufferedImage imageToEncrypt = tempFileManager.loadBufferedImageFromTemp(inputFilePath);
                        Rectangle2D selectedRectangle = getSelectedRectangle();

                        BufferedImage encryptedImage = ImageEncrypt.encryptSelectedArea(
                                imageToEncrypt, selectedRectangle);

                        createEncryptFinalPanelForSelectedImage(encryptedImage);
                        clearRectangles();
                    } catch (IllegalArgumentException | IllegalStateException ex) {
                        dialogDisplayer.showErrorMessage("Ошибка: " + ex.getMessage());
                        logger.error("Validation error: {}", ex.getMessage());
                    } catch (RasterFormatException ex) {
                        dialogDisplayer.showErrorMessage("Выделенная область выходит за границы изображения!");
                        logger.error("Bounds error: {}", ex.getMessage());
                    } catch (Exception ex) {
                        dialogDisplayer.showErrorMessage("Произошла ошибка при шифровании");
                        logger.error("Encryption error: {}", ex.getMessage(), ex);
                    }
                }
        );

        // Создание кнопки "Выбрать другую область" с иконкой
        Button resetPartButton = createIconButton(
                "/elements/icon_resetPart.png",
                () -> {
                    rectangles.clear();
                    rectangleSelected = false;
                    gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight()); // Очищаем canvas
                    clearRectangles();
                }
        );

        // Размещение кнопок в контейнере
        HBox buttonContainer = new HBox(20, encryptWholeButton, encryptPartButton, resetPartButton, swapButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с картинкой
        StackPane rectContainer = new StackPane(mandelbrotImageView, imageContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Создание блока-подсказки
        VBox hintBox = createHintBoxForEncryptPart();
        hintBox.setAlignment(Pos.CENTER);
        hintBox.setPrefSize(300, 230);
        hintBox.setMinSize(300, 230);
        hintBox.setMaxSize(300, 230);

        // Создаем GridPane для размещения hintBox и contentBox
        GridPane centerGrid = new GridPane();
        centerGrid.setAlignment(Pos.CENTER); // Выравниваем по центру
        centerGrid.add(hintBox, 0, 0); // hintBox в первой колонке
        centerGrid.add(contentBox, 1, 0); // contentBox во второй колонке
        centerGrid.setPadding(new Insets(0, 20, 0, 60)); // Отступ слева 60px

        // Устанавливаем пропорции для колонок
        ColumnConstraints hintColumn = new ColumnConstraints();
        hintColumn.setPrefWidth(300);
        hintColumn.setHgrow(Priority.NEVER); // hintBox не растягивается

        ColumnConstraints contentColumn = new ColumnConstraints();
        contentColumn.setHgrow(Priority.ALWAYS); // contentBox занимает всё доступное пространство

        centerGrid.getColumnConstraints().addAll(hintColumn, contentColumn);

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        BorderPane topContainer = new BorderPane();
        topContainer.setCenter(topCenterContainer);
        topContainer.setLeft(topLeftContainer);

        // Создание контейнера для кнопок под прямоугольником
        VBox bottomContainer = new VBox(buttonContainer);
        bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
        bottomContainer.setPadding(new Insets(0, 0, 30, 0)); // Уменьшаем отступ снизу

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

        // Установка центрального контента
        mainContainer.setCenter(centerGrid);

        // Установка контейнера внизу
        mainContainer.setBottom(bottomContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    private VBox createHintBoxForEncryptPart() {
        // Основной контейнер с фиксированными размерами (300x230)
        VBox mainBox = new VBox();
        mainBox.setAlignment(Pos.CENTER);
        mainBox.setPrefSize(300, 230);
        mainBox.setMinSize(300, 230);
        mainBox.setMaxSize(300, 230);

        // Фоновый прямоугольник (немного меньше, чтобы был отступ)
        Region hintBackground = new Region();
        hintBackground.setStyle("-fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px;"
                + " -fx-background-color: transparent;");
        hintBackground.setPrefSize(290, 220);

        // Заголовок
        Text hintTitle = new Text("Подсказка");
        hintTitle.setStyle("-fx-font-family: 'Intro Regular'; -fx-font-weight: bold;");
        hintTitle.setFill(Color.WHITE);
        hintTitle.setFont(Font.font("Intro Regular", 18));

        // Текст подсказки с переносами
        Text hintText = new Text("Для шифрования части изображения\nвыделите нужную область.");
        hintText.setFill(Color.WHITE);
        hintText.setFont(Font.font("Intro Regular", 14));
        hintText.setWrappingWidth(270); // Ширина с учетом отступов

        // Иконки
        Image encryptWholeIcon = loadImageResource("/elements/icon_encryptWhole.png");
        ImageView encryptWholeIconView = new ImageView(encryptWholeIcon);
        encryptWholeIconView.setFitWidth(25); // Уменьшаем иконки
        encryptWholeIconView.setFitHeight(25);

        Image resetPartIcon = loadImageResource("/elements/icon_resetPart.png");
        ImageView resetPartIconView = new ImageView(resetPartIcon);
        resetPartIconView.setFitWidth(25);
        resetPartIconView.setFitHeight(25);

        Image encryptPartIcon = loadImageResource("/elements/icon_encryptPart.png");
        ImageView encryptPartIconView = new ImageView(encryptPartIcon);
        encryptPartIconView.setFitWidth(25);
        encryptPartIconView.setFitHeight(25);

        // Строки с иконками
        HBox encryptWholeBox = createIconTextRow(encryptWholeIconView, "зашифровать изображение полностью;");
        HBox encryptPartBox = createIconTextRow(encryptPartIconView, "зашифровать выделенную часть;");
        HBox resetPartBox = createIconTextRow(resetPartIconView, "сбросить выделение (ПКМ);");

        // Основное содержимое
        VBox content = new VBox(5);
        content.getChildren().addAll(
                hintTitle,
                encryptWholeBox,
                encryptPartBox,
                resetPartBox,
                hintText
        );
        content.setAlignment(Pos.TOP_LEFT);
        content.setPadding(new Insets(10));

        // Центрируем содержимое
        StackPane container = new StackPane(hintBackground, content);
        mainBox.getChildren().add(container);

        return mainBox;
    }

    private void createEncryptGeneratePanelWithParams(String filePath) {
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Создание текста "Ваше изображение-ключ:"
        Label titleLabel = new Label("Ваше изображение-ключ:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50);

        // Создание темно-серого прямоугольника
        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(720, 540);
        darkGrayRect.setMinSize(720, 540);
        darkGrayRect.setMaxSize(720, 540);

        // Загрузка изображения из папки temp
        ImageView imageView = tempFileManager.loadInputImageFromTemp();
        if (imageView == null) {
            return;
        }

        // Создание текста "Картинка генерируется..."
        Label loadingLabel = new Label("Картинка генерируется...");
        loadingLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        loadingLabel.setAlignment(Pos.CENTER);

        // Создание контейнера для текста "Картинка генерируется..."
        StackPane loadingContainer = new StackPane(loadingLabel);
        loadingContainer.setAlignment(Pos.CENTER);

        // Создание кнопки "Сгенерировать заново вручную" с иконкой
        Button manualButton = createIconButton(
                "/elements/icon_writeParams.png",
                () -> {
                    cancelCurrentTask();
                    createManualEncryptionPanel();
                }
        );

        // Создание кнопки "Зашифровать изображение полностью" с иконкой
        Button okayButton = createIconButton(
                "/elements/icon_next.png",
                () -> {
                    BufferedImage imageToEncrypt = tempFileManager.loadBufferedImageFromTemp(getTempPath() + "input.png");
                    if (imageToEncrypt != null) {
                        try {
                            createEncryptFinalPanel(imageToEncrypt);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        } finally {
                            imageToEncrypt.flush();
                        }
                    }
                }
        );

        // Создание кнопки "Вернуться назад" с иконкой
        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    cancelCurrentTask();
                    mainPane.getChildren().clear();
                    createEncryptModePanel();
                }
        );

        // Создание кнопки "Сгенерировать заново" с иконкой
        Button regenerateButton = createIconButton(
                "/elements/icon_repeat.png",
                () -> {
                    cancelCurrentTask();
                    loadingContainer.getChildren().clear();
                    loadingContainer.getChildren().add(loadingLabel);
                    createEncryptGeneratePanel();
                }
        );

        // Добавление иконки swap
        Button swapButton = createIconButton(
                "/elements/icon_swap.png",
                this::panelForChooseAreaForEncrypt
        );

        // Размещение кнопок в контейнере
        HBox buttonContainer = new HBox(20, regenerateButton, manualButton, okayButton, swapButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с картинкой
        StackPane rectContainer = new StackPane(imageView, darkGrayRect, loadingContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Создание блока-подсказки
        VBox hintBox = createHintBoxForEncrypt();
        hintBox.setAlignment(Pos.CENTER);
        hintBox.setPrefSize(300, 230);
        hintBox.setMinSize(300, 230);
        hintBox.setMaxSize(300, 230);

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

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        BorderPane topContainer = new BorderPane();
        topContainer.setCenter(topCenterContainer);
        topContainer.setLeft(topLeftContainer);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

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

        Task<Image> generateImageTask = new Task<>() {
            @Override
            protected Image call() throws IOException {
                Image image = new Image("file:" + getTempPath() + "input.png");
                MandelbrotParams params = BinaryFile.loadMandelbrotParamsFromBinaryFile(filePath);

                double zoom = params.zoom();
                double offsetX = params.offsetX();
                double offsetY = params.offsetY();
                int maxIter = params.maxIter();

                int width = (int) image.getWidth();
                int height = (int) image.getHeight();

                MandelbrotService mandelbrotService = new MandelbrotService(width, height);
                BufferedImage mandelbrotImage = mandelbrotService.generateImage(width, height,
                        zoom, offsetX, offsetY, maxIter);
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

            tempFileManager.saveMandelbrotToTemp(SwingFXUtils.fromFXImage(mandelbrotImage, null));
        });

        generateImageTask.setOnFailed(e -> {
            logger.error("Ошибка при генерации изображения: {}", generateImageTask.getException().getMessage());
            dialogDisplayer.showErrorMessage("Ошибка при генерации изображения: " + generateImageTask.getMessage());
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
        logger.info("Есть ли прямоугольник: {}", hasRect);
        return hasRect;
    }

    private Rectangle2D getSelectedRectangle() {
        if (!rectangles.isEmpty()) {
            return rectangles.getLast();
        }
        return null;
    }

    // Метод для очистки прямоугольников
    private void clearRectangles() {
        logger.info("Очистка canvas от прямоугольников");
        rectangles.clear();
        rectangleSelected = false;
        if (canvas != null) {
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            gc.setStroke(Color.TRANSPARENT);
            gc.strokeRect(0, 0, 0, 0);
        }
    }

    private void createEncryptFinalPanel(BufferedImage image) throws IOException{
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Создание текста "Полученное зашифрованное изображение:"
        Label titleLabel = new Label("Полученное зашифрованное изображение:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50); // Расстояние под текстом

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
        Button saveButton = createIconButton(
                "/elements/icon_save.png",
                () -> saveEncryptedImage(encryptedImage)
        );

        // Создание кнопки "Вернуться назад" с иконкой
        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    mainPane.getChildren().clear();
                    panelForChooseAreaForEncrypt();
                }
        );

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        BorderPane topContainer = new BorderPane();
        topContainer.setCenter(topCenterContainer);
        topContainer.setLeft(topLeftContainer);

        // Размещение кнопок в контейнере
        HBox buttonContainer = new HBox(20, saveButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с картинкой
        StackPane rectContainer = new StackPane(imageContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

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
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Создание текста "Полученное зашифрованное изображение:"
        Label titleLabel = new Label("Полученное зашифрованное изображение:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50);

        // Создание ImageView для зашифрованного изображения
        ImageView imageView = new ImageView(SwingFXUtils.toFXImage(image, null));
        imageView.setFitWidth(640);
        imageView.setFitHeight(450);

        // Создание контейнера для изображения
        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setAlignment(Pos.CENTER);

        // Создание кнопки "Сохранить изображение" с иконкой
        Button saveButton = createIconButton(
                "/elements/icon_save.png",
                () -> {
                    saveEncryptedImage(image);
                    createStartPanel();
                }
        );

        // Создание кнопки "Вернуться назад" с иконкой
        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    mainPane.getChildren().clear();
                    panelForChooseAreaForEncrypt();
                }
        );

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        BorderPane topContainer = new BorderPane();
        topContainer.setCenter(topCenterContainer);
        topContainer.setLeft(topLeftContainer);

        // Размещение кнопок в контейнере
        HBox buttonContainer = new HBox(20, saveButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с картинкой
        StackPane rectContainer = new StackPane(imageContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

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
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Создание GridPane для размещения элементов
        GridPane manualEncryptPanel = new GridPane();
        manualEncryptPanel.setPadding(new Insets(20));
        manualEncryptPanel.setHgap(15);
        manualEncryptPanel.setVgap(15);

        Label titleLabel = new Label("Введите значения параметров:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");

        Label zoomLabel = new Label("Масштаб множества:");
        zoomLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        TextField zoomField = new TextField();
        zoomField.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: black; -fx-font-size: 22px;");
        zoomField.setPromptText("135000");
        zoomField.setTextFormatter(new TextFormatter<>(numberFilter.createIntegerFilter(7)));

        Label iterationsLabel = new Label("Число итераций:");
        iterationsLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        TextField iterationsField = new TextField();
        iterationsField.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: black; -fx-font-size: 22px;");
        iterationsField.setPromptText("1000");
        iterationsField.setTextFormatter(new TextFormatter<>(numberFilter.createIntegerFilter(5)));

        Label xLabel = new Label("Смещение по оси X:");
        xLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        TextField xField = new TextField();
        xField.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: black; -fx-font-size: 22px;");
        xField.setPromptText("-0.9999");
        xField.setTextFormatter(new TextFormatter<>(numberFilter.createDoubleFilter()));

        Label yLabel = new Label("Смещение по оси Y:");
        yLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        TextField yField = new TextField();
        yField.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: black; -fx-font-size: 22px;");
        yField.setPromptText("0.9999");
        yField.setTextFormatter(new TextFormatter<>(numberFilter.createDoubleFilter()));

        Button saveButton = new Button("Сохранить сгенерированный ключ");
        saveButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-text-fill: white; -fx-font-size: 24px; -fx-border-radius: 10px; -fx-border-width: 5px;" +
                " -fx-border-color: white;");
        saveButton.setOnAction(e -> {
            try {
                double zoom = Double.parseDouble(zoomField.getText());
                int iterations = Integer.parseInt(iterationsField.getText());
                double x = Double.parseDouble(xField.getText());
                double y = Double.parseDouble(yField.getText());

                EncryptionService service = new EncryptionService();
                Image image = new Image("file:" + getTempPath() + "input.png");
                int width = (int) image.getWidth();
                int height = (int) image.getHeight();
                service.saveMandelbrotParameters(width, height, zoom, iterations, x, y);

                createEncryptGeneratePanelWithParams(getTempPath() + "mandelbrot_params.bin");
            } catch (NumberFormatException ex) {
                dialogDisplayer.showErrorDialog("Некорректный формат данных");
            } catch (IllegalArgumentException ex) {
                dialogDisplayer.showErrorDialog(ex.getMessage());
            }
        });

        // Создание HBox для выравнивания кнопки "Сохранить сгенерированный ключ" по центру
        HBox saveButtonContainer = new HBox(saveButton);
        saveButtonContainer.setAlignment(Pos.CENTER); // Выравнивание по центру

        // Кнопка "Вернуться назад" с иконкой
        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    mainPane.getChildren().clear();
                    createEncryptModePanel();
                }
        );

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        BorderPane topContainer = new BorderPane();
        topContainer.setCenter(topCenterContainer);
        topContainer.setLeft(topLeftContainer);

        // Добавляем элементы в GridPane
        manualEncryptPanel.add(zoomLabel, 0, 1);
        manualEncryptPanel.add(zoomField, 1, 1);
        manualEncryptPanel.add(iterationsLabel, 0, 2);
        manualEncryptPanel.add(iterationsField, 1, 2);
        manualEncryptPanel.add(xLabel, 0, 3);
        manualEncryptPanel.add(xField, 1, 3);
        manualEncryptPanel.add(yLabel, 0, 4);
        manualEncryptPanel.add(yField, 1, 4);

        HBox saveButtonBox = new HBox(saveButton);
        saveButtonBox.setPadding(new Insets(0, 0, 0, 20));
        saveButtonBox.setAlignment(Pos.CENTER);
        manualEncryptPanel.add(saveButtonBox, 0, 7, 2, 1); // Кнопка сохранения

        // Создание HBox для обертки GridPane и выравнивания по центру
        HBox gridPaneContainer = new HBox(manualEncryptPanel);
        gridPaneContainer.setAlignment(Pos.CENTER); // Выравнивание по центру

        // Создание VBox для вертикального выравнивания
        VBox vbox = new VBox(gridPaneContainer);
        vbox.setAlignment(Pos.CENTER); // Выравнивание по центру

        // Установка VBox в центр основного контейнера
        mainContainer.setCenter(vbox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    private void createChoosenMandelbrotPanel(String imagePath) {
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Загрузка изображения input.png из папки temp
        String inputFilePath = getTempPath() + "input.png";
        Image imageInput = tempFileManager.loadImageFromTemp(inputFilePath);
        if (imageInput == null) {
            logger.error("Файл изображения не найден: {}", inputFilePath);
            return;
        }
        ImageView imageView = new ImageView(imageInput);
        imageView.setFitWidth(640);
        imageView.setFitHeight(480);
        imageView.setTranslateX(150); // Выглядывание на 150px

        // Создание текста "Ваше изображение-ключ:"
        Label titleLabel = new Label("Ваше изображение-ключ:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        //titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(35); // Расстояние под текстом

        // Проверяем, существует ли файл mandelbrot.png в папке temp
        String mandelbrotFilePath = getTempPath() + "mandelbrot.png";
        File mandelbrotFile = new File(mandelbrotFilePath);

        BufferedImage mandelbrotImage;

        if (!mandelbrotFile.exists()) {
            MandelbrotService mandelbrotService = new MandelbrotService((int) imageInput.getWidth(), (int) imageInput.getHeight());
            mandelbrotImage = mandelbrotService.generateAfterGetParams(imagePath);
            if (mandelbrotImage == null) {
                logger.error("Ошибка: не удалось сгенерировать изображение!");
                dialogDisplayer.showErrorMessage("Ошибка: не удалось сгенерировать изображение!");
                return;
            }

            // Сохраняем сгенерированное изображение в файл mandelbrot.png
            try {
                ImageIO.write(mandelbrotImage, "png", mandelbrotFile);
            } catch (IOException e) {
                logger.error("Ошибка при сохранении изображения: {}", e.getMessage());
                dialogDisplayer.showErrorMessage("Ошибка при сохранении изображения: " + e.getMessage());
                return;
            }
        } else {
            // Если файл существует, загружаем изображение из файла
            try {
                ImageIO.read(mandelbrotFile);
            } catch (IOException e) {
                logger.error("Ошибка при загрузке изображения: {}", e.getMessage());
                dialogDisplayer.showErrorMessage("Ошибка при загрузке изображения: " + e.getMessage());
                return;
            }
        }

        // Загрузка изображения mandelbrot.png из папки temp
        Image imageMandelbrot = tempFileManager.loadImageFromTemp(mandelbrotFilePath);
        if (imageMandelbrot == null) {
            return;
        }
        ImageView imageViewMandelbrot = new ImageView(imageMandelbrot);
        imageViewMandelbrot.setFitWidth(720);
        imageViewMandelbrot.setFitHeight(540);

        // Создание кнопки "Сгенерировать заново" с иконкой
        Button regenerateButton = createIconButton(
                "/elements/icon_repeat.png",
                () -> {
                    cancelCurrentTask();
                    createEncryptGeneratePanel();
                    logger.info("Кнопка 'Сгенерировать заново' нажата");
                }
        );

        // Создание кнопки "Сгенерировать заново вручную"
        Button manualButton = createIconButton(
                "/elements/icon_writeParams.png",
                () -> {
                    cancelCurrentTask();
                    createManualEncryptionPanel();
                }
        );

        // Создание кнопки "Зашифровать изображение полностью" с иконкой
        Button okayButton = createIconButton(
                "/elements/icon_next.png",
                () -> {
                    BufferedImage imageToEncrypt = tempFileManager.loadBufferedImageFromTemp(inputFilePath);
                    if (imageToEncrypt != null) {
                        try {
                            createEncryptFinalPanel(imageToEncrypt);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        } finally {
                            imageToEncrypt.flush();
                        }
                    }
                }
        );

        // Создание кнопки "Вернуться назад" с иконкой
        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    cancelCurrentTask();
                    mainPane.getChildren().clear();
                    createEncryptModePanel();
                }
        );

        // Добавление иконки swap
        Button swapButton = createIconButton(
                "/elements/icon_swap.png",
                this::panelForChooseAreaForEncrypt
        );

        // Размещение кнопок в контейнере
        HBox buttonContainer = new HBox(20, regenerateButton, manualButton, okayButton, swapButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение изображения в контейнере
        StackPane imageContainer = new StackPane(imageView, imageViewMandelbrot);
        imageContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, spaceBelowTitle, imageContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Создание блока-подсказки
        VBox hintBox = createHintBoxForEncrypt();
        hintBox.setAlignment(Pos.CENTER); // Выравниваем hintBox по вертикали
        hintBox.setPrefSize(300, 230);
        hintBox.setMinSize(300, 230);
        hintBox.setMaxSize(300, 230);

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

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        BorderPane topContainer = new BorderPane();
        topContainer.setCenter(topCenterContainer);
        topContainer.setLeft(topLeftContainer);

        mainContainer.setTop(topContainer);

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
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Создание текста "Дешифратор"
        Label titleLabel = new Label("Дешифратор");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 64px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50);

        // Создание светло-серого прямоугольника
        Region lightGrayRect = new Region();
        lightGrayRect.setStyle("-fx-background-color: #494949;");
        lightGrayRect.setPrefSize(720, 540);
        lightGrayRect.setMinSize(720, 540);
        lightGrayRect.setMaxSize(720, 540);

        // Создание темно-серого прямоугольника
        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(640, 480);
        darkGrayRect.setMinSize(640, 480);
        darkGrayRect.setMaxSize(640, 480);
        darkGrayRect.setTranslateX(150); // Выглядывание на 150px

        // Создание кнопки "Выбрать файл"
        Button uploadButton = new Button("Выбрать файл");
        uploadButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white;" +
                " -fx-font-size: 28px;");
        uploadButton.setPrefSize(257, 68);
        uploadButton.setOnAction(e -> {
            String imagePath = selectImageFileForDecrypt(); // Получаем путь к файлу input.png
            if (imagePath != null) {
                createDecryptLoadPanel(imagePath); // Передаем путь к файлу в метод
            } else {
                dialogDisplayer.showErrorDialog("Файл не выбран!");
            }
        });

        // Создание кнопки "Вернуться назад" с иконкой
        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    mainPane.getChildren().clear();
                    createStartPanel();
                }
        );

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        BorderPane topContainer = new BorderPane();
        topContainer.setCenter(topCenterContainer);
        topContainer.setLeft(topLeftContainer);

        Region spaceInBottom = new Region();
        spaceInBottom.setStyle("-fx-background-color: transparent;");
        spaceInBottom.setPrefHeight(120); // 120, т.к. на следующей панеле 20+50+50 (bottomButtonsContainer)

        // Размещение кнопки по центру светло-серого прямоугольника
        StackPane buttonContainer = new StackPane(uploadButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого и светло-серого прямоугольников
        StackPane rectContainer = new StackPane(darkGrayRect, lightGrayRect, buttonContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол и установка label по центру вверху
        mainContainer.setTop(topContainer);

        mainContainer.setBottom(spaceInBottom);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    private void createDecryptLoadPanel(String imagePath) {
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Создание текста "Загруженное для расшифровки изображение:"
        Label titleLabel = new Label("Загруженное для расшифровки изображение:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50);

        // Создание темно-серого прямоугольника
        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(640, 480);
        darkGrayRect.setMinSize(640, 480);
        darkGrayRect.setMaxSize(640, 480);
        darkGrayRect.setTranslateX(150); // Выглядывание на 150px

        // Загрузка изображения
        Image image = new Image("file:" + imagePath);
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(720);
        imageView.setFitHeight(540);

        // Создание кнопки "Продолжить расшифровку"
        Button continueButton = new Button("Продолжить расшифровку");
        continueButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white;" +
                " -fx-font-size: 20px;");
        continueButton.setPrefSize(320, 50);
        continueButton.setOnAction(e -> createDecryptModePanel());

        // Создание кнопки "Вернуться назад" с иконкой
        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    mainPane.getChildren().clear();
                    createDecryptBeginPanel();
                }
        );

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20));

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        BorderPane topContainer = new BorderPane();
        topContainer.setLeft(topLeftContainer);
        topContainer.setCenter(topCenterContainer);

        // Размещение темно-серого прямоугольника с изображением
        HBox contentContainer = new HBox(imageView);
        contentContainer.setAlignment(Pos.CENTER);

        // Размещение темно-серого прямоугольника с контейнером
        StackPane rectContainer = new StackPane(darkGrayRect, contentContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

        // Создание контейнера для кнопок "Продолжить расшифровку"
        HBox bottomButtonsContainer = new HBox(20, continueButton);
        bottomButtonsContainer.setAlignment(Pos.CENTER);
        bottomButtonsContainer.setPadding(new Insets(0, 0, 50, 0)); // Отступ снизу

        // Установка контейнера с кнопками внизу
        mainContainer.setBottom(bottomButtonsContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    public void createDecryptModePanel() {
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Создание текста "Дешифратор"
        Label titleLabel = new Label("Дешифратор");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 64px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50);

        // Загрузка изображения input.png
        Image inputImage = new Image("file:" + getTempPath() + "input.png");
        ImageView inputImageView = new ImageView(inputImage);
        inputImageView.setFitWidth(640);
        inputImageView.setFitHeight(480);
        inputImageView.setTranslateX(150); // Сдвигаем прямоугольник вправо на 150px

        // Создание светло-серого прямоугольника
        Region lightGrayRect = new Region();
        lightGrayRect.setStyle("-fx-background-color: #494949;");
        lightGrayRect.setPrefSize(720, 540); // Уменьшаем размер прямоугольника
        lightGrayRect.setMinSize(720, 540);
        lightGrayRect.setMaxSize(720, 540);

        // Создание кнопки "Загрузить файл-ключ"
        Button manualButton = new Button("Загрузить файл-ключ");
        manualButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white;" +
                " -fx-font-size: 22px;");
        manualButton.setPrefSize(287, 68);
        manualButton.setOnAction(e -> {
            File selectedFile = selectKeyFileForDecrypt();
            if (selectedFile != null) {
                createDecryptFinalPanel(selectedFile.getAbsolutePath());
            } else {
                dialogDisplayer.showErrorDialog("Файл-ключ не выбран!");
            }
        });

        // Создание кнопки "Вернуться назад" с иконкой
        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    mainPane.getChildren().clear();
                    createDecryptBeginPanel();
                }
        );

        // Размещение кнопки "Загрузить файл-ключ" по центру светло-серого прямоугольника
        StackPane buttonContainer = new StackPane(manualButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение светло-серого прямоугольника с кнопкой
        StackPane rectContainer = new StackPane(lightGrayRect, buttonContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение изображения input.png под прямоугольником
        StackPane imageContainer = new StackPane(inputImageView, rectContainer);
        imageContainer.setAlignment(Pos.CENTER);

        Region spaceInBottom = new Region();
        spaceInBottom.setStyle("-fx-background-color: transparent;");
        spaceInBottom.setPrefHeight(120); // 120, т.к. на следующей панеле 20+50+50 (bottomButtonsContainer)

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, spaceBelowTitle, imageContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 0, 0, 0));

        BorderPane topContainer = new BorderPane();
        topContainer.setCenter(topCenterContainer);
        topContainer.setLeft(topLeftContainer);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

        mainContainer.setBottom(spaceInBottom);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    private void createDecryptFinalPanel(String keyFilePath) {
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        // Создание текста "Расшифрованное изображение"
        Label titleLabel = new Label("Расшифрованное изображение");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        // Создание прозрачного прямоугольника для расстояния под текстом
        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50);

        // Создание контейнера для изображения
        StackPane imageContainer = new StackPane();
        Label loadingLabel = new Label("Расшифровка изображения...");
        loadingLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        imageContainer.getChildren().add(loadingLabel);

        // Размещение темно-серого прямоугольника с изображением
        StackPane rectContainer = new StackPane(imageContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Создание кнопки "Вернуться назад" с иконкой
        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    mainPane.getChildren().clear();
                    createStartPanel();
                }
        );

        // Размещение кнопки "Вернуться назад" в левом верхнем углу
        HBox topLeftContainer = new HBox(backButton);
        topLeftContainer.setAlignment(Pos.TOP_LEFT);
        topLeftContainer.setPadding(new Insets(20, 0, 0, 20)); // Отступы сверху и слева

        HBox topCenterContainer = new HBox(titleLabel);
        topCenterContainer.setAlignment(Pos.CENTER);
        topCenterContainer.setPadding(new Insets(80, 40, 0, 0));

        BorderPane topContainer = new BorderPane();
        topContainer.setLeft(topLeftContainer);
        topContainer.setCenter(topCenterContainer);

        // Создание кнопки "Сохранить изображение" с иконкой
        Button saveButton = createIconButton(
                "/elements/icon_save.png",
                this::saveDecryptedImage
        );

        // Размещение кнопок в контейнере
        HBox buttonContainer = new HBox(20, saveButton);
        buttonContainer.setAlignment(Pos.CENTER);

        // Размещение кнопки "Сохранить изображение" на 30px от нижнего края окна
        StackPane saveButtonContainer = new StackPane(saveButton);
        saveButtonContainer.setAlignment(Pos.BOTTOM_CENTER);
        saveButtonContainer.setPadding(new Insets(0, 0, 30, 0)); // Отступ снизу 30px

        // Размещение элементов в центре
        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Установка центрального контента
        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопками внизу
        mainContainer.setBottom(saveButtonContainer);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

        // Очистка и добавление нового контента в основной контейнер
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);

        // Запуск задачи расшифровки изображения
        Task<Void> decryptImageTask = new Task<>() {
            @Override
            protected Void call() {
                ImageDecrypt.decryptImage(keyFilePath);
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
                dialogDisplayer.showErrorDialog("Ошибка при загрузке расшифрованного изображения: " + ex.getMessage());
            }
        });

        decryptImageTask.setOnFailed(e ->
                dialogDisplayer.showErrorDialog("Ошибка при расшифровке изображения: " + decryptImageTask.getException().getMessage()));

        new Thread(decryptImageTask).start();
    }

    public void saveEncryptedImage(BufferedImage encryptedImage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить зашифрованное изображение");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "png"));

        File fileToSave = fileChooser.showSaveDialog(primaryStage);

        if (fileToSave != null) {
            String imageFilePath = fileToSave.getAbsolutePath();

            // Добавляем расширение файла, если оно не указано
            if (!imageFilePath.toLowerCase().endsWith(".png")) {
                imageFilePath += ".png";
            }

            try {
                ImageIO.write(encryptedImage, "png", new File(imageFilePath));

                //saveFileWithPathToEncryptImage(imageFilePath);
                saveKeyDecoder(imageFilePath);
                logger.info("Путь к файлу: {}", imageFilePath);
            } catch (IOException e) {
                dialogDisplayer.showErrorDialog("Ошибка при сохранении изображения: " + e.getMessage());
            }
        }
    }

    private void saveKeyDecoder(String imageFilePath) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить key_decoder.bin");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Файл-ключ", "*.bin"));

        File fileToSave = fileChooser.showSaveDialog(primaryStage);

        if (fileToSave != null) {
            String keyDecoderFilePath = fileToSave.getAbsolutePath();

            if (!keyDecoderFilePath.toLowerCase().endsWith(".bin")) {
                keyDecoderFilePath += ".bin";
            }

            try {
                String resourcesPath = getTempPath() + "key_decoder.bin";
                Path sourcePath = Paths.get(resourcesPath);
                Path destinationPath = Paths.get(keyDecoderFilePath);

                Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);

                logger.info("key_decoder.bin сохранен в: {}", keyDecoderFilePath);
                dialogDisplayer.showSuccessDialog("Изображение и файл-ключ успешно сохранены:\n" +
                        "Изображение: " + imageFilePath + "\n" +
                        "Файл-ключ: " + keyDecoderFilePath);

                createStartPanel();
            } catch (Exception e) {
                logger.error("Ошибка при сохранении key_decoder.bin: {}", e.getMessage());
                dialogDisplayer.showErrorDialog("Ошибка при сохранении файл-ключа: " + e.getMessage());
            }
        }
    }

    public File selectMandelbrotForEncrypt() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите файл-ключ");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Бинарный файл", "*.bin")
        );

        File selectedFile = fileChooser.showOpenDialog(primaryStage);

        if (selectedFile != null) {
            String extension = getFileExtension(selectedFile);

            if (extension.equals("bin")) {
                try {
                    MandelbrotParams params = BinaryFile.loadMandelbrotParamsFromBinaryFile(selectedFile.getAbsolutePath());

                    MandelbrotService mandelbrotService = new MandelbrotService(params.startMandelbrotWidth(), params.startMandelbrotHeight());
                    BufferedImage generatedImage = mandelbrotService.generateImage(
                            params.startMandelbrotWidth(),
                            params.startMandelbrotHeight(),
                            params.zoom(),
                            params.offsetX(),
                            params.offsetY(),
                            params.maxIter()
                    );

                    tempFileManager.saveMandelbrotToTemp(generatedImage);
                    EncryptionService service = new EncryptionService();
                    service.saveMandelbrotParameters(params);

                } catch (IOException e) {
                    dialogDisplayer.showErrorDialog("Ошибка при чтении файла-ключа: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    dialogDisplayer.showErrorDialog("Некорректные параметры в файле: " + e.getMessage());
                }
            }
        }

        return selectedFile;
    }

    private String getFileExtension(File file) {
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1).toLowerCase();
    }

    private String selectImageFileForEncrypt() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите изображение для шифрования");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.png",
                "*.jpg", "*.jpeg"));

        File selectedFile = fileChooser.showOpenDialog(primaryStage);

        if (selectedFile != null) {

            String tempPath = getTempPath();
            File tempDir = new File(tempPath);
            tempFileManager.deleteFolder(tempDir);
            tempFileManager.saveInputImageToTemp(selectedFile);
            return getTempPath() + "input.png";
        } else {
            logger.info("Файл не выбран.");
            return null;
        }
    }

    private String selectImageFileForDecrypt() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите изображение для расшифрования");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.jpg",
                "*.jpeg", "*.png", "*.bmp"));

        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile != null) {
            try {
                BufferedImage image = ImageIO.read(selectedFile);
                if (image != null) {

                    String tempPath = getTempPath();
                    File tempDir = new File(tempPath);
                    tempFileManager.deleteFolder(tempDir);
                    tempFileManager.saveInputImageToTemp(selectedFile);
                    logger.info("Изображение сохранено в папку temp: {}", selectedFile.getName());
                    return selectedFile.getAbsolutePath();
                } else {
                    logger.error("Ошибка при загрузке изображения: {}", selectedFile.getAbsolutePath());
                    dialogDisplayer.showErrorMessage("Ошибка при загрузке изображения: " + selectedFile.getAbsolutePath());
                }
            } catch (IOException e) {
                logger.error("Ошибка при загрузке изображения: {}", e.getMessage());
                dialogDisplayer.showErrorMessage("Ошибка при загрузке изображения: " + e.getMessage());
            }
        }
        return null;
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
                BufferedImage decryptedImage = ImageIO.read(new File(getTempPath() + "decrypted_image.png"));

                ImageIO.write(decryptedImage, "png", new File(filePath));

                // Дублируем изображение в src/temp под именем decrypted_image.png
                String resourcesPath = getTempPath() + "decrypted_image.png";
                ImageIO.write(decryptedImage, "png", new File(resourcesPath));

                dialogDisplayer.showSuccessDialog("Изображение успешно сохранено в: " + filePath);

                logger.info("Изображение сохранено в: {}", filePath);

                createStartPanel();
            } catch (IOException e) {
                logger.error("Ошибка при сохранении изображения: {}", e.getMessage());
                dialogDisplayer.showErrorDialog("Ошибка при сохранении изображения: " + e.getMessage());
            }
        }
    }

    private File selectKeyFileForDecrypt() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите файл-ключ для расшифрования");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Файлы ключей", "*.bin"));
        return fileChooser.showOpenDialog(primaryStage);
    }

    // Метод для установки консоли
    public static void setConsole(TextArea console) {
        JavaFXImpl.console = console;
    }

    // Метод для добавления текста в консоль
    public static void logToConsole(String message) {
        if (console != null) {
            console.appendText(message + "\n");
        }
    }

    private Image loadImageResource(String resourcePath) {
        try {
            InputStream inputStream = getClass().getResourceAsStream(resourcePath);
            if (inputStream == null) {
                showErrorAlert("Ошибка ресурса", "Ресурс не найден: " + resourcePath);
                return null;
            }
            return new Image(inputStream);
        } catch (Exception e) {
            showErrorAlert("Ошибка загрузки", "Ошибка загрузки изображения: " + e.getMessage());
            return null;
        }
    }

    private void showErrorAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private Button createIconButton(String iconPath, Runnable onClickAction) {
        Image icon = loadImageResource(iconPath);
        ImageView iconView = new ImageView(icon);
        iconView.setFitWidth(50);
        iconView.setFitHeight(50);

        Button button = new Button("", iconView);
        button.setStyle("-fx-background-color: transparent;");
        button.setPrefSize(50, 50);
        button.setOnAction(e -> onClickAction.run());

        return button;
    }
}