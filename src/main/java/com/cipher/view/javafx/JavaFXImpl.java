//package com.cipher.view.javafx;
//
//import com.cipher.Launcher;
//import com.cipher.core.utils.SceneManager;
//import com.cipher.core.utils.TempFileManager;
//import javafx.application.Application;
//import javafx.application.Platform;
//import javafx.geometry.Pos;
//import javafx.scene.Scene;
//import javafx.scene.control.Alert;
//import javafx.scene.control.Label;
//import javafx.scene.image.Image;
//import javafx.scene.input.KeyCombination;
//import javafx.scene.layout.StackPane;
//import javafx.scene.layout.VBox;
//import javafx.stage.Stage;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.boot.SpringApplication;
//import org.springframework.context.ApplicationContext;
//import org.springframework.context.ConfigurableApplicationContext;
//
//import java.io.InputStream;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//
//public class JavaFXImpl extends Application {
//    private static final Logger logger = LoggerFactory.getLogger(JavaFXImpl.class);
//
//    public static void startJavaFX(String[] args) {
//        // Запускаем JavaFX Application
//        new Thread(() -> Application.launch(JavaFXImpl.class, args)).start();
//    }
//
//    @Override
//    public void start(Stage primaryStage) {
//        try {
//            // 1. Получаем Spring контекст из Launcher
//            ConfigurableApplicationContext context = Launcher.getContext();
//
//            if (context == null) {
//                throw new IllegalStateException("Spring context not initialized");
//            }
//
//            // 2. Регистрируем stage в Spring
//            context.getBeanFactory().registerSingleton("primaryStage", primaryStage);
//
//            // 3. Настраиваем stage
//            setupPrimaryStage(primaryStage);
//
//            // 4. Получаем SceneManager и показываем LoadingController
//            SceneManager sceneManager = context.getBean(SceneManager.class);
//            sceneManager.setPrimaryStage(primaryStage);
//            sceneManager.showLoadingPanel();
//
//        } catch (Exception e) {
//            logger.error("Failed to start application", e);
//            showError(primaryStage, "Ошибка запуска: " + e.getMessage());
//        }
//    }
//
//    private void setupPrimaryStage(Stage primaryStage) {
//        primaryStage.setFullScreen(true);
//        primaryStage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
//
//        try {
//            InputStream iconStream = getClass().getResourceAsStream("/elements/icon.png");
//            if (iconStream != null) {
//                Image icon = new Image(iconStream);
//                primaryStage.getIcons().add(icon);
//            }
//        } catch (Exception e) {
//            logger.warn("Failed to load icon", e);
//        }
//
//        primaryStage.setOnCloseRequest(event -> {
//            event.consume();
//            shutdownApplication();
//        });
//    }
//
//    private void showError(Stage primaryStage, String message) {
//        VBox errorBox = new VBox(20);
//        errorBox.setAlignment(Pos.CENTER);
//        errorBox.setStyle("-fx-background-color: #011324; -fx-padding: 40;");
//
//        Label errorLabel = new Label(message);
//        errorLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16;");
//        errorLabel.setWrapText(true);
//
//        errorBox.getChildren().add(errorLabel);
//
//        primaryStage.setScene(new Scene(errorBox, 600, 400));
//        primaryStage.show();
//    }
//
//    @Override
//    public void stop() {
//        Platform.exit();
//    }
//
//    private void shutdownApplication() {
//        // Останавливаем Spring контекст перед выходом
//        Launcher.shutdownContext();
//        Platform.exit();
//        System.exit(0);
//    }
//}