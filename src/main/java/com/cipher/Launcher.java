package com.cipher;

import com.cipher.client.handler.ClientGlobalExceptionHandler;
import com.cipher.core.config.AppConfig;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import io.github.cdimascio.dotenv.Dotenv;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.InputStream;

/**
 * Главный класс приложения, отвечающий за запуск Spring Boot и JavaFX.
 * Инициализирует глобальный обработчик исключений, запускает JavaFX приложение
 * и контекст Spring в отдельных потоках.
 */
@SpringBootApplication
@EnableFeignClients
@RequiredArgsConstructor
public class Launcher implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(Launcher.class);
    private final SceneManager sceneManager;
    private final DialogDisplayer dialogDisplayer;
    private final ClientGlobalExceptionHandler clientGlobalExceptionHandler;

    /**
     * Точка входа в приложение.
     * Инициализирует глобальный обработчик исключений, запускает JavaFX приложение
     * и контекст Spring Boot.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        // 1. Загружаем .env переменные
        Dotenv dotenv = Dotenv.configure().load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        SpringApplication.run(Launcher.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Spring context initialized, starting JavaFX...");

        // Запускаем JavaFX в правильном потоке
        Platform.startup(() -> {
            try {
                // Создаем Stage в FX потоке
                Stage primaryStage = createPrimaryStage();

                // Устанавливаем Stage в менеджеры
                sceneManager.setPrimaryStage(primaryStage);
                dialogDisplayer.setPrimaryStage(primaryStage);

                // Показываем начальный экран
                sceneManager.showLoadingPanel();
                primaryStage.show();

                logger.info("JavaFX application started successfully");

            } catch (Exception e) {
                logger.error("Failed to start JavaFX", e);
                clientGlobalExceptionHandler.handleException(e);
            }
        });
    }

    private Stage createPrimaryStage() {
        Stage stage = new Stage();
        stage.setTitle("Cipher Application");
        stage.setFullScreen(true);
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);

        // Загрузка иконки
        try {
            InputStream iconStream = getClass().getResourceAsStream("/elements/icon.png");
            if (iconStream != null) {
                Image icon = new Image(iconStream);
                stage.getIcons().add(icon);
            }
        } catch (Exception e) {
            logger.warn("Failed to load application icon", e);
        }

        // Обработчик закрытия окна
        stage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });

        return stage;
    }
}