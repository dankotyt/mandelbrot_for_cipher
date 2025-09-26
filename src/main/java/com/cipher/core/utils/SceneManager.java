package com.cipher.core.utils;

import com.cipher.core.controller.decrypt.DecryptFinalController;
import com.cipher.core.controller.decrypt.DecryptLoadController;
import com.cipher.core.controller.encrypt.*;
import com.cipher.core.dto.neww.EncryptionPreviewResult;
import com.cipher.core.dto.neww.SegmentationParams;
import com.cipher.core.dto.neww.SegmentationResult;
import com.cipher.core.factory.ControllerFactory;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

@Component
@RequiredArgsConstructor
public class SceneManager {
    private static final Logger logger = LoggerFactory.getLogger(SceneManager.class);

    @Setter
    @Getter
    private Stage primaryStage;
    private final ControllerFactory controllerFactory;
    private final DialogDisplayer dialogDisplayer;

    public void showScreen(String fxmlPath) {
        // Все операции с JavaFX должны выполняться в FX Application Thread
        if (Platform.isFxApplicationThread()) {
            loadScreen(fxmlPath);
        } else {
            Platform.runLater(() -> loadScreen(fxmlPath));
        }
    }

    private void loadScreen(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(controllerFactory);

            Parent root = loader.load();

            if (primaryStage.getScene() == null) {
                primaryStage.setScene(new Scene(root));
            } else {
                primaryStage.getScene().setRoot(root);
            }

        } catch (Exception e) {
            logger.error("Error loading screen: {}", fxmlPath, e);
            throw new RuntimeException("Failed to load screen: " + fxmlPath, e);
        }
    }
    public void showLoadingPanel() {
        showScreen("/fxml/start/loading.fxml");
    }

    public void showStartPanel() {
        showScreen("/fxml/start/start.fxml");
    }

    public void showConnectionPanel() {
        showScreen("/fxml/online/connection.fxml");
    }

    public void showSeedGenerationPanel() { showScreen("/fxml/online/seed-generation.fxml");}

    public void showLoginPanel() {
        showScreen("/fxml/online/login.fxml");
    }

    public void showAccountPanel() {
        showScreen("/fxml/online/account.fxml");
    }

    public void showEncryptBeginPanel() {
        showScreen("/fxml/encrypt/encrypt-begin.fxml");
    }

    public void showEncryptLoadPanel() {
        showScreen("/fxml/encrypt/encrypt-load.fxml");
    }

    public void showEncryptModePanel() {
        showScreen("/fxml/encrypt/encrypt-mode.fxml");
    }

    public void showEncryptGeneratePanel() {
        showScreen("/fxml/encrypt/encrypt-generate.fxml");
    }

    public void showManualEncryptionPanel() {
        showScreen("/fxml/encrypt/encrypt-manual.fxml");
    }

    public void showEncryptChooseAreaPanel() {
        showScreen("/fxml/encrypt/encrypt-choose-area.fxml");
    }

    public void showChoosenMandelbrotPanel() {
        showScreen("/fxml/encrypt/encrypt-choose-area.fxml");
    }

    public void showEncryptGenerateParamsPanel(String paramsFilePath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/encrypt/encrypt-generate-params.fxml"));
            loader.setControllerFactory(controllerFactory);
            Parent root = loader.load();

            EncryptGenerateParamsController controller = loader.getController();
            controller.setParamsFilePath(paramsFilePath);

            primaryStage.getScene().setRoot(root);
        } catch (Exception e) {
            logger.error("Error loading encrypt generate params screen", e);
            dialogDisplayer.showErrorAlert("Ошибка", "Не удалось загрузить интерфейс: " + e.getMessage());
            showEncryptModePanel();
        }
    }

    public void showEncryptFinalPanel(BufferedImage encryptedImage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/encrypt/encrypt-final.fxml"));
            loader.setControllerFactory(controllerFactory);
            Parent root = loader.load();

            EncryptFinalController controller = loader.getController();
            controller.setEncryptedImage(encryptedImage);

            primaryStage.getScene().setRoot(root);
        } catch (Exception e) {
            logger.error("Error loading encrypt final screen", e);
            dialogDisplayer.showErrorAlert("Ошибка", "Не удалось загрузить интерфейс: " + e.getMessage());
        }
    }

//    public void showEncryptFinalPanel(EncryptionPreviewResult previewResult) {
//        try {
//            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/encrypt/encrypt-final.fxml"));
//            loader.setControllerFactory(controllerFactory);
//            Parent root = loader.load();
//
//            EncryptFinalController controller = loader.getController();
//            controller.setPreviewResult(previewResult);
//
//            primaryStage.getScene().setRoot(root);
//        } catch (Exception e) {
//            logger.error("Error loading encrypt final screen", e);
//            dialogDisplayer.showErrorAlert("Ошибка", "Не удалось загрузить интерфейс: " + e.getMessage());
//        }
//    }

    public void showEncryptFinalSelectedPanel(BufferedImage encryptedImage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/encrypt/encrypt-final-selected.fxml"));
            loader.setControllerFactory(controllerFactory);
            Parent root = loader.load();

            EncryptFinalSelectedController controller = loader.getController();
            controller.setEncryptedImage(encryptedImage);

            primaryStage.getScene().setRoot(root);
        } catch (Exception e) {
            logger.error("Error loading encrypt final selected screen", e);
            dialogDisplayer.showErrorAlert("Ошибка", "Не удалось загрузить интерфейс: " + e.getMessage());
        }
    }

    public void showDecryptBeginPanel() {
        showScreen("/fxml/decrypt/decrypt-begin.fxml");
    }

    public void showDecryptLoadPanel(String imagePath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/decrypt/decrypt-load.fxml"));
            loader.setControllerFactory(controllerFactory);
            Parent root = loader.load();

            DecryptLoadController controller = loader.getController();
            controller.setImagePath(imagePath);

            primaryStage.getScene().setRoot(root);
        } catch (Exception e) {
            logger.error("Error loading decrypt load screen", e);
            dialogDisplayer.showErrorAlert("Ошибка", "Не удалось загрузить интерфейс: " + e.getMessage());
            showDecryptBeginPanel();
        }
    }

    public void showDecryptModePanel() {
        showScreen("/fxml/decrypt/decrypt-mode.fxml");
    }

    public void showDecryptFinalPanel(String keyFilePath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/decrypt/decrypt-final.fxml"));
            loader.setControllerFactory(controllerFactory);
            Parent root = loader.load();

            DecryptFinalController controller = loader.getController();
            controller.setKeyFilePath(keyFilePath);

            primaryStage.getScene().setRoot(root);
        } catch (Exception e) {
            logger.error("Error loading decrypt final screen", e);
            dialogDisplayer.showErrorAlert("Ошибка", "Не удалось загрузить интерфейс: " + e.getMessage());
            showDecryptModePanel();
        }
    }
}
