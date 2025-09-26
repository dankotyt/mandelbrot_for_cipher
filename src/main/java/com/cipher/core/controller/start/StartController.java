package com.cipher.core.controller.start;

import com.cipher.client.utils.NetworkUtils;
import com.cipher.common.exception.NetworkException;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class StartController {
    private static final Logger logger = LoggerFactory.getLogger(StartController.class);

    @FXML private Button encryptButton;
    @FXML private Button decryptButton;
    @FXML private Button connectButton;
    @FXML private Button exitButton;

    private final SceneManager sceneManager;
    private final DialogDisplayer dialogDisplayer;

    private ExecutorService executorService;

    @FXML
    public void initialize() {
        executorService = Executors.newCachedThreadPool();
        setupButtonActions();
    }

    private void setupButtonActions() {
        encryptButton.setOnAction(e -> sceneManager.showEncryptBeginPanel());
        decryptButton.setOnAction(e -> sceneManager.showDecryptBeginPanel());
        connectButton.setOnAction(e -> handleConnect());
        exitButton.setOnAction(e -> shutdownApplication());
    }

    private void handleConnect() {
        try {
            NetworkUtils.checkNetworkConnection();
            sceneManager.showConnectionPanel();
        } catch (NetworkException ex) {
            Platform.runLater(() ->
                    dialogDisplayer.showErrorAlert("Нет подключения",
                            """
                            Необходимо интернет-соединение для выхода в сеть
                            
                            Пожалуйста, проверьте подключение и попробуйте снова""")
            );
        } catch (Exception ex) {
            logger.error("Ошибка при входе", ex);
            Platform.runLater(() ->
                    dialogDisplayer.showErrorAlert("Ошибка", "Не удалось открыть форму входа")
            );
        }
    }

    private void shutdownApplication() {
        try {
            if (executorService != null) {
                executorService.shutdown();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            }
            Platform.exit();
            System.exit(0);
        } catch (InterruptedException e) {
            if (executorService != null) {
                executorService.shutdownNow();
            }
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void cleanup() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
