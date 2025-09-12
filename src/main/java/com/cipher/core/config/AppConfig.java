package com.cipher.core.config;

import javafx.stage.Stage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@ComponentScan("com.cipher")
public class AppConfig {

    @Bean
    @Scope("prototype")
    public Stage primaryStage() {
        return new Stage();
    }

    @Bean
    public ExecutorService executorService() {
        return Executors.newCachedThreadPool();
    }

    //todo Добавить другие бины по необходимости
}
