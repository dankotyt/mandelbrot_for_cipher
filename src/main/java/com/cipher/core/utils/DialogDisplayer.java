package com.cipher.core.utils;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DialogDisplayer {
    private static final Logger logger = LoggerFactory.getLogger(DialogDisplayer.class);

    @Setter
    private Stage primaryStage;

    public void showErrorMessage(String message) {
        showAlert(Alert.AlertType.ERROR, "Ошибка", null, message);
    }

    public void showErrorDialog(String message) {
        showAlert(Alert.AlertType.ERROR, "Ошибка", null, message);
    }

    public void showSuccessDialog(String message) {
        showAlert(Alert.AlertType.INFORMATION, "Успех", null, message);
    }

    public void showErrorAlert(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, null, message);
    }

    public void showAlert(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, null, message);
    }

    public void showWarningAlert(String title, String message) {
        showAlert(Alert.AlertType.WARNING, title, null, message);
    }

    private void showAlert(Alert.AlertType alertType, String title, String header, String content) {
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(alertType);
                alert.setTitle(title);
                alert.setHeaderText(header);
                alert.setContentText(content);

                if (primaryStage != null) {
                    alert.initOwner(primaryStage);
                    alert.initModality(Modality.APPLICATION_MODAL);

                    // Центрируем алерт относительно главного окна
                    alert.setOnShown(event -> {
                        Window window = alert.getDialogPane().getScene().getWindow();
                        window.setX(primaryStage.getX() + (primaryStage.getWidth() - window.getWidth()) / 2);
                        window.setY(primaryStage.getY() + (primaryStage.getHeight() - window.getHeight()) / 2);
                    });
                }

                alert.showAndWait();
            } catch (Exception e) {
                logger.error("Error showing alert: " + e.getMessage());
            }
        });
    }
}
