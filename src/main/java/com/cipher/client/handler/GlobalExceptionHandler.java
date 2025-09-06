package com.cipher.client.handler;

import javafx.application.Platform;
import javafx.scene.control.Alert;

/**
 * Глобальный обработчик необработанных исключений.
 * Перехватывает исключения, которые не были обработаны в других местах приложения,
 * и показывает пользователю диалоговое окно с ошибкой перед завершением приложения.
 * Реализует интерфейс {@link Thread.UncaughtExceptionHandler}.
 */
public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {

    /**
     * Обрабатывает необработанное исключение в потоке.
     * Показывает диалоговое окно с ошибкой и завершает приложение.
     *
     * @param t поток, в котором произошло исключение
     * @param e исключение, которое было брошено
     */
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

    /**
     * Регистрирует глобальный обработчик исключений для всех потоков.
     * Должен быть вызван при запуске приложения.
     */
    public static void registerGlobalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler());
    }
}
