package com.cipher.view.javafx;

import com.cipher.client.service.impl.ClientAuthServiceImpl;
import com.cipher.client.utils.NetworkUtils;
import com.cipher.common.exception.AuthException;
import com.cipher.common.exception.CryptoException;
import com.cipher.common.exception.NetworkException;
import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.service.EncryptionService;
import com.cipher.core.utils.*;
import com.cipher.client.service.impl.SeedServiceImpl;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.application.Platform;

import javax.imageio.ImageIO;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

public class JavaFXImpl extends Application {
    private static final Logger logger = LoggerFactory.getLogger(JavaFXImpl.class);

    private static TextArea console;

    private static ConfigurableApplicationContext springContext;

    private StackPane mainPane;
    private Stage primaryStage;
    private Canvas canvas;
    private CoordinateUtils coordUtils;
    private Task<Image> currentTask;
    private ExecutorService executorService;
    private Point2D startPoint;
    private Point2D endPoint;
    private boolean drawingRectangle = false;
    private boolean rectangleSelected = false;
    private TextField[] wordFields = new TextField[12];
    private SeedServiceImpl seedService;
    private ClientAuthServiceImpl clientAuthService;

    private final List<Rectangle2D> rectangles = new ArrayList<>();
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
    public void init() {
        waitForSpringContext();

        if (springContext != null && springContext.isActive()) {
            this.seedService = springContext.getBean(SeedServiceImpl.class);
            this.clientAuthService = springContext.getBean(ClientAuthServiceImpl.class);
            logger.info("Spring services initialized successfully");
        } else {
            logger.error("Spring context is not available!");
        }
    }

    private void waitForSpringContext() {
        int maxAttempts = 30;
        int attempt = 0;

        while (springContext == null && attempt < maxAttempts) {
            try {
                Thread.sleep(500);
                attempt++;
                logger.info("Waiting for Spring context... Attempt {}", attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (springContext == null) {
            logger.error("Failed to get Spring context after {} attempts", maxAttempts);
        }
    }

    public static void setSpringContext(ConfigurableApplicationContext context) {
        springContext = context;
        logger.info("Spring context set successfully");
    }

    @Override
    public void start(Stage primaryStage) {
        this.executorService = Executors.newCachedThreadPool();
        this.primaryStage = primaryStage;
        mainPane = new StackPane();
        Scene scene = new Scene(mainPane, 1920, 980);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Шифр Мандельброта");

        Image icon = tempFileManager.loadImageResource("/elements/icon.png");
        primaryStage.getIcons().add(icon);
        primaryStage.show();
        showLoadingScreen();

        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            shutdownApplication();
        });
    }

    private void shutdownExecutors() {
        try {
            executorService.shutdown();

            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void shutdownApplication() {
        try {
            shutdownExecutors();
            if (springContext != null) {
                springContext.close();
            }
            Platform.exit();
            System.exit(0);
        } catch (Exception e) {
            logger.error("Error during shutdown: " + e.getMessage());
            System.exit(1);
        }
    }

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
            protected Void call() {
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
            logger.error("Loading failed: {}", ex.getMessage());
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

        Image lockImage = tempFileManager.loadImageResource("/elements/icon_lock.png");
        Image unlockImage = tempFileManager.loadImageResource("/elements/icon_unlock.png");

        ImageView lockImageView = new ImageView(lockImage);
        lockImageView.setFitWidth(615);
        lockImageView.setFitHeight(127);

        ImageView unlockImageView = new ImageView(unlockImage);
        unlockImageView.setFitWidth(615);
        unlockImageView.setFitHeight(127);

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
        connectButton.setOnAction(e -> {
            try {
                NetworkUtils.checkNetworkConnection();
                createStartConnectionPanel();
            } catch (NetworkException ex) {
                dialogDisplayer.showErrorAlert("Нет подключения",
                        "Необходимо интернет-соединение для выхода в сеть\n\n" +
                                "Пожалуйста, проверьте подключение и попробуйте снова"
                );
            } catch (Exception ex) {
                logger.error("Ошибка при входе", ex);
                dialogDisplayer.showErrorAlert("Ошибка", "Не удалось открыть форму входа");
            }
        });

        String buttonStyle = "-fx-background-color: transparent;";
        encryptButton.setStyle(buttonStyle);
        decryptButton.setStyle(buttonStyle);

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

        Button sendButton = new Button("Регистрация");
        sendButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white;" +
                " -fx-font-size: 28px;");
        sendButton.setAlignment(Pos.CENTER);
        sendButton.setOnAction(e -> {
            try {
                if (seedService != null) {
                    String seedPhrase = seedService.generateAccount();
                    logger.info("Seed phrase generated: {}", seedPhrase);
                    createSeedGenerationPanel(seedPhrase);
                } else {
                    dialogDisplayer.showAlert("Ошибка", "Сервис не инициализирован");
                }
            } catch (Exception ex) {
                logger.error("Registration failed", ex);
                dialogDisplayer.showAlert("Ошибка", "Не удалось создать аккаунт: " + ex.getMessage());
            }
        });

        Button getButton = new Button("Авторизация");
        getButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white;" +
                " -fx-font-size: 28px;");
        getButton.setAlignment(Pos.CENTER);
        getButton.setOnAction(e -> {
            mainPane.getChildren().clear();
            createLoginPanel(); // Переход к панели ввода seed-фразы
        });

        HBox buttonContainer = new HBox(20, sendButton, getButton);
        buttonContainer.setAlignment(Pos.CENTER);

        mainContainer.setTop(topContainer);

        mainContainer.setCenter(buttonContainer);

        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);
    }

