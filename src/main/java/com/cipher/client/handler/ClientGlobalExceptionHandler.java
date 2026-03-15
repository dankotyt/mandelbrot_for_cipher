package com.cipher.client.handler;

import com.cipher.core.utils.DialogDisplayer;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.springframework.http.HttpStatus.*;

/**
 * Глобальный обработчик необработанных исключений.
 * Перехватывает исключения, которые не были обработаны в других местах приложения,
 * и показывает пользователю диалоговое окно с ошибкой перед завершением приложения.
 * Реализует интерфейс {@link Thread.UncaughtExceptionHandler}.
 */
@Component
public class ClientGlobalExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ClientGlobalExceptionHandler.class);

    private final DialogDisplayer dialogDisplayer;

    public ClientGlobalExceptionHandler(DialogDisplayer dialogDisplayer) {
        this.dialogDisplayer = dialogDisplayer;
        initGlobalExceptionHandler();
    }

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
//    public static void registerGlobalExceptionHandler() {
//        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler());
//    }

    private void initGlobalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("Необработанное исключение в потоке {}: {}", thread.getName(),
                    throwable.getMessage(), throwable);

            if (Platform.isFxApplicationThread()) {
                handleException(throwable);
            } else {
                Platform.runLater(() -> handleException(throwable));
            }
        });
    }

    public void handleException(Throwable throwable) {
        try {
            logger.error("Обработка исключения в UI потоке: {}", throwable.getMessage(), throwable);

            String errorMessage = getClientErrorMessage(throwable);
            dialogDisplayer.showErrorDialog(
                    "Произошла ошибка:\n" + errorMessage + "\n\nПриложение продолжит работу."
            );

        } catch (Exception e) {
            logger.error("Критическая ошибка при обработке исключения: {}", e.getMessage(), e);
            Platform.exit();
            System.exit(1);
        }
    }

    private String getClientErrorMessage(Throwable throwable) {
        // Обработка специфичных для клиента ошибок
        if (throwable instanceof HttpClientErrorException) {
            HttpClientErrorException httpEx = (HttpClientErrorException) throwable;
            return handleHttpClientError(httpEx);
        } else if (throwable instanceof HttpServerErrorException) {
            return "Ошибка сервера. Попробуйте позже.";
        } else if (throwable instanceof ResourceAccessException) {
            return "Нет подключения к серверу. Проверьте интернет-соединение.";
        } else if (throwable.getCause() != null) {
            return throwable.getCause().getMessage();
        }
        return throwable.getMessage() != null ? throwable.getMessage() : "Неизвестная ошибка";
    }

    private String handleHttpClientError(HttpClientErrorException ex) {
        switch (ex.getStatusCode()) {
            case NOT_FOUND:
                return "Ресурс не найден на сервере.";
            case UNAUTHORIZED:
                return "Ошибка авторизации. Проверьте учетные данные.";
            case FORBIDDEN:
                return "Доступ запрещен.";
            case BAD_REQUEST:
                return "Неверный запрос к серверу.";
            default:
                return "Ошибка при обращении к серверу: " + ex.getStatusCode();
        }
    }

    public static void registerGlobalExceptionHandler(DialogDisplayer dialogDisplayer) {
        ClientGlobalExceptionHandler handler = new ClientGlobalExceptionHandler(dialogDisplayer);
    }
}
