package com.cipher.core.utils;

import com.cipher.core.controller.encrypt.EncryptLoadController;
import com.cipher.core.controller.online.SeedGenerationController;
import com.cipher.core.factory.ControllerFactory;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SceneManager {
    private static final Logger logger = LoggerFactory.getLogger(SceneManager.class);

    private final Stage primaryStage;
    private final ControllerFactory controllerFactory;
    private final DialogDisplayer dialogDisplayer;

    public void showScreen(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(controllerFactory::createController);
            Parent root = loader.load();
            primaryStage.getScene().setRoot(root);
        } catch (Exception e) {
            logger.error("Error loading screen: {}", fxmlPath, e);
        }
    }

    public void showStartPanel() {
        showScreen("/fxml/start/start.fxml");
    }

    public void showConnectionPanel() {
        showScreen("/fxml/online/connection.fxml");
    }

    public void showSeedGenerationPanel(String seedPhrase) {
        showScreen("/fxml/online/seed-generation.fxml");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/online/seed-generation.fxml"));
            loader.setControllerFactory(controllerFactory::createController);
            Parent root = loader.load();

            SeedGenerationController controller = loader.getController();
            controller.setSeedPhrase(seedPhrase);

            primaryStage.getScene().setRoot(root);
        } catch (Exception e) {
            logger.error("Error loading seed generation screen", e);
        }
    }

    public void showLoginPanel() {
        showScreen("/fxml/online/login.fxml");
    }

    public void showAccountPanel() {
        showScreen("/fxml/online/account.fxml");
    }

    public void showEncryptBeginPanel() {
        showScreen("/fxml/encrypt-begin.fxml");
    }

    public void showEncryptLoadPanel(String imagePath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/encrypt-load.fxml"));
            loader.setControllerFactory(controllerFactory::createController);
            Parent root = loader.load();

            EncryptLoadController controller = loader.getController();
            controller.setImagePath(imagePath);

            primaryStage.getScene().setRoot(root);
        } catch (Exception e) {
            logger.error("Error loading encrypt load screen", e);
            dialogDisplayer.showErrorAlert("Ошибка", "Не удалось загрузить интерфейс: " + e.getMessage());
            showEncryptBeginPanel();
        }
    }

    public void showEncryptModePanel() {
        showScreen("/fxml/encrypt-mode.fxml");
    }

    public void showEncryptGeneratePanel() {
        showScreen("/fxml/encrypt-generate.fxml");
    }

    public void showManualEncryptionPanel() {
        showScreen("/fxml/encrypt-manual.fxml");
    }
}
