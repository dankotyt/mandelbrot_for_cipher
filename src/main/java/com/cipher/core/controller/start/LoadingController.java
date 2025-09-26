package com.cipher.core.controller.start;

import com.cipher.core.utils.SceneManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.concurrent.ExecutorService;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class LoadingController implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(LoadingController.class);

    @FXML private ProgressBar progressBar;

    private final ExecutorService executorService;
    private final SceneManager sceneManager;

    @FXML
    public void initialize() {
        logger.info("LoadingController initialized");

        // Проверяем, что progressBar был инжектирован
        if (progressBar == null) {
            logger.error("ProgressBar is null! FXML injection failed.");
            // Показываем ошибку и переходим к стартовому экрану
            Platform.runLater(sceneManager::showStartPanel);
            return;
        }

        logger.info("ProgressBar: {}", progressBar);
        startLoadingTask();
    }

    @Override
    public void run(String... args) {
        sceneManager.showLoadingPanel();
    }

    private void startLoadingTask() {
        Task<Void> loadingTask = new Task<>() {
            @Override
            protected Void call() {
                for (int i = 0; i <= 100; i++) {
                    if (isCancelled()) break;
                    updateProgress(i, 100);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        if (isCancelled()) break;
                        Thread.currentThread().interrupt();
                    }
                }
                return null;
            }
        };

        // Добавляем проверку
        if (progressBar != null) {
            progressBar.progressProperty().bind(loadingTask.progressProperty());
        }

        loadingTask.setOnSucceeded(event -> {
            Platform.runLater(sceneManager::showStartPanel);
        });

        loadingTask.setOnFailed(event -> {
            logger.error("Loading task failed", loadingTask.getException());
            Platform.runLater(sceneManager::showStartPanel);
        });

        executorService.execute(loadingTask);
    }
}
