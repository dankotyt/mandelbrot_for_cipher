package com.cipher.core.config;

import com.cipher.client.handler.ClientGlobalExceptionHandler;
import com.cipher.core.factory.ControllerFactory;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@ComponentScan("com.cipher")
public class AppConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService executorService() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setUncaughtExceptionHandler((t, e) -> {
                LoggerFactory.getLogger(AppConfig.class).error("Необработанное исключение в потоке ExecutorService: {}", e.getMessage(), e);
            });
            return thread;
        });
    }

    @Bean
    public DialogDisplayer dialogDisplayer() {
        return new DialogDisplayer();
    }

    @Bean
    public ClientGlobalExceptionHandler clientGlobalExceptionHandler(DialogDisplayer dialogDisplayer) {
        return new ClientGlobalExceptionHandler(dialogDisplayer);
    }

    @Bean
    public TempFileManager tempFileManager(DialogDisplayer dialogDisplayer, SceneManager sceneManager) {
        return new TempFileManager(dialogDisplayer, sceneManager);
    }

    @Bean
    public SceneManager sceneManager(ControllerFactory controllerFactory) {
        return new SceneManager(controllerFactory, dialogDisplayer());
    }

    @Bean
    public ControllerFactory controllerFactory(ApplicationContext applicationContext) {
        return new ControllerFactory(applicationContext);
    }

    // Дополнительные бины по необходимости
}