    //=========================================
    private void createSeedGenerationPanel(String seedPhrase) {
        try {
            BorderPane mainContainer = new BorderPane();
            mainContainer.setBackground(new Background(new BackgroundFill(
                    createGradient(),
                    CornerRadii.EMPTY,
                    Insets.EMPTY
            )));

            // Кнопка назад
            Button backButton = createIconButton(
                    "/elements/icon_back.png",
                    () -> {
                        mainPane.getChildren().clear();
                        createStartConnectionPanel();
                    }
            );

            HBox topLeftContainer = new HBox(backButton);
            topLeftContainer.setAlignment(Pos.TOP_LEFT);
            topLeftContainer.setPadding(new Insets(20, 0, 0, 20));

            Label titleLabel = new Label("Ваша seed-фраза:");
            titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");

            HBox topCenterContainer = new HBox(titleLabel);
            topCenterContainer.setAlignment(Pos.CENTER);
            topCenterContainer.setPadding(new Insets(40, 0, 30, 0));

            BorderPane topContainer = new BorderPane();
            topContainer.setLeft(topLeftContainer);
            topContainer.setCenter(topCenterContainer);

            // Разбиваем фразу на отдельные слова
            String[] words = seedPhrase.split(" ");

            // Создаем сетку для 12 слов (4 строки x 3 столбца)
            GridPane gridPane = new GridPane();
            gridPane.setAlignment(Pos.CENTER);
            gridPane.setHgap(30); // Увеличиваем расстояние между колонками
            gridPane.setVgap(20); // Увеличиваем расстояние между строками
            gridPane.setPadding(new Insets(30));

            // Отображаем 12 слов с нумерацией и крупным шрифтом (48px)
            for (int i = 0; i < 12; i++) {
                int wordNumber = i + 1;

                // Создаем метку с номером слова
                Label numberLabel = new Label(wordNumber + ".");
                numberLabel.setStyle("-fx-text-fill: white; -fx-font-size: 36px; -fx-font-family: 'Intro Regular';");
                numberLabel.setMinWidth(50);
                numberLabel.setAlignment(Pos.CENTER_RIGHT);

                // Создаем метку с самим словом (48px как requested)
                Label wordLabel = new Label(words[i]);
                wordLabel.setStyle("-fx-text-fill: white; -fx-font-size: 48px; -fx-font-family: 'Intro Regular';" +
                        " -fx-font-weight: bold; -fx-background-color: rgba(255,255,255,0.1);" +
                        " -fx-padding: 10px 15px; -fx-background-radius: 8px;");
                wordLabel.setMinWidth(200);
                wordLabel.setAlignment(Pos.CENTER);

                // Создаем контейнер для метки и слова
                HBox wordContainer = new HBox(15, numberLabel, wordLabel);
                wordContainer.setAlignment(Pos.CENTER_LEFT);

                // Размещаем в сетке (4 строки, 3 столбца)
                int row = i / 3;
                int col = i % 3;
                gridPane.add(wordContainer, col, row);
            }

            // Предупреждение для пользователя
            Label warningLabel = new Label("ЗАПИШИТЕ эти слова в безопасном месте!\nЭто единственный способ восстановить доступ к аккаунту.");
            warningLabel.setStyle("-fx-text-fill: #FF6B6B; -fx-font-size: 20px; -fx-font-family: 'Intro Regular';" +
                    " -fx-text-alignment: center; -fx-alignment: center;");
            warningLabel.setTextAlignment(TextAlignment.CENTER);
            warningLabel.setWrapText(true);

            // Кнопка подтверждения
            Button confirmButton = new Button("Я записал слова");
            confirmButton.setStyle("-fx-background-color: #4CAF50; -fx-font-family: 'Intro Regular';" +
                    " -fx-text-fill: white; -fx-font-size: 24px; -fx-background-radius: 10px; -fx-padding: 15px 30px;");
            confirmButton.setOnAction(e -> {
                try {
                    dialogDisplayer.showAlert("Успех", "Аккаунт успешно создан!");
                    mainPane.getChildren().clear();
                    createStartConnectionPanel();
                } catch (Exception ex) {
                    logger.error("Ошибка при создании аккаунта", ex);
                    dialogDisplayer.showErrorAlert("Ошибка", "Не удалось создать аккаунт: " + ex.getMessage());
                }
            });

            VBox centerContainer = new VBox(30, gridPane, warningLabel, confirmButton);
            centerContainer.setAlignment(Pos.CENTER);
            centerContainer.setPadding(new Insets(20));

            mainContainer.setTop(topContainer);
            mainContainer.setCenter(centerContainer);

            mainPane.getChildren().clear();
            mainPane.getChildren().add(mainContainer);
        } catch (Exception e) {
            logger.error("Ошибка создания панели генерации seed", e);
            dialogDisplayer.showErrorAlert("Ошибка", "Не удалось создать интерфейс: " + e.getMessage());
            createStartConnectionPanel();
        }
    }

