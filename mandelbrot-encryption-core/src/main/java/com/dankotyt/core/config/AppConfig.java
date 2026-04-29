package com.dankotyt.core.config;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Основная конфигурация Spring, сканирующая компоненты пакета {@code com.dankotyt}
 * и предоставляющая общий {@link ExecutorService} для многопоточных задач.
 *
 * @author dankotyt
 * @since 1.1.0
 */
@Configuration
@ComponentScan("com.dankotyt")
public class AppConfig {

    /**
     * Предоставляет кэширующий пул потоков с обработчиком неперехваченных исключений,
     * который логирует ошибки через SLF4J.
     *
     * @return глобальный {@link ExecutorService} с автоматическим завершением при остановке контекста.
     */
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
}
