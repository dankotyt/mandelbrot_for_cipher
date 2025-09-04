package com.cipher;

import com.cipher.client.handler.GlobalExceptionHandler;
import com.cipher.view.javafx.JavaFXImpl;
import javafx.application.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@EnableFeignClients
public class Launcher {

    public static void main(String[] args) {
        GlobalExceptionHandler.registerGlobalExceptionHandler();

        new Thread(() -> {
            Application.launch(JavaFXImpl.class, args);
        }).start();

        ConfigurableApplicationContext context = SpringApplication.run(Launcher.class, args);
        JavaFXImpl.setSpringContext(context);
    }
}