    public void createLoginPanel() {
        try {
            BorderPane mainContainer = new BorderPane();
            mainContainer.setBackground(new Background(new BackgroundFill(
                    createGradient(),
                    CornerRadii.EMPTY,
                    Insets.EMPTY
            )));

            // Кнопка назад
            Button backButton = createIconButton(
                    "/elements/icon_back.png",
                    () -> {
                        mainPane.getChildren().clear();
                        createStartConnectionPanel();
                    }
            );

            HBox topLeftContainer = new HBox(backButton);
            topLeftContainer.setAlignment(Pos.TOP_LEFT);
            topLeftContainer.setPadding(new Insets(20, 0, 0, 20));

            Label titleLabel = new Label("Введите seed-фразу:");
            titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 36px;");

            HBox topCenterContainer = new HBox(titleLabel);
            topCenterContainer.setAlignment(Pos.CENTER);
            topCenterContainer.setPadding(new Insets(40, 0, 30, 0));

            BorderPane topContainer = new BorderPane();
            topContainer.setLeft(topLeftContainer);
            topContainer.setCenter(topCenterContainer);

            // Создаем сетку для 12 полей ввода (4 строки x 3 столбца)
            GridPane gridPane = new GridPane();
            gridPane.setAlignment(Pos.CENTER);
            gridPane.setHgap(15);
            gridPane.setVgap(15);
            gridPane.setPadding(new Insets(20));

            // Создаем 12 текстовых полей с нумерацией
            for (int i = 0; i < 12; i++) {
                int wordNumber = i + 1;

                // Создаем метку с номером слова
                Label numberLabel = new Label(wordNumber + ".");
                numberLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-family: 'Intro Regular';");
                numberLabel.setMinWidth(30);

                // Создаем поле для ввода слова
                TextField wordField = new TextField();
                wordField.setPromptText("Слово " + wordNumber);
                wordField.setStyle("-fx-font-size: 16px; -fx-font-family: 'Intro Regular'; -fx-background-radius: 5px;");
                wordField.setPrefWidth(150);

                // Сохраняем ссылку на поле в массив
                wordFields[i] = wordField;

                // Создаем контейнер для метки и поля ввода
                HBox wordContainer = new HBox(5, numberLabel, wordField);
                wordContainer.setAlignment(Pos.CENTER_LEFT);

                // Размещаем в сетке (4 строки, 3 столбца)
                int row = i / 3;
                int col = i % 3;
                gridPane.add(wordContainer, col, row);
            }

            // Кнопка подтверждения
            Button confirmButton = new Button("Подтвердить");
            confirmButton.setStyle("-fx-background-color: #4CAF50; -fx-font-family: 'Intro Regular';" +
                    " -fx-text-fill: white; -fx-font-size: 20px; -fx-background-radius: 10px; -fx-padding: 10px 20px;");
            confirmButton.setOnAction(e -> {
                try {
                    if (clientAuthService != null) {
                        List<String> words = getWordsFromFields();

                        if (words.size() != 12) {
                            dialogDisplayer.showAlert("Ошибка", "Введите все 12 слов seed-фразы");
                            return;
                        }

                        String authToken = clientAuthService.login(words);
                        dialogDisplayer.showAlert("Успех", "Авторизация прошла успешно!");

                        // Здесь можно перейти к главному интерфейсу приложения
                        // openMainInterface(authToken);

                    } else {
                        dialogDisplayer.showAlert("Ошибка", "Сервис авторизации не доступен");
                    }
                } catch (NetworkException ex) {
                    logger.warn("Сетевая ошибка при авторизации", ex);
                    dialogDisplayer.showErrorAlert("Сетевая ошибка", ex.getMessage());
                } catch (AuthException ex) {
                    logger.warn("Ошибка авторизации", ex);
                    dialogDisplayer.showErrorAlert("Ошибка авторизации", ex.getMessage());
                } catch (CryptoException ex) {
                    logger.error("Криптографическая ошибка", ex);
                    dialogDisplayer.showErrorAlert("Ошибка безопасности", "Криптографическая ошибка: " + ex.getMessage());
                } catch (Exception ex) {
                    logger.error("Неизвестная ошибка при авторизации", ex);
                    dialogDisplayer.showErrorAlert("Ошибка", "Неизвестная ошибка: " + ex.getMessage());
                }
            });

            VBox centerContainer = new VBox(20, gridPane, confirmButton);
            centerContainer.setAlignment(Pos.CENTER);
            centerContainer.setPadding(new Insets(20));

            mainContainer.setTop(topContainer);
            mainContainer.setCenter(centerContainer);

            mainPane.getChildren().clear();
            mainPane.getChildren().add(mainContainer);
        } catch (Exception e) {
            logger.error("Ошибка создания панели входа", e);
            dialogDisplayer.showErrorAlert("Ошибка", "Не удалось создать интерфейс входа: " + e.getMessage());
            createStartConnectionPanel();
        }
    }

