package com.cipher.client.handler;

import javafx.application.Platform;
import javafx.scene.control.Alert;

public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Критическая ошибка");
            alert.setContentText("Приложение будет закрыто. Ошибка: " + e.getMessage());
            alert.showAndWait();
            System.exit(1);
        });
    }

    public static void registerGlobalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler());
    }
}
