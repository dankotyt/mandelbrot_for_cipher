package View;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Класс ServerInterface представляет собой графический интерфейс для работы с сервером,
 * диспетчеризующим передачу зашифрованных изображений и ключей для их расшифровки
 * между различными клиентами на различных IP-адресах и портах.
 *
 * @author Илья
 * @version 0.2
 */
public class ServerInterface extends Application {

    private static final int NORTH_COL = 0x011324;
    private static final int SOUTH_COL = 0x011A30;

    private Pane mainPane;
    private TextArea consoleTextArea;
    private Stage primaryStage;

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

    /**
     * Создает экран загрузки.
     */
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

    /**
     * Создает стартовую панель.
     */
    private void createStartPanel() {
        BorderPane startPanel = new BorderPane();
        startPanel.setBackground(new Background(new BackgroundFill(
                createGradient(), CornerRadii.EMPTY, Insets.EMPTY
        )));

        Label titleLabel = new Label("Запустить сервер");
        titleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 64px;");
        titleLabel.setAlignment(Pos.CENTER);

        Button startServerButton = new Button("Запустить сервер");
        startServerButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular'; -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white; -fx-font-size: 28px;");
        startServerButton.setPrefSize(327, 58);
        startServerButton.setAlignment(Pos.CENTER);
        startServerButton.setOnAction(e -> createListeningPortPanel());

        VBox contentBox = new VBox(20, titleLabel, startServerButton);
        contentBox.setAlignment(Pos.CENTER); // Выравниваем по центру
        contentBox.setPadding(new Insets(20));

        startPanel.setCenter(contentBox);

        mainPane.getChildren().clear(); // Очищаем mainPane
        mainPane.getChildren().add(startPanel); // Добавляем новый контент
    }

    /**
     * Создает панель для ввода порта сервера.
     */
    private void createListeningPortPanel() {
        BorderPane listeningPortPanel = new BorderPane();
        listeningPortPanel.setBackground(new Background(new BackgroundFill(
                createGradient(), CornerRadii.EMPTY, Insets.EMPTY
        )));

        Label portLabel = new Label("Введите порт сервера:");
        portLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 32px;");

        TextField portField = new TextField();
        portField.setPromptText("Порт");
        portField.setPrefSize(320, 42);
        portField.setAlignment(Pos.CENTER);
        portField.setMaxWidth(320);
        portField.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: black; -fx-font-size: 22px;");

        Button startServerButton = new Button("Запустить сервер");
        startServerButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular'; -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white; -fx-font-size: 28px;");
        startServerButton.setPrefSize(327, 58);
        startServerButton.setOnAction(e -> {
            try {
                int port = Integer.parseInt(portField.getText());
                if (port <= 0 || port > 65535) {
                    throw new IllegalArgumentException("Некорректный порт.");
                }
                // Запуск сервера
                System.out.println(getTimestamp() + "Сервер запущен на порту " + port);
                createServerConsolePanel();
            } catch (NumberFormatException ex) {
                showAlert("Ошибка", "Некорректный формат порта.");
            } catch (IllegalArgumentException ex) {
                showAlert("Ошибка", ex.getMessage());
            }
        });

        VBox contentBox = new VBox(20, portLabel, portField, startServerButton);
        contentBox.setAlignment(Pos.CENTER); // Выравниваем по центру
        contentBox.setPadding(new Insets(20));

        listeningPortPanel.setCenter(contentBox);

        mainPane.getChildren().clear();
        mainPane.getChildren().add(listeningPortPanel);
    }