    private List<String> getWordsFromFields() {
        List<String> words = new ArrayList<>();

        for (TextField field : wordFields) {
            if (field != null) {
                String word = field.getText().trim();
                if (!word.isEmpty()) {
                    words.add(word);
                }
            }
        }

        return words;
    }

    //=========================================

    public void createEncryptBeginPanel() {
        BorderPane mainContainer = new BorderPane();
        mainContainer.setBackground(new Background(new BackgroundFill(
                createGradient(),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));

        Label titleLabel = new Label("Шифратор");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 72px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50);

        Region lightGrayRect = new Region();
        lightGrayRect.setStyle("-fx-background-color: #494949;");
        lightGrayRect.setPrefSize(720, 540);
        lightGrayRect.setMinSize(720, 540);
        lightGrayRect.setMaxSize(720, 540);

        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(640, 480);
        darkGrayRect.setMinSize(640, 480);
        darkGrayRect.setMaxSize(640, 480);
        darkGrayRect.setTranslateX(150); // Выглядывание на 150px

        Region spaceBelowRect = new Region();
        spaceBelowRect.setStyle("-fx-background-color: transparent;");
        spaceBelowRect.setPrefHeight(50);

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

        StackPane buttonContainer = new StackPane(uploadButton);
        buttonContainer.setAlignment(Pos.CENTER);

        StackPane rectContainer = new StackPane(darkGrayRect, lightGrayRect, buttonContainer);
        rectContainer.setAlignment(Pos.CENTER);

        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        Region spaceInBottom = new Region();
        spaceInBottom.setStyle("-fx-background-color: transparent;");
        spaceInBottom.setPrefHeight(120); // 120, т.к. на следующей панеле 20+50+50 (bottomButtonsContainer)

        mainContainer.setCenter(contentBox);

        mainContainer.setTop(topContainer);

        mainContainer.setBottom(spaceInBottom);

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

        Label titleLabel = new Label("Загруженное для шифрования изображение:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50);

        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(640, 480);
        darkGrayRect.setMinSize(640, 480); // Фиксируем минимальный размер
        darkGrayRect.setMaxSize(640, 480); // Фиксируем максимальный размер
        darkGrayRect.setTranslateX(150); // Выглядывание на 50px

        Image image = new Image("file:" + imagePath);
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(720);
        imageView.setFitHeight(540);

        Button continueButton = new Button("Продолжить шифрование");
        continueButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular';" +
                " -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white;" +
                " -fx-font-size: 20px;");
        continueButton.setPrefSize(290, 50);
        continueButton.setOnAction(e -> createEncryptModePanel());

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

        Region spaceBelowRect = new Region();
        spaceBelowRect.setStyle("-fx-background-color: transparent;");
        spaceBelowRect.setPrefHeight(50); // Устанавливаем фиксированную высоту

        HBox contentContainer = new HBox(imageView);
        contentContainer.setAlignment(Pos.CENTER);

        StackPane rectContainer = new StackPane(darkGrayRect, contentContainer);
        rectContainer.setAlignment(Pos.CENTER);

        HBox bottomButtonsContainer = new HBox(20, continueButton);
        bottomButtonsContainer.setAlignment(Pos.CENTER);
        bottomButtonsContainer.setPadding(new Insets(0, 0, 50, 0));

        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        mainContainer.setCenter(contentBox);

        mainContainer.setTop(topContainer);

        mainContainer.setBottom(bottomButtonsContainer);

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

        Label titleLabel = new Label("Выберите изображение-ключ:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50); // Устанавливаем фиксированную высоту

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

        VBox buttonContainer = new VBox(20, generateButton, manualButton);
        buttonContainer.setAlignment(Pos.CENTER);

        StackPane rectContainer = new StackPane(darkGrayRect, buttonContainer);
        rectContainer.setAlignment(Pos.CENTER);

        StackPane contentContainer = new StackPane(imageView, rectContainer);
        contentContainer.setAlignment(Pos.CENTER);

        Region spaceBelowRect = new Region();
        spaceBelowRect.setStyle("-fx-background-color: transparent;");
        spaceBelowRect.setPrefHeight(50); // Устанавливаем фиксированную высоту

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

        mainContainer.setCenter(contentBox);

        mainContainer.setTop(topContainer);

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

        Label titleLabel = new Label("Ваше изображение-ключ:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(35); // Расстояние под текстом

        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(720, 540);
        darkGrayRect.setMinSize(720, 540);
        darkGrayRect.setMaxSize(720, 540);

        ImageView imageView = tempFileManager.loadInputImageFromTemp();
        if (imageView == null) {
            return;
        }

        Label loadingLabel = new Label("Картинка генерируется...");
        loadingLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        loadingLabel.setAlignment(Pos.CENTER);

        StackPane loadingContainer = new StackPane(loadingLabel);
        loadingContainer.setAlignment(Pos.CENTER);

        Button manualButton = createIconButton(
                "/elements/icon_writeParams.png",
                () -> {
                    cancelCurrentTask();
                    createManualEncryptionPanel();
                }
        );

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

        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    cancelCurrentTask();
                    mainPane.getChildren().clear();
                    createEncryptModePanel();
                }
        );

       Button swapButton = createIconButton(
                "/elements/icon_swap.png",
                this::panelForChooseAreaForEncrypt
        );

        Button regenerateButton = createIconButton(
                "/elements/icon_repeat.png",
                () -> {
                    cancelCurrentTask();
                    loadingContainer.getChildren().clear();
                    loadingContainer.getChildren().add(loadingLabel);
                    createEncryptGeneratePanel();
                }
        );

        HBox buttonContainer = new HBox(20, regenerateButton, manualButton, okayButton, swapButton);
        buttonContainer.setAlignment(Pos.CENTER);

        StackPane rectContainer = new StackPane(imageView, darkGrayRect, loadingContainer);
        rectContainer.setAlignment(Pos.CENTER);

        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

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

        VBox consoleContainer = new VBox(console);
        consoleContainer.setAlignment(Pos.CENTER);
        consoleContainer.setFillWidth(true); // Позволяем консоли растягиваться по ширине
        consoleContainer.setPrefSize(400,200);
        consoleContainer.setMinSize(400, 200);
        consoleContainer.setMaxSize(400, 200);
        consoleContainer.setStyle("-fx-background-color: #001F3D;");

        GridPane centerGrid = new GridPane();
        centerGrid.setAlignment(Pos.CENTER);
        centerGrid.add(hintBox, 0, 0); // hintBox в первой колонке
        centerGrid.add(contentBox, 1, 0); // contentBox во второй колонке
        centerGrid.setPadding(new Insets(0, 20, 0, 60)); // Отступ слева 60px

        VBox hintAndConsoleContainer = new VBox(hintBox, consoleContainer);
        hintAndConsoleContainer.setSpacing(30); // Устанавливаем расстояние между hintBox и consoleContainer
        hintAndConsoleContainer.setAlignment(Pos.CENTER);

        centerGrid.add(hintAndConsoleContainer, 0, 0); // hintAndConsoleContainer в первой колонке

        VBox.setVgrow(consoleContainer, Priority.ALWAYS);

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

        VBox bottomContainer = new VBox(buttonContainer);
        bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
        bottomContainer.setPadding(new Insets(0, 0, 30, 0)); // Уменьшаем отступ снизу

        mainContainer.setCenter(centerGrid);

        mainContainer.setTop(topContainer);

        mainContainer.setBottom(bottomContainer);

        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainContainer);

