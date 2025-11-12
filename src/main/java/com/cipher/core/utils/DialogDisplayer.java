package com.cipher.core.utils;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.*;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Setter
@Component
public class DialogDisplayer {
    private static final Logger logger = LoggerFactory.getLogger(DialogDisplayer.class);

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

    public void showTimedAlert(String title, String message, int seconds) {
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.initModality(Modality.NONE);

                if (primaryStage != null) {
                    alert.initOwner(primaryStage);

                    alert.setOnShown(event -> {
                        Window window = alert.getDialogPane().getScene().getWindow();
                        window.setX(primaryStage.getX() + (primaryStage.getWidth() - window.getWidth()) / 2);
                        window.setY(primaryStage.getY() + (primaryStage.getHeight() - window.getHeight()) / 2);
                    });
                }

                alert.show();

                new Thread(() -> {
                    try {
                       Thread.sleep(seconds * 1000L);
                        Platform.runLater(alert::close);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();

            } catch (Exception e) {
                logger.error("Error showing timed alert: " + e.getMessage());
            }
        });
    }

    public void showTimedErrorAlert(String title, String message, int seconds) {
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.initModality(Modality.NONE);

                if (primaryStage != null) {
                    alert.initOwner(primaryStage);

                    alert.setOnShown(event -> {
                        Window window = alert.getDialogPane().getScene().getWindow();
                        window.setX(primaryStage.getX() + (primaryStage.getWidth() - window.getWidth()) / 2);
                        window.setY(primaryStage.getY() + (primaryStage.getHeight() - window.getHeight()) / 2);
                    });
                }

                alert.show();

                new Thread(() -> {
                    try {
                        Thread.sleep(seconds * 1000L);
                        Platform.runLater(alert::close);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();

            } catch (Exception e) {
                logger.error("Error showing timed alert: " + e.getMessage());
            }
        });
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
                    alert.initModality(Modality.WINDOW_MODAL);

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

    public boolean showConfirmationDialog(String title, String header, String content,
                                          String confirmButtonText, String cancelButtonText) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        alert.initModality(Modality.WINDOW_MODAL);
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }

        ButtonType confirmButton = new ButtonType(confirmButtonText);
        ButtonType cancelButton = new ButtonType(cancelButtonText, ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(confirmButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == confirmButton;
    }
}
