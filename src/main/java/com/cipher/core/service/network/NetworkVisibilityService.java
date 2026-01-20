package com.cipher.core.service.network;

import com.cipher.client.service.localNetwork.DiscoveryClient;
import com.cipher.client.service.localNetwork.DiscoveryServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkVisibilityService {

    private final DiscoveryServer discoveryServer;
    private final DiscoveryClient discoveryClient;
    private final NetworkDiscoveryService networkDiscoveryService;

    private final AtomicBoolean isVisible = new AtomicBoolean(false);
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        // Инициализируем клиент, но не запускаем сервер
        try {
            discoveryClient.start();
            isInitialized.set(true);
            log.info("NetworkVisibilityService инициализирован (невидим)");
        } catch (Exception e) {
            log.error("Ошибка инициализации NetworkVisibilityService: {}", e.getMessage());
        }
    }

    /**
     * Стать видимым в сети
     */
    public void becomeVisible() {
        if (isVisible.compareAndSet(false, true)) {
            try {
                // Запускаем сервер для анонсирования
                discoveryServer.start();

                // Инициализируем сервис обнаружения
                networkDiscoveryService.initialize();

                // Немедленная рассылка
                broadcastImmediatePresence();

                log.info("✅ Устройство стало видимым в сети");
            } catch (Exception e) {
                log.error("Ошибка при становлении видимым: {}", e.getMessage());
                isVisible.set(false);
            }
        }
    }

    /**
     * Стать невидимым в сети
     */
    public void becomeInvisible() {
        if (isVisible.compareAndSet(true, false)) {
            try {
                // Останавливаем сервер анонсирования
                discoveryServer.stop();

                // Останавливаем обнаружение
                networkDiscoveryService.stopDiscovery();

                log.info("🔇 Устройство стало невидимым в сети");
            } catch (Exception e) {
                log.error("Ошибка при становлении невидимым: {}", e.getMessage());
            }
        }
    }

    /**
     * Немедленная рассылка присутствия
     */
    private void broadcastImmediatePresence() {
        new Thread(() -> {
            try {
                Thread.sleep(500); // Даем время на инициализацию
                networkDiscoveryService.broadcastPresence();

                // Дополнительные 2 рассылки для гарантии
                for (int i = 0; i < 2; i++) {
                    Thread.sleep(100);
                    networkDiscoveryService.broadcastPresence();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("Ошибка немедленной рассылки: {}", e.getMessage());
            }
        }).start();
    }

    /**
     * Проверить, видимо ли устройство
     */
    public boolean isVisible() {
        return isVisible.get();
    }

    @PreDestroy
    public void shutdown() {
        becomeInvisible();
        try {
            discoveryClient.stop();
        } catch (Exception e) {
            log.warn("Ошибка остановки клиента: {}", e.getMessage());
        }
        log.info("NetworkVisibilityService остановлен");
    }
}
