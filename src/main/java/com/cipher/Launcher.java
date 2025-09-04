package com.cipher;

import com.cipher.client.handler.GlobalExceptionHandler;
import com.cipher.view.javafx.JavaFXImpl;
import javafx.application.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Главный класс приложения, отвечающий за запуск Spring Boot и JavaFX.
 * Инициализирует глобальный обработчик исключений, запускает JavaFX приложение
 * и контекст Spring в отдельных потоках.
 */
@SpringBootApplication
@EnableFeignClients
public class Launcher {

    /**
     * Точка входа в приложение.
     * Инициализирует глобальный обработчик исключений, запускает JavaFX приложение
     * и контекст Spring Boot.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        GlobalExceptionHandler.registerGlobalExceptionHandler();

        new Thread(() -> {
            Application.launch(JavaFXImpl.class, args);
        }).start();

        ConfigurableApplicationContext context = SpringApplication.run(Launcher.class, args);
        JavaFXImpl.setSpringContext(context);
    }
}