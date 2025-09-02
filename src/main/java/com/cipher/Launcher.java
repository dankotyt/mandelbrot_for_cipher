package com.cipher;

import com.cipher.view.javafx.JavaFXImpl;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;

@SpringBootApplication
@EnableFeignClients
@Getter
public class Launcher {

    private static ConfigurableApplicationContext context;

    public static void main(String[] args) {
        new Thread(() -> {
            context = SpringApplication.run(Launcher.class, args);
            JavaFXImpl.setSpringContext(context);
        }).start();

        Application.launch(JavaFXImpl.class, args);
    }
}