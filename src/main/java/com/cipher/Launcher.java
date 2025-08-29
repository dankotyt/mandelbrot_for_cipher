package com.cipher;

import com.cipher.view.javafx.JavaFXImpl;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;

@SpringBootApplication
@Getter
public class Launcher {

    private static ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        try {
            springContext = SpringApplication.run(Launcher.class, args);

            // Запускаем JavaFX и передаем контекст
            JavaFXImpl.setSpringContext(springContext);
            Application.launch(JavaFXImpl.class, args);

        } catch (Throwable t) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Ошибка запуска");
                alert.setHeaderText("Launch failed:");
                alert.setContentText(t.getMessage());
                alert.showAndWait();
            });
        }
    }

}