    /**
     * Создает панель для отображения журнала действий сервера.
     */
    private void createServerConsolePanel() {
        BorderPane serverConsolePanel = new BorderPane();
        serverConsolePanel.setBackground(new Background(new BackgroundFill(
                createGradient(), CornerRadii.EMPTY, Insets.EMPTY
        )));

        Label consoleLabel = new Label("Журнал действий сервера:");
        consoleLabel.setStyle("-fx-font-family: 'Intro Regular'; -fx-text-fill: white; -fx-font-size: 32px;");

        consoleTextArea = new TextArea();
        consoleTextArea.setEditable(false);
        consoleTextArea.setPrefSize(800, 400);
        consoleTextArea.setMaxSize(800, 400);
        consoleTextArea.setMinSize(800, 400);
        consoleTextArea.setStyle("-fx-text-fill: #56AAFF; -fx-font-family: 'Intro Regular'; -fx-font-size: 18px; -fx-control-inner-background: #001F3D; -fx-background-color: #001F3D;");
        Platform.runLater(() -> {
            // Настройка стилей для вертикального скроллбара
            consoleTextArea.lookup(".scroll-bar:vertical").setStyle(
                    "-fx-background-color: #001F3D; " + // Цвет фона ползунка
                            "-fx-pref-width: 10px; " +         // Ширина ползунка
                            "-fx-background-radius: 5px;"      // Закругленные углы
            );

            // Настройка стилей для трека вертикального скроллбара
            consoleTextArea.lookup(".scroll-bar:vertical .track").setStyle(
                    "-fx-background-color: #001F3D; " + // Цвет фона трека
                            "-fx-border-color: transparent;"    // Убираем границу
            );

            // Настройка стилей для ползунка вертикального скроллбара
            consoleTextArea.lookup(".scroll-bar:vertical .thumb").setStyle(
                    "-fx-background-color: #004488; " + // Цвет ползунка
                            "-fx-background-radius: 5px;"       // Закругленные углы
            );

            // Настройка стилей для горизонтального скроллбара
            consoleTextArea.lookup(".scroll-bar:horizontal").setStyle(
                    "-fx-background-color: #001F3D; " + // Цвет фона ползунка
                            "-fx-pref-height: 10px; " +        // Высота ползунка
                            "-fx-background-radius: 5px;"      // Закругленные углы
            );

            // Настройка стилей для трека горизонтального скроллбара
            consoleTextArea.lookup(".scroll-bar:horizontal .track").setStyle(
                    "-fx-background-color: #001F3D; " + // Цвет фона трека
                            "-fx-border-color: transparent;"    // Убираем границу
            );

            // Настройка стилей для ползунка горизонтального скроллбара
            consoleTextArea.lookup(".scroll-bar:horizontal .thumb").setStyle(
                    "-fx-background-color: #004488; " + // Цвет ползунка
                            "-fx-background-radius: 5px;"       // Закругленные углы
            );

            // Настройка стилей для указателя (arrow buttons)
            consoleTextArea.lookup(".scroll-bar .increment-button, .scroll-bar .decrement-button").setStyle(
                    "-fx-background-color: transparent; " + // Прозрачные кнопки
                            "-fx-border-color: transparent;"
            );

            // Настройка стилей для стрелок (arrows)
            consoleTextArea.lookup(".scroll-bar .increment-arrow, .scroll-bar .decrement-arrow").setStyle(
                    "-fx-background-color: transparent;" // Скрываем стрелки
            );
        });

        Button stopServerButton = new Button("Завершить работу сервера");
        stopServerButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular'; -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white; -fx-font-size: 28px;");
        stopServerButton.setPrefSize(257, 68);
        stopServerButton.setOnAction(e -> {
            // Логика завершения работы сервера
            System.out.println(getTimestamp() + "Сервер остановлен.");
        });

        Button backButton = new Button("Вернуться назад");
        backButton.setStyle("-fx-background-color: transparent; -fx-font-family: 'Intro Regular'; -fx-border-color: white; -fx-border-width: 5px; -fx-border-radius: 10px; -fx-text-fill: white; -fx-font-size: 28px;");
        backButton.setPrefSize(257, 68);
        backButton.setOnAction(e -> createStartPanel());

        HBox buttonBox = new HBox(20, stopServerButton, backButton);
        buttonBox.setAlignment(Pos.CENTER); // Выравниваем по центру
        buttonBox.setPadding(new Insets(20));

        VBox contentBox = new VBox(20, consoleLabel, consoleTextArea, buttonBox);
        contentBox.setAlignment(Pos.CENTER); // Выравниваем по центру
        contentBox.setPadding(new Insets(20));

        serverConsolePanel.setCenter(contentBox);

        mainPane.getChildren().clear();
        mainPane.getChildren().add(serverConsolePanel);

        // Перенаправление System.out в TextArea
        System.setOut(new ConsolePrintStream(consoleTextArea));
    }

    /**
     * Создает градиентный фон.
     */
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

    /**
     * Отображает диалоговое окно с сообщением.
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Генерирует временный отпечаток в формате "yyyy-MM-dd HH:mm:ss".
     */
    private String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return "[" + sdf.format(new Date()) + "] ";
    }

    /**
     * Перенаправляет вывод System.out в TextArea.
     */
    private static class ConsolePrintStream extends java.io.PrintStream {
        private final TextArea textArea;

        public ConsolePrintStream(TextArea textArea) {
            super(System.out);
            this.textArea = textArea;
        }

        @Override
        public void println(String x) {
            super.println(x);
            Platform.runLater(() -> textArea.appendText(x + "\n")); // Обновление UI через Platform.runLater
        }
    }
}