        generateImage(loadingContainer, getTempPath() + "input.png", regenerateButton,
                manualButton, okayButton, swapButton);
    }

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

        Text hintTitle = new Text("Подсказка");
        hintTitle.setStyle("-fx-font-family: 'Intro Regular'; -fx-font-weight: bold;");
        hintTitle.setFill(Color.WHITE);
        hintTitle.setFont(Font.font("Intro Regular", 18));

        Text hintText = new Text("Для шифрования части изображения - перейдите на исходную картинку и выделите желаемую часть");
        hintText.setFill(Color.WHITE);
        hintText.setFont(Font.font("Intro Regular", 14));
        hintText.setWrappingWidth(280);

        Image repeatIcon = tempFileManager.loadImageResource("/elements/icon_repeat.png");
        ImageView repeatIconView = new ImageView(repeatIcon);
        repeatIconView.setFitWidth(30);
        repeatIconView.setFitHeight(30);

        Image manualIcon = tempFileManager.loadImageResource("/elements/icon_writeParams.png");
        ImageView manualIconView = new ImageView(manualIcon);
        manualIconView.setFitWidth(30);
        manualIconView.setFitHeight(30);

        Image nextIcon = tempFileManager.loadImageResource("/elements/icon_next.png");
        ImageView nextIconView = new ImageView(nextIcon);
        nextIconView.setFitWidth(30);
        nextIconView.setFitHeight(30);

        Image swapIcon = tempFileManager.loadImageResource("/elements/icon_swap.png");
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

        StackPane hintContainer = new StackPane(hintBackground, hintContent);
        StackPane.setAlignment(hintContent, Pos.TOP_LEFT);

        VBox mainHintBox = new VBox(hintContainer);
        mainHintBox.setAlignment(Pos.CENTER);
        mainHintBox.setPrefSize(300, 230);
        mainHintBox.setMinSize(300, 230);
        mainHintBox.setMaxSize(300, 230);

        return mainHintBox;
    }

    private HBox createIconTextRow(ImageView iconView, String text) {
        Text textNode = new Text(" " + text);
        textNode.setFill(Color.WHITE);
        textNode.setFont(Font.font("Intro Regular", 14));

        HBox row = new HBox(5, iconView, textNode);
        row.setAlignment(Pos.CENTER_LEFT);

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

        Button backButton = createIconButton(
                "/elements/icon_back.png",
                () -> {
                    cancelCurrentTask();
                    clearRectangles();
                    mainPane.getChildren().clear();
                    createEncryptModePanel();
                }
        );

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

        HBox buttonContainer = new HBox(20, encryptWholeButton, encryptPartButton, resetPartButton, swapButton);
        buttonContainer.setAlignment(Pos.CENTER);

        StackPane rectContainer = new StackPane(mandelbrotImageView, imageContainer);
        rectContainer.setAlignment(Pos.CENTER);

        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        VBox hintBox = createHintBoxForEncryptPart();
        hintBox.setAlignment(Pos.CENTER);
        hintBox.setPrefSize(300, 230);
        hintBox.setMinSize(300, 230);
        hintBox.setMaxSize(300, 230);

        GridPane centerGrid = new GridPane();
        centerGrid.setAlignment(Pos.CENTER);
        centerGrid.add(hintBox, 0, 0); // hintBox в первой колонке
        centerGrid.add(contentBox, 1, 0); // contentBox во второй колонке
        centerGrid.setPadding(new Insets(0, 20, 0, 60)); // Отступ слева 60px

        ColumnConstraints hintColumn = new ColumnConstraints();
        hintColumn.setPrefWidth(300);
        hintColumn.setHgrow(Priority.NEVER);

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

        Text hintTitle = new Text("Подсказка");
        hintTitle.setStyle("-fx-font-family: 'Intro Regular'; -fx-font-weight: bold;");
        hintTitle.setFill(Color.WHITE);
        hintTitle.setFont(Font.font("Intro Regular", 18));

        Text hintText = new Text("Для шифрования части изображения\nвыделите нужную область.");
        hintText.setFill(Color.WHITE);
        hintText.setFont(Font.font("Intro Regular", 14));
        hintText.setWrappingWidth(270); // Ширина с учетом отступов

        Image encryptWholeIcon = tempFileManager.loadImageResource("/elements/icon_encryptWhole.png");
        ImageView encryptWholeIconView = new ImageView(encryptWholeIcon);
        encryptWholeIconView.setFitWidth(25); // Уменьшаем иконки
        encryptWholeIconView.setFitHeight(25);

        Image resetPartIcon = tempFileManager.loadImageResource("/elements/icon_resetPart.png");
        ImageView resetPartIconView = new ImageView(resetPartIcon);
        resetPartIconView.setFitWidth(25);
        resetPartIconView.setFitHeight(25);

        Image encryptPartIcon = tempFileManager.loadImageResource("/elements/icon_encryptPart.png");
        ImageView encryptPartIconView = new ImageView(encryptPartIcon);
        encryptPartIconView.setFitWidth(25);
        encryptPartIconView.setFitHeight(25);

        HBox encryptWholeBox = createIconTextRow(encryptWholeIconView, "зашифровать изображение полностью;");
        HBox encryptPartBox = createIconTextRow(encryptPartIconView, "зашифровать выделенную часть;");
        HBox resetPartBox = createIconTextRow(resetPartIconView, "сбросить выделение (ПКМ);");

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

        Label titleLabel = new Label("Ваше изображение-ключ:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50);

        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(720, 540);
        darkGrayRect.setMinSize(720, 540);
        darkGrayRect.setMaxSize(720, 540);

        ImageView imageView = tempFileManager.loadInputImageFromTemp();
        if (imageView == null) {
            return;
        }

        Label loadingLabel = new Label("Картинка генерируется...");
        loadingLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 24px;");
        loadingLabel.setAlignment(Pos.CENTER);

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

        HBox buttonContainer = new HBox(20, regenerateButton, manualButton, okayButton, swapButton);
        buttonContainer.setAlignment(Pos.CENTER);

        StackPane rectContainer = new StackPane(imageView, darkGrayRect, loadingContainer);
        rectContainer.setAlignment(Pos.CENTER);

        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        VBox hintBox = createHintBoxForEncrypt();
        hintBox.setAlignment(Pos.CENTER);
        hintBox.setPrefSize(300, 230);
        hintBox.setMinSize(300, 230);
        hintBox.setMaxSize(300, 230);

        GridPane centerGrid = new GridPane();
        centerGrid.setAlignment(Pos.CENTER); // Выравниваем по центру
        centerGrid.add(hintBox, 0, 0); // hintBox в первой колонке
        centerGrid.add(contentBox, 1, 0); // contentBox во второй колонке
        centerGrid.setPadding(new Insets(0, 20, 0, 60)); // Отступ слева 60px

        ColumnConstraints hintColumn = new ColumnConstraints();
        hintColumn.setPrefWidth(300);
        hintColumn.setHgrow(Priority.NEVER);

        ColumnConstraints contentColumn = new ColumnConstraints();
        contentColumn.setHgrow(Priority.ALWAYS); // contentBox занимает всё доступное пространство

        centerGrid.getColumnConstraints().addAll(hintColumn, contentColumn);

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

        VBox bottomContainer = new VBox(buttonContainer);
        bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
        bottomContainer.setPadding(new Insets(0, 0, 30, 0)); // Уменьшаем отступ снизу

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

        Label titleLabel = new Label("Полученное зашифрованное изображение:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50); // Расстояние под текстом

        ImageEncrypt imageEncrypt = new ImageEncrypt();
        imageEncrypt.encryptWholeImage(image);

        BufferedImage encryptedImage = imageEncrypt.getEncryptedImage();

        ImageView imageView = new ImageView(SwingFXUtils.toFXImage(encryptedImage, null));
        imageView.setFitWidth(640);
        imageView.setFitHeight(450);

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

        HBox buttonContainer = new HBox(20, saveButton);
        buttonContainer.setAlignment(Pos.CENTER);

        StackPane rectContainer = new StackPane(imageContainer);
        rectContainer.setAlignment(Pos.CENTER);

        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

        // Создание контейнера для кнопок под прямоугольником
        VBox bottomContainer = new VBox(buttonContainer);
        bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
        bottomContainer.setPadding(new Insets(0, 0, 30, 0)); // Уменьшаем отступ снизу

        // Установка контейнера внизу
        mainContainer.setBottom(bottomContainer);

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

        Label titleLabel = new Label("Полученное зашифрованное изображение:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50);

        ImageView imageView = new ImageView(SwingFXUtils.toFXImage(image, null));
        imageView.setFitWidth(640);
        imageView.setFitHeight(450);

        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setAlignment(Pos.CENTER);

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

        HBox buttonContainer = new HBox(20, saveButton);
        buttonContainer.setAlignment(Pos.CENTER);

        StackPane rectContainer = new StackPane(imageContainer);
        rectContainer.setAlignment(Pos.CENTER);

        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

        // Создание контейнера для кнопок под прямоугольником
        VBox bottomContainer = new VBox(buttonContainer);
        bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
        bottomContainer.setPadding(new Insets(0, 0, 30, 0)); // Уменьшаем отступ снизу

        // Установка контейнера внизу
        mainContainer.setBottom(bottomContainer);

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

        HBox gridPaneContainer = new HBox(manualEncryptPanel);
        gridPaneContainer.setAlignment(Pos.CENTER);

        VBox vbox = new VBox(gridPaneContainer);
        vbox.setAlignment(Pos.CENTER);

        mainContainer.setCenter(vbox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

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

        Label titleLabel = new Label("Ваше изображение-ключ:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");

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

        HBox buttonContainer = new HBox(20, regenerateButton, manualButton, okayButton, swapButton);
        buttonContainer.setAlignment(Pos.CENTER);

        StackPane imageContainer = new StackPane(imageView, imageViewMandelbrot);
        imageContainer.setAlignment(Pos.CENTER);

        VBox contentBox = new VBox(10, spaceBelowTitle, imageContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        // Создание блока-подсказки
        VBox hintBox = createHintBoxForEncrypt();
        hintBox.setAlignment(Pos.CENTER); // Выравниваем hintBox по вертикали
        hintBox.setPrefSize(300, 230);
        hintBox.setMinSize(300, 230);
        hintBox.setMaxSize(300, 230);

        GridPane centerGrid = new GridPane();
        centerGrid.setAlignment(Pos.CENTER); // Выравниваем по центру
        centerGrid.add(hintBox, 0, 0); // hintBox в первой колонке
        centerGrid.add(contentBox, 1, 0); // contentBox во второй колонке
        centerGrid.setPadding(new Insets(0, 20, 0, 60)); // Отступ слева 60px

        ColumnConstraints hintColumn = new ColumnConstraints();
        hintColumn.setPrefWidth(300); // Фиксированная ширина для hintBox
        hintColumn.setHgrow(Priority.NEVER); // hintBox не растягивается

        ColumnConstraints contentColumn = new ColumnConstraints();
        contentColumn.setHgrow(Priority.ALWAYS); // contentBox занимает всё доступное пространство

        centerGrid.getColumnConstraints().addAll(hintColumn, contentColumn);

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

        VBox bottomContainer = new VBox(buttonContainer);
        bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
        bottomContainer.setPadding(new Insets(0, 0, 30, 0)); // Уменьшаем отступ снизу

        mainContainer.setBottom(bottomContainer);

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

        Label titleLabel = new Label("Дешифратор");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 64px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50);

        Region lightGrayRect = new Region();
        lightGrayRect.setStyle("-fx-background-color: #494949;");
        lightGrayRect.setPrefSize(720, 540);
        lightGrayRect.setMinSize(720, 540);
        lightGrayRect.setMaxSize(720, 540);

        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(640, 480);
        darkGrayRect.setMinSize(640, 480);
        darkGrayRect.setMaxSize(640, 480);
        darkGrayRect.setTranslateX(150); // Выглядывание на 150px

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

        StackPane buttonContainer = new StackPane(uploadButton);
        buttonContainer.setAlignment(Pos.CENTER);

        StackPane rectContainer = new StackPane(darkGrayRect, lightGrayRect, buttonContainer);
        rectContainer.setAlignment(Pos.CENTER);

        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        mainContainer.setCenter(contentBox);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол и установка label по центру вверху
        mainContainer.setTop(topContainer);

        mainContainer.setBottom(spaceInBottom);

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

        Label titleLabel = new Label("Загруженное для расшифровки изображение:");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 48px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50);

        Region darkGrayRect = new Region();
        darkGrayRect.setStyle("-fx-background-color: #373737;");
        darkGrayRect.setPrefSize(640, 480);
        darkGrayRect.setMinSize(640, 480);
        darkGrayRect.setMaxSize(640, 480);
        darkGrayRect.setTranslateX(150); // Выглядывание на 150px

        Image image = new Image("file:" + imagePath);
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(720);
        imageView.setFitHeight(540);

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

        HBox contentContainer = new HBox(imageView);
        contentContainer.setAlignment(Pos.CENTER);

        StackPane rectContainer = new StackPane(darkGrayRect, contentContainer);
        rectContainer.setAlignment(Pos.CENTER);

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

        mainContainer.setBottom(bottomButtonsContainer);

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

        Label titleLabel = new Label("Дешифратор");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 64px;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        Region spaceBelowTitle = new Region();
        spaceBelowTitle.setStyle("-fx-background-color: transparent;");
        spaceBelowTitle.setPrefHeight(50);

        // Загрузка изображения input.png
        Image inputImage = new Image("file:" + getTempPath() + "input.png");
        ImageView inputImageView = new ImageView(inputImage);
        inputImageView.setFitWidth(640);
        inputImageView.setFitHeight(480);
        inputImageView.setTranslateX(150); // Сдвигаем прямоугольник вправо на 150px

        Region lightGrayRect = new Region();
        lightGrayRect.setStyle("-fx-background-color: #494949;");
        lightGrayRect.setPrefSize(720, 540); // Уменьшаем размер прямоугольника
        lightGrayRect.setMinSize(720, 540);
        lightGrayRect.setMaxSize(720, 540);

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

        StackPane buttonContainer = new StackPane(manualButton);
        buttonContainer.setAlignment(Pos.CENTER);

        StackPane rectContainer = new StackPane(lightGrayRect, buttonContainer);
        rectContainer.setAlignment(Pos.CENTER);

        // Размещение изображения input.png под прямоугольником
        StackPane imageContainer = new StackPane(inputImageView, rectContainer);
        imageContainer.setAlignment(Pos.CENTER);

        Region spaceInBottom = new Region();
        spaceInBottom.setStyle("-fx-background-color: transparent;");
        spaceInBottom.setPrefHeight(120); // 120, т.к. на следующей панеле 20+50+50 (bottomButtonsContainer)

        VBox contentBox = new VBox(10, spaceBelowTitle, imageContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

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

        VBox contentBox = new VBox(10, spaceBelowTitle, rectContainer);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(20));

        mainContainer.setCenter(contentBox);

        mainContainer.setBottom(saveButtonContainer);

        // Установка контейнера с кнопкой "Вернуться назад" в верхний левый угол
        mainContainer.setTop(topContainer);

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

    public static void setConsole(TextArea console) {
        JavaFXImpl.console = console;
    }

    public static void logToConsole(String message) {
        if (console != null) {
            console.appendText(message + "\n");
        }
    }

    private Button createIconButton(String iconPath, Runnable onClickAction) {
        Image icon = tempFileManager.loadImageResource(iconPath);
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