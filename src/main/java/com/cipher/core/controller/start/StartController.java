package com.cipher.core.controller.start;

import com.cipher.client.utils.NetworkUtils;
import com.cipher.common.exception.NetworkException;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import com.cipher.client.service.impl.SeedServiceImpl;
import com.cipher.core.utils.TempFileManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Getter
@RequiredArgsConstructor
public class StartController {
    private static final Logger logger = LoggerFactory.getLogger(StartController.class);

    @FXML private Button encryptButton;
    @FXML private Button decryptButton;
    @FXML private Button connectButton;
    @FXML private Button exitButton;

    private final SceneManager sceneManager;
    private final ExecutorService executorService;
    private final SeedServiceImpl seedService;
    private final NetworkUtils networkUtils;
    private final DialogDisplayer dialogDisplayer;
    private final TempFileManager tempFileManager;

    @FXML
    public void initialize() {
        setupButtonActions();
    }

    private void setupButtonActions() {
        encryptButton.setOnAction(e -> sceneManager.showEncryptBeginPanel());
        decryptButton.setOnAction(e -> sceneManager.showDecryptPanel());
        connectButton.setOnAction(e -> handleConnect());
        exitButton.setOnAction(e -> shutdownApplication());
    }

    private void handleConnect() {
        try {
            networkUtils.checkNetworkConnection();
            sceneManager.showConnectionPanel();
        } catch (NetworkException ex) {
            dialogDisplayer.showErrorAlert("Нет подключения",
                    """
                            Необходимо интернет-соединение для выхода в сеть
                            
                            Пожалуйста, проверьте подключение и попробуйте снова""");
        } catch (Exception ex) {
            logger.error("Ошибка при входе", ex);
            dialogDisplayer.showErrorAlert("Ошибка", "Не удалось открыть форму входа");
        }
    }

    private void shutdownApplication() {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            Platform.exit();
            System.exit(0);
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
